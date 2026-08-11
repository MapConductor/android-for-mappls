package com.mapconductor.mappls

import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoRectBounds
import com.mappls.sdk.maps.geometry.LatLngBounds

fun GeoRectBounds.toLatLngBounds(): LatLngBounds? {
    val sw = southWest ?: return null
    val ne = northEast ?: return null

    return LatLngBounds.from(
        ne.latitude,
        ne.longitude,
        sw.latitude,
        sw.longitude,
    )
}

fun LatLngBounds.toGeoRectBounds(): GeoRectBounds =
    GeoRectBounds(
        southWest = GeoPoint(latitudeSouth, longitudeWest),
        northEast = GeoPoint(latitudeNorth, longitudeEast),
    )
