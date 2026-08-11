package com.mapconductor.mappls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mapconductor.compose.map.MapViewBase
import com.mapconductor.core.OnCameraMoveHandler
import com.mapconductor.core.OnMapEventHandler
import com.mapconductor.core.OnMapLoadedHandler
import com.mapconductor.core.circle.CircleManager
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.core.marker.MarkerEventControllerInterface
import com.mapconductor.core.marker.MarkerManager
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerRenderingStrategyInterface
import com.mapconductor.core.marker.MarkerRenderingSupport
import com.mapconductor.core.marker.MarkerRenderingSupportKey
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.core.marker.StrategyMarkerController
import com.mapconductor.core.polygon.PolygonManager
import com.mapconductor.core.polyline.PolylineManager
import com.mapconductor.mappls.circle.MapplsCircleController
import com.mapconductor.mappls.circle.MapplsCircleLayer
import com.mapconductor.mappls.circle.MapplsCircleOverlayRenderer
import com.mapconductor.mappls.groundimage.MapplsGroundImageController
import com.mapconductor.mappls.groundimage.MapplsGroundImageOverlayRenderer
import com.mapconductor.mappls.marker.MapplsMarkerController
import com.mapconductor.mappls.marker.MapplsMarkerOverlayRenderer
import com.mapconductor.mappls.marker.MarkerDragLayer
import com.mapconductor.mappls.marker.MarkerLayer
import com.mapconductor.mappls.polygon.MapplsPolygonConductor
import com.mapconductor.mappls.polygon.MapplsPolygonLayer
import com.mapconductor.mappls.polygon.MapplsPolygonOverlayRenderer
import com.mapconductor.mappls.polyline.MapplsPolylineController
import com.mapconductor.mappls.polyline.MapplsPolylineLayer
import com.mapconductor.mappls.polyline.MapplsPolylineOverlayRenderer
import com.mapconductor.mappls.raster.MapplsRasterLayerController
import com.mapconductor.mappls.raster.MapplsRasterLayerOverlayRenderer
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.MapplsMapOptions
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.Style
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun MapplsMapView(
    state: MapplsViewState,
    modifier: Modifier = Modifier,
    markerTiling: MarkerTilingOptions? = null,
    cameraRestriction: CameraRestriction? = null,
    sdkInitialize: (suspend (Context) -> Boolean)? = null,
    onMapLoaded: OnMapLoadedHandler? = null,
    onMapClick: OnMapEventHandler? = null,
    onMapLongClick: OnMapEventHandler? = null,
    onCameraMoveStart: OnCameraMoveHandler? = null,
    onCameraMove: OnCameraMoveHandler? = null,
    onCameraMoveEnd: OnCameraMoveHandler? = null,
    content: (@Composable MapplsMapViewScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = remember { MapplsMapViewScope() }
    val registry = remember { scope.buildRegistry() }
    val cameraState = remember { mutableStateOf<MapCameraPositionInterface?>(state.cameraPosition) }

    MapViewBase(
        state = state,
        cameraState = cameraState,
        modifier = modifier,
        viewProvider = {
            val cameraPosition =
                state.cameraPosition.toCameraPosition()
            val mapInitOptions =
                MapplsMapOptions
                    .createFromAttributes(context)
                    .camera(cameraPosition)
                    .textureMode(true)
            // Don't set style here - it will be set in holderProvider

            MapView(context, mapInitOptions)
        },
        scope = scope,
        registry = registry,
        onMapLoaded = onMapLoaded,
        holderProvider = { mapView ->
            suspendCancellableCoroutine { continuation ->
                mapView.getMapAsync(
                    object : OnMapReadyCallback {
                        override fun onMapReady(map: MapplsMap) {
                            // 認証が通ると SDK がアカウントの既定スタイルを読み込む。
                            // 名前指定があればそのスタイルへ差し替え、読み込み完了後に resume する。
                            val designName = state.mapDesignType.styleName
                            if (designName.isEmpty()) {
                                map.getStyle {
                                    continuation.resume(
                                        MapplsMapViewHolder(mapView, map),
                                    ) { _, _, _ -> }
                                }
                            } else {
                                map.setMapplsStyle(
                                    designName,
                                    Style.OnStyleLoaded {
                                        continuation.resume(
                                            MapplsMapViewHolder(mapView, map),
                                        ) { _, _, _ -> }
                                    },
                                )
                            }
                        }

                        override fun onMapError(
                            code: Int,
                            message: String?,
                        ) {
                            // 認証エラー（コンフィグファイル不一致など）はここへ来る。
                            // resume しないので地図は表示されないまま — ログで原因を残す。
                            Log.e("Mappls", "Map init error $code: $message")
                        }
                    },
                )
            }
        },
        controllerProvider = { holder ->
            createMapplsViewController(
                holder = holder,
                markerTiling = markerTiling ?: MarkerTilingOptions.Default,
                serviceRegistry = state.serviceRegistry,
            ).also { mapController ->
                // Store controller reference in holder
                mapController.setCameraMoveStartListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMoveStart?.invoke(it)
                }
                mapController.setCameraMoveListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMove?.invoke(it)
                }
                mapController.setCameraMoveEndListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMoveEnd?.invoke(it)
                }
                mapController.setMapClickListener(onMapClick)
                mapController.setMapDesignTypeChangeListener(state::onMapDesignTypeChange)
                mapController.setMapLongClickListener(onMapLongClick)
                cameraRestriction?.let { mapController.setCameraRestriction(it) }
                state.setController(mapController)
                // Post an initial camera update after layout to compute visibleRegion correctly
                holder.mapView.post { mapController.sendInitialCameraUpdate() }
            }
        },
        sdkInitialize = {
            // 外部タイルのホワイトリスト回避（MapplsHttpBridge のコメント参照）は
            // 地図生成前に必ず入れる。sdkInitialize を差し替えられても失われないよう
            // ここで無条件に呼ぶ。
            MapplsHttpBridge.install()
            if (sdkInitialize != null) {
                sdkInitialize(context)
            } else {
                Mappls.getInstance(context)
                true
            }
        },
        // Pass content if it needs to be rendered within the overlay providers in MapViewBase,
        // or handle it here if it's specific to MapplsMapView structure before calling MapViewBase.
        // For now, assuming content relates to overlay definitions.
        content = content, // This might need adjustment based on how overlays are handled
    )
}

/**
 * Creates the imperative controller graph used by both the Compose MapView and non-Compose hosts
 * such as React Native. Keeping this construction here prevents provider-specific layer setup from
 * being duplicated by each UI integration.
 *
 * @param serviceRegistry 登録先のサービスレジストリ。Compose からは `state.serviceRegistry` を渡す
 *   （react-sdk / ios-sdk と同じく持ち主は state）。React Native / Cordova のような非 Compose
 *   ホストは state を持たないので、自前のレジストリを渡す。
 */
fun createMapplsViewController(
    holder: MapplsMapViewHolderInterface,
    markerTiling: MarkerTilingOptions = MarkerTilingOptions.Default,
    serviceRegistry: MutableMapServiceRegistry? = null,
): MapplsViewController {
    // 非 Compose ホスト（React Native / Cordova）はこちらが入口なので、ここでも入れる
    MapplsHttpBridge.install()
    val markerController = getMarkerController(holder, markerTiling)
    val mapController =
        MapplsViewController(
            holder = holder,
            markerController = markerController,
            polylineController = getPolylineController(holder),
            polygonController = getPolygonController(holder),
            groundImageController = getGroundImageController(holder),
            circleController = getCircleController(holder),
            rasterLayerController = getRasterLayerController(holder),
        )

    serviceRegistry?.let { registry ->
        MapplsCapabilities.declare(registry)
        registry.put(
            MarkerRenderingSupportKey,
            object : MarkerRenderingSupport<MapplsActualMarker> {
                override fun createMarkerRenderer(
                    strategy: MarkerRenderingStrategyInterface<MapplsActualMarker>,
                ): MarkerOverlayRendererInterface<MapplsActualMarker> = mapController.createMarkerRenderer(strategy)

                override fun createMarkerEventController(
                    controller: StrategyMarkerController<MapplsActualMarker>,
                    renderer: MarkerOverlayRendererInterface<MapplsActualMarker>,
                ): MarkerEventControllerInterface<MapplsActualMarker> =
                    mapController.createMarkerEventController(controller, renderer)

                override fun registerMarkerEventController(
                    controller: MarkerEventControllerInterface<MapplsActualMarker>,
                ) {
                    mapController.registerMarkerEventController(controller)
                }

                override fun onMarkerRenderingReady() {
                    mapController.sendInitialCameraUpdate()
                }
            },
        )
    }
    return mapController
}

fun getMarkerController(
    holder: MapplsMapViewHolderInterface,
    markerTiling: MarkerTilingOptions,
): MapplsMarkerController {
    val manager = MarkerManager.defaultManager<MapplsActualMarker>()
    val markerLayer =
        MarkerLayer(
            sourceId = "markers-source",
            layerId = "markers-layer",
        )
    val dragLayer =
        MarkerDragLayer(
            sourceId = "marker-drag-source",
            layerId = "marker-drag-layer",
        )
    val renderer =
        MapplsMarkerOverlayRenderer(
            holder = holder,
            markerLayer = markerLayer,
            dragLayer = dragLayer,
            markerManager = manager,
        )

    val controller =
        MapplsMarkerController(
            renderer = renderer,
            markerTiling = markerTiling,
        )
    return controller
}

fun getPolylineController(holder: MapplsMapViewHolderInterface): MapplsPolylineController {
    val polylineLayer =
        MapplsPolylineLayer(
            sourceId = "polyline-source",
            layerId = "polyline-layer",
        )
    val polylineManager = PolylineManager<MapplsActualPolyline>()

    val renderer =
        MapplsPolylineOverlayRenderer(
            layer = polylineLayer,
            polylineManager = polylineManager,
            holder = holder,
        )

    val controller =
        MapplsPolylineController(
            renderer = renderer,
        )
    return controller
}

fun getPolygonController(holder: MapplsMapViewHolderInterface): MapplsPolygonConductor {
    val polylineLayer =
        MapplsPolylineLayer(
            sourceId = "polygon-outline-source",
            layerId = "polygon-outline-layer",
        )
    val polylineManager = PolylineManager<MapplsActualPolyline>()
    val polylineOverlayRenderer =
        MapplsPolylineOverlayRenderer(
            layer = polylineLayer,
            polylineManager = polylineManager,
            holder = holder,
        )

    val polygonManager = PolygonManager<MapplsActualPolygon>()
    val polygonLayer =
        MapplsPolygonLayer(
            sourceId = "polygon-fill-source",
            layerId = "polygon-fill-layer",
        )
    val polygonOverlayRenderer =
        MapplsPolygonOverlayRenderer(
            layer = polygonLayer,
            polygonManager = polygonManager,
            holder = holder,
        )

    return MapplsPolygonConductor(
        polygonOverlay = polygonOverlayRenderer,
        polylineOverlay = polylineOverlayRenderer,
    )
}

fun getCircleController(holder: MapplsMapViewHolderInterface): MapplsCircleController {
    val circleLayer =
        MapplsCircleLayer(
            sourceId = "circle-source",
            layerId = "circle-layer",
        )
    val circleManager = CircleManager<MapplsActualCircle>()
    val renderer =
        MapplsCircleOverlayRenderer(
            layer = circleLayer,
            circleManager = circleManager,
            holder = holder,
        )
    return MapplsCircleController(
        renderer = renderer,
        circleManager = circleManager,
    )
}

fun getRasterLayerController(holder: MapplsMapViewHolderInterface): MapplsRasterLayerController {
    val renderer =
        MapplsRasterLayerOverlayRenderer(
            holder = holder,
        )
    return MapplsRasterLayerController(
        renderer = renderer,
    )
}

fun getGroundImageController(holder: MapplsMapViewHolderInterface): MapplsGroundImageController {
    val renderer =
        MapplsGroundImageOverlayRenderer(
            holder = holder,
        )
    return MapplsGroundImageController(renderer = renderer)
}

internal fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
