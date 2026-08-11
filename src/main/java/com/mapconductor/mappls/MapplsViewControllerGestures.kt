package com.mapconductor.mappls

import androidx.compose.ui.geometry.Offset
import com.mappls.sdk.gestures.MoveGestureDetector
import com.mappls.sdk.maps.geometry.LatLng
import android.annotation.SuppressLint
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.launch

/**
 * マーカーのドラッグ中だけ地図のスクロールを止めるための、タッチの横取り。
 *
 * Mappls のジェスチャ設定を切るだけでは、ドラッグ開始前に始まった慣性が
 * 残って地図が流れる。ビューの手前で touch を受けて握りつぶす。
 *
 * 同じファイルの `handleMapClick` / `handleMapLongClick` はタップの受け口。
 * タップのカスケード（marker → circle → groundImage → polyline → polygon → map）は
 * コアの [com.mapconductor.core.controller.BaseMapViewController.dispatchTap] が回すので、
 * ここは座標を変換して渡すだけ。長押しはドラッグ開始の判定が要るのでここに残す。
 */
internal fun MapplsViewController.installDragTouchInterceptor() {
    if (dragTouchInterceptor != null) return
    val view = holder.mapView
    dragTouchInterceptor =
        View.OnTouchListener { _, event ->
            val controller = activeDragController ?: return@OnTouchListener false
            val selected = controller.getSelectedMarker() ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val pos = holder.fromScreenOffsetSync(Offset(event.x, event.y))
                    if (pos != null) {
                        selected.state.position = pos
                        controller.updateDragPosition(pos)
                        controller.dispatchDrag(selected.state)
                    }
                    true // consume to prevent map panning
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val point = holder.map.projection.fromScreenLocation(PointF(event.x, event.y))
                    controller.updateDragPosition(point.toGeoPoint())
                    controller.setSelectedMarker(null)
                    controller.dispatchDragEnd(selected.state)
                    try {
                        holder.map.uiSettings?.isScrollGesturesEnabled = wasScrollEnabledBeforeDrag == true
                    } catch (e: Exception) {
                        Log.w("Mappls", "Failed to re-enable scroll gestures: ${e.message}")
                    } finally {
                        wasScrollEnabledBeforeDrag = null
                    }
                    removeDragTouchInterceptor()
                    activeDragController = null
                    true
                }
                else -> false
            }
        }
    view.setOnTouchListener(dragTouchInterceptor)
}

@SuppressLint("ClickableViewAccessibility")
internal fun MapplsViewController.removeDragTouchInterceptor() {
    val view = holder.mapView
    view.setOnTouchListener(null)
    dragTouchInterceptor = null
}

internal fun MapplsViewController.handleMapClick(point: LatLng): Boolean = dispatchTap(point.toGeoPoint())

internal fun MapplsViewController.handleMapLongClick(point: LatLng): Boolean {
    val touchPosition = point.toGeoPoint()
    markerEventControllers.forEach { controller ->
        controller.find(touchPosition)?.let { entity ->
            if (entity.state.draggable) {
                // Disable map scroll while dragging a marker
                try {
                    val ui = holder.map.uiSettings
                    wasScrollEnabledBeforeDrag = ui?.isScrollGesturesEnabled
                    ui?.isScrollGesturesEnabled = false
                } catch (e: Exception) {
                    Log.w("Mappls", "Failed to disable scroll gestures: ${e.message}")
                }
                activeDragController = controller
                controller.setSelectedMarker(entity)
                controller.dispatchDragStart(entity.state)
                // Intercept touch to move marker without moving the map
                installDragTouchInterceptor()
                return true
            }
        }
    }

    emitMapLongClick(touchPosition)
    return true
}

internal fun MapplsViewController.handleMoveBegin(detector: MoveGestureDetector) {
    mainCoroutine.launch {
        getMapCameraPosition(readLogicalCameraPosition())?.let { mapCameraPosition ->
            emitCameraMoveStart(mapCameraPosition)
        }
    }
}

internal fun MapplsViewController.handleMove(detector: MoveGestureDetector) {
    val controller = activeDragController ?: return
    controller.getSelectedMarker()?.let { entity ->

        val screenCoordinate =
            Offset(
                detector.focalPoint.x,
                detector.focalPoint.y,
            )

        holder.fromScreenOffsetSync(screenCoordinate)?.let {
            entity.state.position = it
            controller.updateDragPosition(it)
        }

        controller.dispatchDrag(entity.state)
    }
}

internal fun MapplsViewController.handleMoveEnd(detector: MoveGestureDetector) {
    val controller = activeDragController ?: return
    controller.getSelectedMarker()?.let { entity ->
        val screenCoordinate =
            PointF(
                detector.focalPoint.x,
                detector.focalPoint.y,
            )
        val point = holder.map.projection.fromScreenLocation(screenCoordinate)
        controller.updateDragPosition(point.toGeoPoint())
        controller.setSelectedMarker(null)
        controller.dispatchDragEnd(entity.state)
        // Re-enable map scroll after dragging finishes
        try {
            holder.map.uiSettings?.isScrollGesturesEnabled = wasScrollEnabledBeforeDrag == true
        } catch (e: Exception) {
            Log.w("Mappls", "Failed to re-enable scroll gestures: ${e.message}")
        } finally {
            wasScrollEnabledBeforeDrag = null
        }
        removeDragTouchInterceptor()
        activeDragController = null
    }
}
