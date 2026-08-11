package com.mapconductor.mappls.polyline

import com.mapconductor.core.polyline.PolylineController
import com.mapconductor.core.polyline.PolylineManagerInterface
import com.mapconductor.mappls.MapplsActualPolyline

class MapplsPolylineController(
    override val renderer: MapplsPolylineOverlayRenderer,
    polylineManager: PolylineManagerInterface<MapplsActualPolyline> = renderer.polylineManager,
) : PolylineController<MapplsActualPolyline>(polylineManager, renderer)
