package com.mapconductor.mappls.polygon

import com.mapconductor.core.controller.OverlayHit
import com.mapconductor.core.controller.OverlayKind
import com.mapconductor.core.controller.SlottedOverlayController
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.polygon.PolygonEntity
import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.core.polygon.PolygonEvent
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polyline.PolylineEntity
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.mappls.polyline.MapplsPolylineOverlayRenderer

class MapplsPolygonConductor(
    val polygonOverlay: MapplsPolygonOverlayRenderer,
    val polylineOverlay: MapplsPolylineOverlayRenderer,
) : SlottedOverlayController<
        PolygonState,
        PolygonEntityInterface<PolygonState>,
    > {
    override val zIndex: Int = 2

    var clickListener: ((PolygonEvent) -> Unit)? = null

    override suspend fun add(data: List<PolygonState>) {
        val nextIds = data.asSequence().map { it.id }.toSet()
        val prevIds =
            polygonOverlay.polygonManager
                .allEntities()
                .asSequence()
                .map { it.state.id }
                .toSet()
        val removeIds = prevIds - nextIds

        removeIds.forEach { id ->
            polygonOverlay.polygonManager.removeEntity(id)
            polylineOverlay.polylineManager.removeEntity("outline-$id")
        }

        data.forEach { polygonState ->

            polygonOverlay.createPolygon(polygonState)?.let { polygon ->
                val polygonEntity =
                    PolygonEntity(
                        polygon = polygon,
                        state = polygonState,
                    )
                polygonOverlay.polygonManager.registerEntity(polygonEntity)
            }

            val polylineState = polygonState.toPolylineState()
            polylineOverlay.createPolyline(polylineState)?.let { polyline ->
                val polylineEntity =
                    PolylineEntity(
                        polyline = polyline,
                        state = polylineState,
                    )
                polylineOverlay.polylineManager.registerEntity(polylineEntity)
            }
        }
        polygonOverlay.onPostProcess()
        polylineOverlay.onPostProcess()
    }

    override suspend fun update(state: PolygonState) {
        polygonOverlay.createPolygon(state)?.let { polygon ->
            val polygonEntity =
                PolygonEntity(
                    polygon = polygon,
                    state = state,
                )
            polygonOverlay.polygonManager.registerEntity(polygonEntity)
        }

        val polylineState = state.toPolylineState()
        polylineOverlay.createPolyline(polylineState)?.let { polyline ->
            val polylineEntity =
                PolylineEntity(
                    polyline = polyline,
                    state = polylineState,
                )
            polylineOverlay.polylineManager.registerEntity(polylineEntity)
        }
        polygonOverlay.onPostProcess()
        polylineOverlay.onPostProcess()
    }

    fun dispatchClick(event: PolygonEvent) {
        // 配送座標の wrap は PolygonEvent の生成時に一元化済み。
        event.state.onClick?.invoke(event)
        clickListener?.invoke(event)
    }

    @Suppress("UNCHECKED_CAST")
    override fun find(position: GeoPointInterface): PolygonEntityInterface<PolygonState>? =
        polygonOverlay.polygonManager.find(position) as? PolygonEntityInterface<PolygonState>

    override suspend fun clear() {
        polygonOverlay.polygonManager.clear()
        polylineOverlay.polylineManager.clear()
        polygonOverlay.onPostProcess()
        polylineOverlay.onPostProcess()
    }

    // ── Capable ファサードのスロットとクリックカスケードへの参加 ──────────
    //
    // このコンダクタはコアの PolygonController を継承していない（ポリゴンの輪郭を
    // ポリラインとして描くために polygon と polyline の 2 レンダラを束ねている）。
    // それでも扱いは他プロバイダのポリゴンと同じでなければならないので、
    // スロットの契約はここで自前で満たす。
    //
    // 実際、Step 4 でこれを実装し忘れて `hasPolygon` が常に false になり、
    // ポリゴン単体の状態更新が黙って捨てられていた。

    override val kind: OverlayKind = OverlayKind.Polygon

    override fun has(id: String): Boolean = polygonOverlay.polygonManager.hasEntity(id)

    @Suppress("UNCHECKED_CAST")
    override fun setClickListenerAny(listener: Any?) {
        clickListener = listener as? ((PolygonEvent) -> Unit)
    }

    override fun resolveTap(position: GeoPointInterface): OverlayHit? =
        find(position)?.let { entity ->
            OverlayHit(OverlayKind.Polygon, position) {
                dispatchClick(PolygonEvent(entity.state, position))
            }
        }

    override fun destroy() {
        // No native resources to clean up for polygons
    }
}

private fun PolygonState.toPolylineState(): PolylineState {
    val closedPoints =
        if (points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
    return PolylineState(
        points = closedPoints,
        id = "outline-${this.id}",
        strokeColor = this.strokeColor,
        strokeWidth = this.strokeWidth,
        geodesic = this.geodesic,
        zIndex = this.zIndex,
        extra = this.extra,
    )
}
