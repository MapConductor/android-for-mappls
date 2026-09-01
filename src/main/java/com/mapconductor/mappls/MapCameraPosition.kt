package com.mapconductor.mappls

import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.map.CameraBearing
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.spherical.Spherical
import com.mapconductor.mappls.zoom.ZoomAltitudeConverter
import com.mappls.sdk.maps.camera.CameraPosition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

private val converter = ZoomAltitudeConverter()
private const val NEGATIVE_TILT_TARGET_DISTANCE_SCALE = 1.83
private const val NEGATIVE_TILT_ZOOM_OFFSET_AT_MAX_TILT = -0.9

fun MapCameraPosition.toCameraPosition(): CameraPosition {
    if (tilt >= 0) {
        return CameraPosition
            .Builder()
            .target(GeoPoint.from(position).toLatLng())
            .zoom(ZoomAltitudeConverter.googleZoomToMapplsZoom(zoom))
            .tilt(tilt)
            .bearing(CameraBearing.toNativeHeading(bearing))
            // TODO:
//    .padding(paddings?.toEdgeInsects())
            .build()
    } else {
        // tilt < 0: Mappls cannot represent an upward pitch directly.
        // Match the Google Maps workaround: move the ground target forward and render with abs(tilt).
        val tiltAbsDeg = abs(tilt).coerceIn(0.0, 60.0)
        val tiltAbsRad = Math.toRadians(tiltAbsDeg)
        val mapplsZoomForAltitude = ZoomAltitudeConverter.googleZoomToMapplsZoom(zoom)
        val altitude = converter.zoomLevelToAltitude(mapplsZoomForAltitude, position.latitude, 0.0)
        val distanceForward =
            altitude *
                cos(tiltAbsRad) *
                tan(tiltAbsRad) *
                NEGATIVE_TILT_TARGET_DISTANCE_SCALE
        val target = Spherical.computeOffset(position, distanceForward, CameraBearing.toNativeHeading(bearing))
        val adjustedZoom = zoom + NEGATIVE_TILT_ZOOM_OFFSET_AT_MAX_TILT * (tiltAbsDeg / 60.0)

        return CameraPosition
            .Builder()
            .target(target.toLatLng())
            .zoom(ZoomAltitudeConverter.googleZoomToMapplsZoom(adjustedZoom))
            .tilt(tiltAbsDeg)
            .bearing(CameraBearing.toNativeHeading(bearing))
            // TODO:
//    .padding(paddings?.toEdgeInsects())
            .build()
    }
}

fun MapCameraPosition.Companion.from(cameraPosition: MapCameraPositionInterface) =
    when (cameraPosition) {
        is MapCameraPosition -> cameraPosition
        else ->
            MapCameraPosition(
                position = cameraPosition.position,
                zoom = cameraPosition.zoom,
                bearing = cameraPosition.bearing,
                tilt = cameraPosition.tilt,
                visibleRegion = cameraPosition.visibleRegion,
            )
    }

fun CameraPosition.toMapCameraPosition() = toMapCameraPosition(logicalTiltHint = null)

internal data class MapplsCameraStateSnapshot(
    val cameraPosition: CameraPosition,
    val logicalTiltHint: Double?,
) {
    fun toMapCameraPosition(): MapCameraPosition = cameraPosition.toMapCameraPosition(logicalTiltHint)
}

internal fun CameraPosition.toMapCameraPosition(logicalTiltHint: Double?): MapCameraPosition {
    val pitch = tilt
    val pitchAbsDeg = abs(pitch).coerceIn(0.0, 60.0)

    if (logicalTiltHint == null || logicalTiltHint >= 0.0 || pitchAbsDeg == 0.0) {
        return MapCameraPosition(
            position = target?.toGeoPoint() ?: GeoPoint.fromLongLat(0.0, 0.0),
            zoom = ZoomAltitudeConverter.mapplsZoomToGoogleZoom(zoom),
            bearing = CameraBearing.bearingFromNativeHeading(bearing),
            tilt = pitch,
            visibleRegion = null,
        )
    }

    // Recover original position and zoom from shifted camera state (tilt < 0 case)
    val pitchAbsRad = Math.toRadians(pitchAbsDeg)
    val shiftedCenter = target?.toGeoPoint() ?: GeoPoint.fromLongLat(0.0, 0.0)
    val bear = bearing

    val googleZoom = ZoomAltitudeConverter.mapplsZoomToGoogleZoom(zoom)
    val originalGoogleZoom = googleZoom - NEGATIVE_TILT_ZOOM_OFFSET_AT_MAX_TILT * (pitchAbsDeg / 60.0)
    val originalMapplsZoom = ZoomAltitudeConverter.googleZoomToMapplsZoom(originalGoogleZoom)

    val altitude = converter.zoomLevelToAltitude(originalMapplsZoom, shiftedCenter.latitude, 0.0)
    val distanceBackward = altitude * cos(pitchAbsRad) * tan(pitchAbsRad) * NEGATIVE_TILT_TARGET_DISTANCE_SCALE
    val originalPosition = Spherical.computeOffset(shiftedCenter, distanceBackward, bear + 180.0)

    return MapCameraPosition(
        position = originalPosition,
        zoom = originalGoogleZoom,
        bearing = CameraBearing.bearingFromNativeHeading(bear),
        tilt = -pitchAbsDeg,
        visibleRegion = null,
    )
}
