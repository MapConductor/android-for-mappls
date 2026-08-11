package com.mapconductor.mappls.polyline

import com.mapconductor.core.polyline.AbstractPolylineOverlayRenderer
import com.mapconductor.core.polyline.PolylineEntityInterface
import com.mapconductor.core.polyline.PolylineManagerInterface
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.mappls.MapplsActualPolyline
import com.mapconductor.mappls.MapplsMapViewHolderInterface
import com.mapconductor.mappls.createMapplsLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapplsPolylineOverlayRenderer(
    val layer: MapplsPolylineLayer,
    val polylineManager: PolylineManagerInterface<MapplsActualPolyline>,
    override val holder: MapplsMapViewHolderInterface,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractPolylineOverlayRenderer<MapplsActualPolyline>() {
    private fun resolveZIndex(state: PolylineState): Int =
        if (state.zIndex != 0) {
            state.zIndex
        } else {
            (state.extra as? Int) ?: 0
        }

    override suspend fun createPolyline(state: PolylineState): MapplsActualPolyline? =
        createMapplsLines(
            id = state.id,
            points = state.points,
            geodesic = state.geodesic,
            strokeColor = state.strokeColor,
            strokeWidth = state.strokeWidth,
            zIndex = resolveZIndex(state),
        )

    override suspend fun updatePolylineProperties(
        polyline: MapplsActualPolyline,
        current: PolylineEntityInterface<MapplsActualPolyline>,
        prev: PolylineEntityInterface<MapplsActualPolyline>,
    ): MapplsActualPolyline {
        // Recreate features to apply updated properties
        return createMapplsLines(
            id = current.state.id,
            points = current.state.points,
            geodesic = current.state.geodesic,
            strokeColor = current.state.strokeColor,
            strokeWidth = current.state.strokeWidth,
            zIndex = resolveZIndex(current.state),
        )
    }

    override suspend fun removePolyline(entity: PolylineEntityInterface<MapplsActualPolyline>) {
        // Remove features by rewriting source without this entity
        // Actual removal is handled in onPostProcess by redrawing all remaining polylines
    }

    override suspend fun onPostProcess() {
        val polylines = getAllPolylineEntities()
        holder.map.style?.let {
            coroutine.launch {
                layer.draw(polylines, it)
            }
        }
    }

    private fun getAllPolylineEntities(): List<PolylineEntityInterface<MapplsActualPolyline>> {
        // This would need access to the polyline manager
        // For now, we'll implement a simple workaround
        return polylineManager.allEntities()
    }

    fun redraw() {
        val entities = polylineManager.allEntities()
        holder.map.style?.let {
            coroutine.launch {
                layer.draw(entities, it)
            }
        }
    }
}
