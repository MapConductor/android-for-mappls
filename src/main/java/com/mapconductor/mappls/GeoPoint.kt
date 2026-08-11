package com.mapconductor.mappls

import com.mapconductor.core.features.GeoPoint
import com.mappls.sdk.geojson.Point
import com.mappls.sdk.maps.geometry.LatLng

fun GeoPoint.toLatLng(): LatLng = LatLng(this.latitude, this.longitude, this.altitude)

fun GeoPoint.Companion.from(latLng: LatLng) = GeoPoint(latLng.latitude, latLng.longitude, latLng.altitude)

fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude, altitude)

fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)

fun GeoPoint.Companion.from(point: Point) =
    GeoPoint(
        latitude = point.latitude(),
        longitude = point.longitude(),
        altitude = point.altitude(),
    )
