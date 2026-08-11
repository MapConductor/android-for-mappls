package com.mapconductor.mappls

import androidx.compose.ui.geometry.Offset
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.MapViewHolderInterface
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import android.graphics.PointF

interface MapplsMapViewHolderInterface : MapViewHolderInterface<MapView, MapplsMap>

class MapplsMapViewHolder(
    override val mapView: MapView,
    override val map: MapplsMap,
) : MapplsMapViewHolderInterface {
    override fun toScreenOffset(position: GeoPointInterface): Offset? {
        val pixel =
            map.projection.toScreenLocation(GeoPoint.from(position).toLatLng())
        return Offset(
            x = pixel.x,
            y = pixel.y,
        )
    }

    override fun fromScreenOffsetSync(offset: Offset): GeoPoint? =
        map.projection.fromScreenLocation(PointF(offset.x, offset.y)).toGeoPoint()
}
