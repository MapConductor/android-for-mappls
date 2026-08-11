package com.mapconductor.mappls.marker

import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.marker.MarkerEntityInterface
import com.mapconductor.mappls.MapplsActualMarker
import com.mapconductor.mappls.toPoint
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.FeatureCollection
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.style.sources.GeoJsonSource

open class MarkerDragLayer(
    sourceId: String,
    layerId: String,
) : MarkerLayer(sourceId, layerId) {
    var selected: MarkerEntityInterface<MapplsActualMarker>? = null

    fun updatePosition(geoPoint: GeoPoint) {
        selected?.let {
            it.state.position = geoPoint
        }
    }

    fun draw(style: Style) {
        val features =
            selected?.let {
                if (it.marker != null) {
                    val feature =
                        Feature.fromGeometry(
                            GeoPoint.from(it.state.position).toPoint(),
                            it.marker?.properties(),
                            it.state.id,
                        )
                    it.marker = feature
                    listOf<MapplsActualMarker>(feature)
                } else {
                    emptyList()
                }
            } ?: emptyList()
        val collection = FeatureCollection.fromFeatures(features)
        val styleSource =
            try {
                style.getSource(sourceId)
            } catch (_: IllegalStateException) {
                null
            }
        if (styleSource is GeoJsonSource) {
            try {
                styleSource.setGeoJson(collection)
                return
            } catch (_: IllegalStateException) {
            }
        }
        source.setGeoJson(collection)
    }
}
