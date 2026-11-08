package com.mapconductor.mappls

import com.mapconductor.core.controller.OverlayKind
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
 * [MapplsOverlayStacking.add] はいずれも既存があれば作らない）。層の位置は
 * [MapplsOverlayStacking] が帯の順（ラスター < GroundImage < ポリゴン < サークル <
 * ポリライン < マーカー）で決める。
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

// 層の位置は MapplsOverlayStacking が決める。以下 2 つは公開 API の互換のために残している（SDK 内では未使用）。
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
    ensureGeoJsonSource(style, polygonController.renderer.outline.layer.sourceId)
    ensureGeoJsonSource(style, polygonController.renderer.fill.layer.sourceId)

    // 層の位置は MapplsOverlayStacking が帯の順で決める（追加の順に依らない）。
    val stacking = MapplsOverlayStacking
    val map = holder.map
    val markerRenderer = markerController.renderer as MapplsMarkerOverlayRenderer

    ensureGeoJsonSource(style, circleController.renderer.layer.sourceId)
    stacking.add(map, style, circleController.renderer.layer.layer, stacking.key(OverlayKind.Circle))
    // 線は塗りの直後（同じ鍵なら後から入れたものが上）。
    stacking.add(map, style, circleController.renderer.layer.strokeLayer, stacking.key(OverlayKind.Circle))

    ensureGeoJsonSource(style, polylineController.renderer.layer.sourceId)
    stacking.add(map, style, polylineController.renderer.layer.layer, stacking.key(OverlayKind.Polyline))

    ensurePolygonZLayers(style)

    ensureGeoJsonSource(style, markerRenderer.markerLayer.sourceId)
    stacking.add(map, style, markerRenderer.markerLayer.layer, stacking.key(OverlayKind.Marker))
    markerRenderer.redraw()

    ensureGeoJsonSource(style, markerRenderer.dragLayer.sourceId)
    stacking.add(map, style, markerRenderer.dragLayer.layer, stacking.MARKER_DRAG)
    markerRenderer.redraw()

    markerEventControllers
        .map { it.renderer as MapplsMarkerOverlayRenderer }
        .filter { it != markerController.renderer }
        .forEach { renderer ->
            renderer.ensureDefaultIcon(style)
            ensureGeoJsonSource(style, renderer.markerLayer.sourceId)
            stacking.add(map, style, renderer.markerLayer.layer, stacking.key(OverlayKind.Marker))
            ensureGeoJsonSource(style, renderer.dragLayer.sourceId)
            stacking.add(map, style, renderer.dragLayer.layer, stacking.MARKER_DRAG)
            renderer.redraw()
            renderer.drawDragLayer()
        }

    // Force redraw after adding layers
    (markerController.renderer as MapplsMarkerOverlayRenderer).redraw()
    circleController.renderer.redraw()
    polylineController.renderer.redraw()
//        polygonController.renderer.fill.onPostProcess()
    mainCoroutine.launch {
        groundImageController.reapplyStyle()
        rasterLayerController.reapplyStyle()
    }
}

internal fun MapplsViewController.ensurePolygonZLayers(style: Style) {
    val fillSourceId = polygonController.renderer.fill.layer.sourceId
    val outlineSourceId = polygonController.renderer.outline.layer.sourceId

    val zSet =
        polygonController.polygonManager
            .allEntities()
            .map { it.state.zIndex }
            .toSet()

    // Remove stale z-indexed layers we previously created
    val toRemove = polygonZLayers.subtract(zSet)
    toRemove.forEach { z ->
        val fillId = "polygon-fill-layer-$z"
        val outlineId = "polygon-outline-layer-$z"
        MapplsOverlayStacking.remove(holder.map, style, outlineId)
        MapplsOverlayStacking.remove(holder.map, style, fillId)
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
            MapplsOverlayStacking.add(holder.map, style, fill, MapplsOverlayStacking.key(OverlayKind.Polygon, z))
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
            // 塗りの直後（同じ鍵なら後から入れたものが上）。
            MapplsOverlayStacking.add(holder.map, style, outline, MapplsOverlayStacking.key(OverlayKind.Polygon, z))
        }
    }
    polygonZLayers.clear()
    polygonZLayers.addAll(zSet)
}
