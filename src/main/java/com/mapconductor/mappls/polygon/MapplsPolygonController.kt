package com.mapconductor.mappls.polygon

import com.mapconductor.core.polygon.PolygonController
import com.mapconductor.mappls.MapplsActualPolygon

/**
 * Mappls 用のポリゴンコントローラ。
 *
 * 追加・更新・削除の差分計算、`has`、クリックカスケードはコアの [PolygonController] が担う。
 * 塗りと輪郭（幅のある LineLayer）の 2 系統の描画は [MapplsPolygonCompositeRenderer] が束ねる。
 */
class MapplsPolygonController(
    override val renderer: MapplsPolygonCompositeRenderer,
) : PolygonController<MapplsActualPolygon>(renderer.fill.polygonManager, renderer) {
    // 旧 MapplsPolygonConductor と同じ値を保つ。
    override val zIndex: Int = 2
}
