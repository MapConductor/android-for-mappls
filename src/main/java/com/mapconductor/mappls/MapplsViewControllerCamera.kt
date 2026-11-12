package com.mapconductor.mappls

import android.view.View
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.CameraPaddingEmulation
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapPaddings
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
    CameraPaddingEmulation.fromNativeCamera(
        MapplsCameraStateSnapshot(
            cameraPosition = holder.map.cameraPosition,
            logicalTiltHint = lastLogicalCameraPosition?.tilt,
            density = density(),
        ).toMapCameraPosition(),
        cameraPaddings,
        MAPPLS_VERTICAL_FOV,
        viewportHeightDp(),
    )

/** 地図の高さ（dp）。パディングのエミュレーションで遠近を解くのに使う。 */
internal fun MapplsViewController.viewportHeightDp(): Double = holder.mapView.height / density().toDouble()

/**
 * パディングを中心のずらしに置き換えたカメラ。Mappls のパディングは常に 0 にする。
 *
 * ネイティブのパディングは視錐台を上へ広げる。tilt があると上のパディングが
 * 大きいほど画面の上端が地平線の先まで届き、そこまでタイルを読みに行って固まる
 * （パディング中は tilt も 56° 前後に下げられる）。中心をずらせば、パディング無しと
 * 同じ視錐台のまま target をパディング中央に置ける。paddings が null なら今のパディングを使い続ける。
 */
internal fun MapplsViewController.toNativeCamera(position: MapCameraPosition): MapCameraPosition {
    position.paddings?.let { cameraPaddings = it }
    return CameraPaddingEmulation
        .toNativeCamera(position.copy(paddings = cameraPaddings), MAPPLS_VERTICAL_FOV, viewportHeightDp())
        .copy(paddings = MapPaddings.Zeros)
}

/** 地図がまだ大きさを持たない（レイアウト前）ときは、パディングを解けないので最初のレイアウトまで待つ。 */
private fun MapplsViewController.needsLayoutFor(position: MapCameraPosition): Boolean {
    val paddings = position.paddings ?: cameraPaddings
    val padded = paddings.top != 0.0 || paddings.left != 0.0 || paddings.bottom != 0.0 || paddings.right != 0.0
    return padded && holder.mapView.height <= 0
}

private fun MapplsViewController.afterFirstLayout(action: () -> Unit) {
    holder.mapView.addOnLayoutChangeListener(
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                if (bottom - top <= 0) return
                v.removeOnLayoutChangeListener(this)
                action()
            }
        },
    )
}

internal fun MapplsViewController.density(): Float = holder.mapView.resources.displayMetrics.density

internal fun MapplsViewController.getMapCameraPosition(camera: MapCameraPositionInterface): MapCameraPosition? {
    // 4 隅の逆投影は全プロバイダ共通なのでコアの buildVisibleRegion を使う。
    val visibleRegion = holder.buildVisibleRegion() ?: return null
    return MapCameraPosition.from(camera).copy(visibleRegion = visibleRegion)
}

internal fun MapplsViewController.handleMoveCamera(position: MapCameraPosition) {
    lastLogicalCameraPosition = position
    mainCoroutine.launch {
        if (needsLayoutFor(position)) {
            afterFirstLayout { handleMoveCamera(position) }
            return@launch
        }
        val cameraPos = toNativeCamera(position).toCameraPosition(density())
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
        if (needsLayoutFor(position)) {
            afterFirstLayout { handleAnimateCamera(position, duration) }
            return@launch
        }
        val cameraPos = toNativeCamera(position).toCameraPosition(density())
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
