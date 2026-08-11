package com.mapconductor.mappls.circle

import com.mapconductor.core.circle.CircleController
import com.mapconductor.core.circle.CircleManager
import com.mapconductor.core.circle.CircleManagerInterface
import com.mapconductor.mappls.MapplsActualCircle

class MapplsCircleController(
    override val renderer: MapplsCircleOverlayRenderer,
    circleManager: CircleManagerInterface<MapplsActualCircle> = CircleManager(),
) : CircleController<MapplsActualCircle>(circleManager, renderer)
