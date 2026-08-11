package com.mapconductor.mappls.polygon

import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.mappls.MapplsActualPolygon
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.FeatureCollection
import com.mappls.sdk.maps.style.expressions.Expression.get
import com.mappls.sdk.maps.style.layers.FillLayer
import com.mappls.sdk.maps.style.layers.PropertyFactory.fillColor
import com.mappls.sdk.maps.style.sources.GeoJsonSource

class MapplsPolygonLayer(
    val sourceId: String,
    val layerId: String,
) {
    object Prop {
        const val FILL_COLOR = "fillColor"
        const val Z_INDEX = "zIndex"
    }

    val source: GeoJsonSource =
        GeoJsonSource(
            sourceId,
            FeatureCollection.fromFeatures(emptyList()),
        )

    val layer: FillLayer =
        FillLayer(layerId, sourceId).apply {
            setProperties(
                fillColor(get(Prop.FILL_COLOR)),
            )
        }

    fun draw(
        entities: List<PolygonEntityInterface<MapplsActualPolygon>>,
        style: com.mappls.sdk.maps.Style,
    ) {
        val features: List<Feature> =
            entities
                .sortedBy { it.state.zIndex }
                .flatMap { it.polygon }

        val styleSource =
            try {
                style.getSource(sourceId)
            } catch (e: IllegalStateException) {
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
