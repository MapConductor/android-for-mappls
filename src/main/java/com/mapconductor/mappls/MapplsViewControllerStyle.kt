package com.mapconductor.mappls

import com.mapconductor.mappls.marker.MapplsMarkerOverlayRenderer
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.style.expressions.Expression
import com.mappls.sdk.maps.style.layers.FillLayer
import com.mappls.sdk.maps.style.layers.Layer
import com.mappls.sdk.maps.style.layers.LineLayer
import com.mappls.sdk.maps.style.layers.Property
import com.mappls.sdk.maps.style.layers.PropertyFactory
import com.mappls.sdk.maps.style.sources.GeoJsonSource
import android.util.Log
import kotlinx.coroutines.launch

/**
 * スタイル（レイヤーとソース）の組み立て。
 *
 * Mappls はスタイルが読み込み直されるたびにレイヤーが全部消えるため、ここは
 * 何度呼ばれても同じ状態になるように書いてある（`ensureGeoJsonSource` /
 * `addLayerSafely` はいずれも既存があれば作らない）。
 *
 * ポリゴンの z 順は**レイヤーを z 値ごとに用意して**表現する。Mappls には
 * フィーチャー単位の z 指定が無く、レイヤーの並び順だけが順序を決めるため。
 */
internal fun MapplsViewController.ensureGeoJsonSource(
    style: Style,
    sourceId: String,
) {
    if (style.getSource(sourceId) != null) return
    try {
        style.addSource(GeoJsonSource(sourceId))
    } catch (e: Exception) {
        Log.w("Mappls", "Failed to add source: $sourceId (${e.message})")
    }
}

internal fun MapplsViewController.addLayerSafely(
    style: Style,
    layer: Layer,
    layerId: String,
) {
    if (style.getLayer(layerId) != null) return
    try {
        style.addLayer(layer)
    } catch (e: Exception) {
        if (style.getLayer(layerId) == null) {
            Log.w("Mappls", "Failed to add layer: $layerId (${e.message})")
        }
    }
}

internal fun MapplsViewController.addLayerAboveSafely(
    style: Style,
    layer: Layer,
    layerId: String,
    aboveId: String,
) {
    if (style.getLayer(layerId) != null) return
    try {
        style.addLayerAbove(layer, aboveId)
    } catch (_: Exception) {
        if (style.getLayer(layerId) != null) return
        try {
            style.addLayer(layer)
        } catch (e2: Exception) {
            if (style.getLayer(layerId) == null) {
                Log.w("Mappls", "Failed to add layer: $layerId (${e2.message})")
            }
        }
    }
}

internal fun MapplsViewController.setupStyle(style: Style) {
    // Store the style instance for future use
    styleInstance = style

    // Log existing layers
    // val topLayerId = style.layers.lastOrNull()?.id

    // Ensure default icon image exists on this style
    (markerController.renderer as MapplsMarkerOverlayRenderer).ensureDefaultIcon(style)

    // Polygon sources only (layers will be added per zIndex)
    ensureGeoJsonSource(style, polygonController.polylineOverlay.layer.sourceId)
    ensureGeoJsonSource(style, polygonController.polygonOverlay.layer.sourceId)

    // Circle cts as anchor above polygons
    ensureGeoJsonSource(style, circleController.renderer.layer.sourceId)
    addLayerSafely(
        style = style,
        layer = circleController.renderer.layer.layer,
        layerId = circleController.renderer.layer.layerId,
    )
    // Circle stroke (LineLayer) directly above the circle fill
    addLayerAboveSafely(
        style = style,
        layer = circleController.renderer.layer.strokeLayer,
        layerId = circleController.renderer.layer.strokeLayerId,
        aboveId = circleController.renderer.layer.layerId,
    )

    // Polyline (general) acts as anchor above circles
    ensureGeoJsonSource(style, polylineController.renderer.layer.sourceId)
    addLayerSafely(
        style = style,
        layer = polylineController.renderer.layer.layer,
        layerId = polylineController.renderer.layer.layerId,
    )

    // Add z-indexed polygon layers below general polylines
    ensurePolygonZLayers(style)

    // Marker - add source and layer at the top
    ensureGeoJsonSource(style, (markerController.renderer as MapplsMarkerOverlayRenderer).markerLayer.sourceId)
    addLayerAboveSafely(
        style = style,
        layer = (markerController.renderer as MapplsMarkerOverlayRenderer).markerLayer.layer,
        layerId = (markerController.renderer as MapplsMarkerOverlayRenderer).markerLayer.layerId,
        aboveId = polylineController.renderer.layer.layerId,
    )
    (markerController.renderer as MapplsMarkerOverlayRenderer).redraw()

    // Drag layer above marker layer
    ensureGeoJsonSource(style, (markerController.renderer as MapplsMarkerOverlayRenderer).dragLayer.sourceId)
    addLayerAboveSafely(
        style = style,
        layer = (markerController.renderer as MapplsMarkerOverlayRenderer).dragLayer.layer,
        layerId = (markerController.renderer as MapplsMarkerOverlayRenderer).dragLayer.layerId,
        aboveId = (markerController.renderer as MapplsMarkerOverlayRenderer).markerLayer.layerId,
    )
    (markerController.renderer as MapplsMarkerOverlayRenderer).redraw()

    markerEventControllers
        .map { it.renderer as MapplsMarkerOverlayRenderer }
        .filter { it != markerController.renderer }
        .forEach { renderer ->
            renderer.ensureDefaultIcon(style)
            ensureGeoJsonSource(style, renderer.markerLayer.sourceId)
            addLayerAboveSafely(
                style = style,
                layer = renderer.markerLayer.layer,
                layerId = renderer.markerLayer.layerId,
                aboveId = polylineController.renderer.layer.layerId,
            )
            ensureGeoJsonSource(style, renderer.dragLayer.sourceId)
            addLayerAboveSafely(
                style = style,
                layer = renderer.dragLayer.layer,
                layerId = renderer.dragLayer.layerId,
                aboveId = renderer.markerLayer.layerId,
            )
            renderer.redraw()
            renderer.drawDragLayer()
        }

    // Force redraw after adding layers
    (markerController.renderer as MapplsMarkerOverlayRenderer).redraw()
    circleController.renderer.redraw()
    polylineController.renderer.redraw()
//        polygonController.polygonOverlay.onPostProcess()
    mainCoroutine.launch {
        groundImageController.reapplyStyle()
        rasterLayerController.reapplyStyle()
    }
}

internal fun MapplsViewController.ensurePolygonZLayers(style: Style) {
    val fillSourceId = polygonController.polygonOverlay.layer.sourceId
    val outlineSourceId = polygonController.polylineOverlay.layer.sourceId
    val anchorId = polylineController.renderer.layer.layerId

    val zSet =
        polygonController.polygonOverlay.polygonManager
            .allEntities()
            .map { it.state.zIndex }
            .toSet()

    // Remove stale z-indexed layers we previously created
    val toRemove = polygonZLayers.subtract(zSet)
    toRemove.forEach { z ->
        val fillId = "polygon-fill-layer-$z"
        val outlineId = "polygon-outline-layer-$z"
        try {
            style.removeLayer(outlineId)
        } catch (_: Exception) {
        }
        try {
            style.removeLayer(fillId)
        } catch (_: Exception) {
        }
    }

    val zList = zSet.toList().sorted()
    zList.forEach { z ->
        val fillId = "polygon-fill-layer-$z"
        val outlineId = "polygon-outline-layer-$z"

        if (style.getLayer(fillId) == null) {
            val fill =
                FillLayer(fillId, fillSourceId).apply {
                    setFilter(
                        Expression.eq(
                            Expression
                                .get("zIndex"),
                            Expression
                                .literal(z),
                        ),
                    )
                    setProperties(
                        PropertyFactory.fillColor(
                            Expression
                                .get("fillColor"),
                        ),
                    )
                }
            try {
                style.addLayerBelow(fill, anchorId)
            } catch (_: Exception) {
                style.addLayer(fill)
            }
        }

        if (style.getLayer(outlineId) == null) {
            val outline =
                LineLayer(outlineId, outlineSourceId).apply {
                    setFilter(
                        Expression.eq(
                            Expression
                                .get("zIndex"),
                            Expression
                                .literal(z),
                        ),
                    )
                    setProperties(
                        PropertyFactory
                            .lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory
                            .lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineColor(
                            Expression
                                .get("strokeColor"),
                        ),
                        PropertyFactory.lineWidth(
                            Expression
                                .get("strokeWidth"),
                        ),
                    )
                }
            try {
                style.addLayerAbove(outline, fillId)
            } catch (_: Exception) {
                style.addLayer(outline)
            }
        }
    }
    polygonZLayers.clear()
    polygonZLayers.addAll(zSet)
}
