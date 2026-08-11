package com.mapconductor.mappls.polyline

import com.mapconductor.core.polyline.PolylineEntityInterface
import com.mapconductor.mappls.MapplsActualPolyline
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.FeatureCollection
import com.mappls.sdk.maps.style.expressions.Expression.get
import com.mappls.sdk.maps.style.layers.LineLayer
import com.mappls.sdk.maps.style.layers.Property
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineCap
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineColor
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineJoin
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineWidth
import com.mappls.sdk.maps.style.sources.GeoJsonSource

class MapplsPolylineLayer(
    val sourceId: String,
    val layerId: String,
) {
    object Prop {
        const val STROKE_COLOR = "strokeColor"
        const val STROKE_WIDTH = "strokeWidth"
        const val Z_INDEX = "zIndex"
    }

    val source: GeoJsonSource =
        GeoJsonSource(
            sourceId,
            FeatureCollection.fromFeatures(emptyList()),
        )

    val layer: LineLayer =
        LineLayer(layerId, sourceId).apply {
            setProperties(
                lineJoin(Property.LINE_JOIN_ROUND),
                lineCap(Property.LINE_CAP_ROUND),
                lineColor(get(Prop.STROKE_COLOR)),
                lineWidth(get(Prop.STROKE_WIDTH)),
            )
        }

    fun draw(
        entities: List<PolylineEntityInterface<MapplsActualPolyline>>,
        style: com.mappls.sdk.maps.Style,
    ) {
        val features: List<Feature> = entities.flatMap { it.polyline }

        val styleSource =
            try {
                style.getSource(sourceId)
            } catch (_: IllegalStateException) {
                // Style might be in transition
                null
            }

        if (styleSource is GeoJsonSource) {
            try {
                styleSource.setGeoJson(FeatureCollection.fromFeatures(features))
                return
            } catch (_: IllegalStateException) {
                // fall through to fallback
            }
        }
        // Fallback to local source instance if style source is unavailable
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}
