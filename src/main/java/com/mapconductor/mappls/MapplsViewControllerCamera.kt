package com.mapconductor.mappls

import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.buildVisibleRegion
import com.mapconductor.mappls.zoom.ZoomAltitudeConverter
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.constants.MapplsConstants
import kotlinx.coroutines.launch

/**
 * カメラ位置の読み取りと初回通知。
 *
 * Mappls のズームは 256px タイル基準で、MapConductor の論理ズーム（Google 基準）とは
 * 1 段ずれる。読むときも書くときも [ZoomAltitudeConverter] を通す。
 */
internal fun MapplsViewController.readLogicalCameraPosition(): MapCameraPosition =
    MapplsCameraStateSnapshot(
        cameraPosition = holder.map.cameraPosition,
        logicalTiltHint = lastLogicalCameraPosition?.tilt,
    ).toMapCameraPosition()

internal fun MapplsViewController.getMapCameraPosition(camera: MapCameraPositionInterface): MapCameraPosition? {
    // 4 隅の逆投影は全プロバイダ共通なのでコアの buildVisibleRegion を使う。
    val visibleRegion = holder.buildVisibleRegion() ?: return null
    return MapCameraPosition.from(camera).copy(visibleRegion = visibleRegion)
}

internal fun MapplsViewController.handleMoveCamera(position: MapCameraPosition) {
    lastLogicalCameraPosition = position
    mainCoroutine.launch {
        val cameraPos = position.toCameraPosition()
        val cameraUpdate =
            CameraUpdateFactory
                .newCameraPosition(cameraPos)
        holder.map.moveCamera(cameraUpdate)
        emitCameraMoveEnd(position)
    }
}

internal fun MapplsViewController.handleAnimateCamera(
    position: MapCameraPosition,
    duration: Long,
) {
    lastLogicalCameraPosition = position
    mainCoroutine.launch {
        val cameraPos = position.toCameraPosition()
        val cameraUpdate =
            CameraUpdateFactory
                .newCameraPosition(cameraPos)
        holder.map.animateCamera(cameraUpdate, duration.toInt())
        emitCameraMoveEnd(position)
    }
}

internal fun MapplsViewController.handleFitBounds(
    bounds: GeoRectBounds,
    padding: Int,
) {
    val latLngBounds = bounds.toLatLngBounds() ?: return
    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(latLngBounds, padding)
    mainCoroutine.launch {
        holder.map.moveCamera(cameraUpdate)
        emitCameraMoveEnd(readLogicalCameraPosition())
    }
}

internal fun MapplsViewController.handleCameraRestriction(restriction: CameraRestriction?) {
    mainCoroutine.launch {
        holder.map.setLatLngBoundsForCameraTarget(restriction?.bounds?.toLatLngBounds())
        // 統一ズーム（Google 準拠）を Mappls ズームへ変換して適用。
        // preference は解除 API が無いため、未指定時は既定の下限/上限を渡す。
        holder.map.setMinZoomPreference(
            restriction
                ?.minZoom
                ?.let { ZoomAltitudeConverter.googleZoomToMapplsZoom(it) }
                ?: MapplsConstants.MINIMUM_ZOOM.toDouble(),
        )
        holder.map.setMaxZoomPreference(
            restriction
                ?.maxZoom
                ?.let { ZoomAltitudeConverter.googleZoomToMapplsZoom(it) }
                ?: MapplsConstants.MAXIMUM_ZOOM.toDouble(),
        )
    }
}
