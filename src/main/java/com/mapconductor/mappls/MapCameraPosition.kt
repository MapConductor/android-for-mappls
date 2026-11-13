package com.mapconductor.mappls

import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.map.CameraBearing
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapPaddings
import com.mapconductor.core.map.MapPaddingsInterface
import com.mapconductor.core.spherical.Spherical
import com.mapconductor.mappls.zoom.ZoomAltitudeConverter
import com.mappls.sdk.maps.camera.CameraPosition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

private val converter = ZoomAltitudeConverter()

/**
 * Mappls のカメラの縦方向の視野角（度）。Mappls は MapLibre Native 派生で、既定値は地図の大きさに
 * よらず一定（焦点距離 = 地図の高さ × 1.5）。TB520FU で 36.9° を実測。
 */
internal const val MAPPLS_VERTICAL_FOV = 36.87

/** dp のパディング → Mappls の px のパディング（left, top, right, bottom）。 */
internal fun MapPaddingsInterface.toMapplsPadding(density: Float): DoubleArray =
    doubleArrayOf(left * density, top * density, right * density, bottom * density)

/** Mappls の px のパディング（left, top, right, bottom）→ dp のパディング。 */
internal fun DoubleArray.toMapPaddings(density: Float): MapPaddings? {
    if (size < 4 || density <= 0f) return null
    return MapPaddings(
        top = this[1] / density,
        left = this[0] / density,
        bottom = this[3] / density,
        right = this[2] / density,
    )
}

private const val NEGATIVE_TILT_TARGET_DISTANCE_SCALE = 1.83
private const val NEGATIVE_TILT_ZOOM_OFFSET_AT_MAX_TILT = -0.9

fun MapCameraPosition.toCameraPosition(): CameraPosition = toCameraPosition(density = null)

/**
 * [density] があれば [MapCameraPosition.paddings] を Mappls のパディングとして渡す。
 * Mappls はパディングを除いた領域の中央にカメラの中心を置き、tilt の遠近もエンジンが解く。
 * Mappls のパディングは次に変えるまで残るので、null（未指定）のときは渡さない。
 */
internal fun MapCameraPosition.toCameraPosition(density: Float?): CameraPosition {
    val padding = if (density != null) paddings?.toMapplsPadding(density) else null
    if (tilt >= 0) {
        return CameraPosition
            .Builder()
            .target(GeoPoint.from(position).toLatLng())
            .zoom(ZoomAltitudeConverter.googleZoomToMapplsZoom(zoom))
            .tilt(tilt)
            .bearing(CameraBearing.toNativeHeading(bearing))
            .apply { padding?.let { padding(it) } }
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
            .apply { padding?.let { padding(it) } }
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
                paddings = cameraPosition.paddings,
                visibleRegion = cameraPosition.visibleRegion,
                fov = cameraPosition.fov,
            )
    }

fun CameraPosition.toMapCameraPosition() = toMapCameraPosition(logicalTiltHint = null)

internal data class MapplsCameraStateSnapshot(
    val cameraPosition: CameraPosition,
    val logicalTiltHint: Double?,
    val density: Float? = null,
) {
    fun toMapCameraPosition(): MapCameraPosition = cameraPosition.toMapCameraPosition(logicalTiltHint, density)
}

internal fun CameraPosition.toMapCameraPosition(logicalTiltHint: Double?): MapCameraPosition =
    toMapCameraPosition(logicalTiltHint, density = null)

/** [density] があれば Mappls のパディングを dp に直して paddings として返す。 */
internal fun CameraPosition.toMapCameraPosition(
    logicalTiltHint: Double?,
    density: Float?,
): MapCameraPosition {
    val paddings = density?.let { d -> padding?.toMapPaddings(d) } ?: MapPaddings.Zeros
    val pitch = tilt
    val pitchAbsDeg = abs(pitch).coerceIn(0.0, 60.0)

    if (logicalTiltHint == null || logicalTiltHint >= 0.0 || pitchAbsDeg == 0.0) {
        return MapCameraPosition(
            position = target?.toGeoPoint() ?: GeoPoint.fromLongLat(0.0, 0.0),
            zoom = ZoomAltitudeConverter.mapplsZoomToGoogleZoom(zoom),
            bearing = CameraBearing.bearingFromNativeHeading(bearing),
            tilt = pitch,
            paddings = paddings,
            visibleRegion = null,
            fov = MAPPLS_VERTICAL_FOV,
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
        paddings = paddings,
        visibleRegion = null,
        fov = MAPPLS_VERTICAL_FOV,
    )
}
