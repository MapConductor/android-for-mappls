package com.mapconductor.mappls.polygon

import com.mapconductor.core.polygon.AbstractPolygonOverlayRenderer
import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.core.polygon.PolygonManagerInterface
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polygon.unionHoles
import com.mapconductor.mappls.MapplsActualPolygon
import com.mapconductor.mappls.MapplsMapViewHolderInterface
import com.mapconductor.mappls.createMapplsPolygons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Mappls Polygon Overlay Renderer
 *
 * 使用 GeoJSON hole polygons（与 React SDK 相同的方式）而不是 tile-based masking
 */
class MapplsPolygonOverlayRenderer(
    val layer: MapplsPolygonLayer,
    val polygonManager: PolygonManagerInterface<MapplsActualPolygon>,
    override val holder: MapplsMapViewHolderInterface,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractPolygonOverlayRenderer<MapplsActualPolygon>() {
    override suspend fun onRemove(data: List<PolygonEntityInterface<MapplsActualPolygon>>) {
        coroutine.launch {
            holder.map.style?.let {
                layer.draw(getAllPolygonEntities(), it)
            }
        }
    }

    override suspend fun onPostProcess() {
        val polygons = getAllPolygonEntities()

        holder.map.style?.let {
            coroutine.launch {
                layer.draw(polygons, it)
            }
        }
    }

    override suspend fun removePolygon(entity: PolygonEntityInterface<MapplsActualPolygon>) {
        // 不单独删除，通过 onPostProcess 重绘所有多边形
    }

    override suspend fun createPolygon(state: PolygonState): MapplsActualPolygon? {
        val resolved = if (state.holes.size > 1) state.unionHoles() else state
        val features =
            createMapplsPolygons(
                id = resolved.id,
                points = resolved.points,
                holes = resolved.holes,
                geodesic = resolved.geodesic,
                fillColor = resolved.fillColor,
                zIndex = resolved.zIndex,
            )

        if (features.isEmpty()) {
            return null
        }
        return features // MapplsActualPolygon = List<Feature>
    }

    override suspend fun updatePolygonProperties(
        polygon: MapplsActualPolygon,
        current: PolygonEntityInterface<MapplsActualPolygon>,
        prev: PolygonEntityInterface<MapplsActualPolygon>,
    ): MapplsActualPolygon? {
        val finger = current.fingerPrint
        val prevFinger = prev.fingerPrint

        if (finger != prevFinger) {
            return createPolygon(current.state)
        }
        return polygon
    }

    private fun getAllPolygonEntities(): List<PolygonEntityInterface<MapplsActualPolygon>> =
        polygonManager.allEntities()
}
