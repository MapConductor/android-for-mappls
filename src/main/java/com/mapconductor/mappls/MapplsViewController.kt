package com.mapconductor.mappls

import com.mapconductor.core.circle.OnCircleEventHandler
import com.mapconductor.core.controller.BaseMapViewController
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.OnGroundImageEventHandler
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapUISettings
import com.mapconductor.core.marker.DefaultMarkerEventController
import com.mapconductor.core.marker.MarkerAnimationOverlayHost
import com.mapconductor.core.marker.MarkerEventControllerInterface
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerRenderingStrategyInterface
import com.mapconductor.core.marker.OnMarkerEventHandler
import com.mapconductor.core.marker.StrategyMarkerController
import com.mapconductor.core.marker.dispatchGeoMarkerClick
import com.mapconductor.core.polygon.OnPolygonEventHandler
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polyline.OnPolylineEventHandler
import com.mapconductor.mappls.circle.MapplsCircleController
import com.mapconductor.mappls.groundimage.MapplsGroundImageController
import com.mapconductor.mappls.marker.MapplsMarkerController
import com.mapconductor.mappls.marker.MapplsMarkerOverlayRenderer
import com.mapconductor.mappls.marker.MarkerDragLayer
import com.mapconductor.mappls.marker.MarkerLayer
import com.mapconductor.mappls.polygon.MapplsPolygonConductor
import com.mapconductor.mappls.polyline.MapplsPolylineController
import com.mapconductor.mappls.raster.MapplsRasterLayerController
import com.mappls.sdk.gestures.MoveGestureDetector
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.style.OnStyleLoadListener
import java.util.UUID
import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

typealias MapplsDesignTypeChangeHandler = (MapplsMapDesignTypeInterface) -> Unit

class MapplsViewController(
    override val holder: MapplsMapViewHolderInterface,
    internal val markerController: MapplsMarkerController,
    internal val polylineController: MapplsPolylineController,
    internal val polygonController: MapplsPolygonConductor,
    internal val groundImageController: MapplsGroundImageController,
    internal val circleController: MapplsCircleController,
    internal val rasterLayerController: MapplsRasterLayerController,
    override val mainCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
    override val defaultCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : BaseMapViewController(),
    MapplsViewControllerInterface,
    MapplsMap.OnMapClickListener,
    MapplsMap.OnMapLongClickListener,
    MapplsMap.OnMoveListener,
    MapplsMap.OnCameraMoveListener,
    MapplsMap.OnCameraIdleListener {
    // Keep reference to the style instance to avoid getting a new one
    internal var styleInstance: Style? = null
    internal var wasScrollEnabledBeforeDrag: Boolean? = null
    internal var dragTouchInterceptor: View.OnTouchListener? = null
    internal val polygonZLayers: MutableSet<Int> = mutableSetOf()
    internal val markerEventControllers = mutableListOf<DefaultMarkerEventController<MapplsActualMarker>>()
    internal var activeDragController: DefaultMarkerEventController<MapplsActualMarker>? = null
    internal var markerClickListener: OnMarkerEventHandler? = null
    internal var markerDragStartListener: OnMarkerEventHandler? = null
    internal var markerDragListener: OnMarkerEventHandler? = null
    internal var markerDragEndListener: OnMarkerEventHandler? = null
    internal var markerAnimateStartListener: OnMarkerEventHandler? = null
    internal var markerAnimateEndListener: OnMarkerEventHandler? = null

    internal var lastLogicalCameraPosition: MapCameraPosition? = null

    init {
        // Style should already be loaded by holderProvider
        val style = holder.map.style
        if (style != null) {
            setupStyle(style)
            // Trigger initial camera update after style is ready
            sendInitialCameraUpdate()
        }

        setupListeners()
        registerOverlayController(markerController)
        registerOverlayController(polylineController)
        registerOverlayController(polygonController)
        registerOverlayController(groundImageController)
        registerOverlayController(circleController)
        registerOverlayController(rasterLayerController)
        registerMarkerEventController(DefaultMarkerEventController(markerController))

        markerController.setRasterLayerCallback { state ->
            if (state != null) {
                rasterLayerController.upsert(state)
            } else {
                val markerTileLayers =
                    rasterLayerController.rasterLayerManager
                        .allEntities()
                        .filter { it.state.id.startsWith("marker-tile-") }
                markerTileLayers.forEach { entity -> rasterLayerController.removeById(entity.state.id) }
            }
        }
    }

    fun setupListeners() {
        holder.map.addOnCameraMoveListener(this)
        holder.map.addOnCameraIdleListener(this)

        holder.map.removeOnMapClickListener(this)
        holder.map.addOnMapClickListener(this)

        holder.map.removeOnMapLongClickListener(this)
        holder.map.addOnMapLongClickListener(this)

        holder.map.removeOnMoveListener(this)
        holder.map.addOnMoveListener(this)
    }

    override suspend fun clearOverlays() {
        markerController.clear()
        polylineController.clear()
        polygonController.clear()
        groundImageController.clear()
        circleController.clear()
        rasterLayerController.clear()
    }

    override fun moveCamera(position: MapCameraPosition) = handleMoveCamera(position)

    override fun animateCamera(
        position: MapCameraPosition,
        duration: Long,
    ) = handleAnimateCamera(position, duration)

    override fun fitBounds(
        bounds: GeoRectBounds,
        padding: Int,
    ) = handleFitBounds(bounds, padding)

    override fun setCameraRestriction(restriction: CameraRestriction?) = handleCameraRestriction(restriction)

    override fun applyUISettings(settings: MapUISettings) {
        // Mappls の getUiSettings() は @Nullable（MapLibre との差分）
        holder.map.uiSettings?.apply {
            isScrollGesturesEnabled = settings.scrollGesture
            isZoomGesturesEnabled = settings.zoomGesture
            isRotateGesturesEnabled = settings.rotateGesture
            isTiltGesturesEnabled = settings.tiltGesture
        }
    }

    // Provide access to the style instance
    fun getStyleInstance(): Style? = styleInstance

    private var mapDesignTypeChangeListener: MapplsDesignTypeChangeHandler? = null

    override fun setMapDesignType(value: MapplsMapDesignTypeInterface) {
        mainCoroutine.launch {
            // 空文字は「アカウントの既定スタイル」。既に既定が載っているので何もしない
            // （setMapplsStyle("") は無効なスタイル名として弾かれる）。
            if (value.styleName.isEmpty()) return@launch
            holder.map.setMapplsStyle(
                value.styleName,
                object : OnStyleLoadListener {
                    override fun onStyleLoaded(newStyle: Style) {
                        Log.d("Mappls", "Style changed to ${value.styleName}")
                        setupStyle(newStyle)
                    }

                    override fun onError(message: String?) {
                        // スタイル名はアカウント紐付き。存在しない名前はここへ来る
                        Log.w("Mappls", "setMapplsStyle(${value.styleName}) failed: $message")
                    }
                },
            )
        }
    }

    fun sendInitialCameraUpdate() {
        mainCoroutine.launch {
            notifyMapInitialized()
            val mapWidth = holder.mapView.width.toFloat()
            val mapHeight = holder.mapView.height.toFloat()
            if (mapWidth <= 0 || mapHeight <= 0) return@launch

            val camera = readLogicalCameraPosition()
            getMapCameraPosition(camera)?.let { mapCameraPosition ->
                defaultCoroutine.launch { notifyMapCameraPosition(mapCameraPosition) }
            }
        }
    }

    override fun setMapDesignTypeChangeListener(listener: MapplsDesignTypeChangeHandler) {
        mapDesignTypeChangeListener = listener
        // Don't call listener immediately - it may trigger style reload
        // listener(mapDesignType)
    }

    override fun setMarkerAnimationOverlayHost(host: MarkerAnimationOverlayHost?) {
        (markerController.renderer as MapplsMarkerOverlayRenderer).animationOverlayHost = host
    }

    override suspend fun compositionPolygons(data: List<PolygonState>) {
        polygonController.add(data)
        getStyleInstance()?.let { ensurePolygonZLayers(it) }
    }

    override suspend fun updatePolygon(state: PolygonState) {
        polygonController.update(state)
        getStyleInstance()?.let { ensurePolygonZLayers(it) }
    }

    @Deprecated("Use MarkerState.onDragStart instead.")
    override fun setOnMarkerDragStart(listener: OnMarkerEventHandler?) {
        markerDragStartListener = listener
        markerEventControllers.forEach { it.setDragStartListener(listener) }
    }

    @Deprecated("Use MarkerState.onDrag instead.")
    override fun setOnMarkerDrag(listener: OnMarkerEventHandler?) {
        markerDragListener = listener
        markerEventControllers.forEach { it.setDragListener(listener) }
    }

    @Deprecated("Use MarkerState.onDragEnd instead.")
    override fun setOnMarkerDragEnd(listener: OnMarkerEventHandler?) {
        markerDragEndListener = listener
        markerEventControllers.forEach { it.setDragEndListener(listener) }
    }

    @Deprecated("Use PolylineState.onClick instead.")
    override fun setOnPolylineClickListener(listener: OnPolylineEventHandler?) {
        polylineController.clickListener = listener
    }

    @Deprecated("Use PolygonState.onClick instead.")
    override fun setOnPolygonClickListener(listener: OnPolygonEventHandler?) {
        polygonController.clickListener = listener
    }

    @Deprecated("Use CircleState.onClick instead.")
    override fun setOnCircleClickListener(listener: OnCircleEventHandler?) {
        this.circleController.clickListener = listener
    }

    @Deprecated("Use MarkerState.onAnimateStart instead.")
    override fun setOnMarkerAnimateStart(listener: OnMarkerEventHandler?) {
        markerAnimateStartListener = listener
        markerEventControllers.forEach { it.setAnimateStartListener(listener) }
    }

    @Deprecated("Use MarkerState.onAnimateEnd instead.")
    override fun setOnMarkerAnimateEnd(listener: OnMarkerEventHandler?) {
        markerAnimateEndListener = listener
        markerEventControllers.forEach { it.setAnimateEndListener(listener) }
    }

    @Deprecated("Use MarkerState.onClick instead.")
    override fun setOnMarkerClickListener(listener: OnMarkerEventHandler?) {
        markerClickListener = listener
        markerEventControllers.forEach { it.setClickListener(listener) }
    }

    @Deprecated("Use GroundImageState.onClick instead.")
    override fun setOnGroundImageClickListener(listener: OnGroundImageEventHandler?) {
        this.groundImageController.clickListener = listener
    }

    /**
     * マーカーのヒットテスト。クリックカスケードの先頭。
     *
     * Mappls は地図クリックの座標からそのまま引けるので、コアの
     * [dispatchGeoMarkerClick] に委ねる（`clickable = false` の透過もそちら）。
     */
    override fun dispatchMarkerTap(position: GeoPointInterface): Boolean =
        markerEventControllers.dispatchGeoMarkerClick(position)

    // 拡張ファイル（Gestures / Camera）からは基底クラスの protected へ触れないため、
    // ここで internal の入口を用意しておく。
    internal fun emitCameraMoveStart(position: MapCameraPosition) {
        cameraMoveStartCallback?.invoke(position)
    }

    internal fun emitCameraMoveEnd(position: MapCameraPosition) {
        cameraMoveEndCallback?.invoke(position)
    }

    override fun onMapClick(point: LatLng): Boolean = handleMapClick(point)

    override fun onMapLongClick(point: LatLng): Boolean = handleMapLongClick(point)

    override fun onMoveBegin(detector: MoveGestureDetector) = handleMoveBegin(detector)

    override fun onMove(detector: MoveGestureDetector) = handleMove(detector)

    override fun onMoveEnd(detector: MoveGestureDetector) = handleMoveEnd(detector)

    @SuppressLint("ClickableViewAccessibility")
    override fun onCameraMove() {
        mainCoroutine.launch {
            getMapCameraPosition(readLogicalCameraPosition())?.let { mapCameraPosition ->
                defaultCoroutine.launch {
                    notifyMapCameraPosition(mapCameraPosition)
                }
                cameraMoveCallback?.invoke(mapCameraPosition)
            }
        }
    }

    override fun onCameraIdle() {
        mainCoroutine.launch {
            getMapCameraPosition(readLogicalCameraPosition())?.let { mapCameraPosition ->
                defaultCoroutine.launch {
                    notifyMapCameraPosition(mapCameraPosition)
                }
                cameraMoveEndCallback?.invoke(mapCameraPosition)
            }
        }
    }

    internal fun registerMarkerEventController(controller: DefaultMarkerEventController<MapplsActualMarker>) {
        if (markerEventControllers.contains(controller)) return
        markerEventControllers.add(controller)
        controller.setClickListener(markerClickListener)
        controller.setDragStartListener(markerDragStartListener)
        controller.setDragListener(markerDragListener)
        controller.setDragEndListener(markerDragEndListener)
        controller.setAnimateStartListener(markerAnimateStartListener)
        controller.setAnimateEndListener(markerAnimateEndListener)

        val renderer = controller.renderer as MapplsMarkerOverlayRenderer
        styleInstance?.let { style ->
            renderer.ensureDefaultIcon(style)
            ensureGeoJsonSource(style, renderer.markerLayer.sourceId)
            addLayerAboveSafely(
                style = style,
                layer = renderer.markerLayer.layer,
                layerId = renderer.markerLayer.layerId,
                aboveId = polylineController.renderer.layer.layerId,
            )
            ensureGeoJsonSource(style, renderer.dragLayer.sourceId)
            addLayerAboveSafely(
                style = style,
                layer = renderer.dragLayer.layer,
                layerId = renderer.dragLayer.layerId,
                aboveId = renderer.markerLayer.layerId,
            )
            renderer.redraw()
            renderer.drawDragLayer()
        }
    }

    fun createMarkerRenderer(
        strategy: MarkerRenderingStrategyInterface<MapplsActualMarker>,
    ): MarkerOverlayRendererInterface<MapplsActualMarker> {
        val groupId = UUID.randomUUID().toString()
        val markerLayer =
            MarkerLayer(
                sourceId = "markers-source-$groupId",
                layerId = "markers-layer-$groupId",
            )
        val dragLayer =
            MarkerDragLayer(
                sourceId = "marker-drag-source-$groupId",
                layerId = "marker-drag-layer-$groupId",
            )
        return MapplsMarkerOverlayRenderer(
            holder = holder,
            markerManager = strategy.markerManager,
            markerLayer = markerLayer,
            dragLayer = dragLayer,
        )
    }

    fun createMarkerEventController(
        controller: StrategyMarkerController<MapplsActualMarker>,
        renderer: MarkerOverlayRendererInterface<MapplsActualMarker>,
    ): MarkerEventControllerInterface<MapplsActualMarker> = DefaultMarkerEventController(controller)

    fun registerMarkerEventController(controller: MarkerEventControllerInterface<MapplsActualMarker>) {
        @Suppress("UNCHECKED_CAST")
        val typed = controller as? DefaultMarkerEventController<MapplsActualMarker> ?: return
        registerMarkerEventController(typed)
    }
}
