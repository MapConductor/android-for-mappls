package com.mapconductor.mappls

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.gson.JsonObject
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.geometry.buildUnwrappedPolygonRings
import com.mapconductor.core.geometry.buildUnwrappedPolylinePath
import com.mapconductor.core.geometry.closeRing
import com.mapconductor.mappls.polygon.MapplsPolygonLayer
import com.mapconductor.mappls.polyline.MapplsPolylineLayer
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.LineString
import com.mappls.sdk.geojson.Polygon as GLPolygon

internal fun createMapplsLines(
    id: String,
    points: List<GeoPointInterface>,
    geodesic: Boolean,
    strokeColor: Color,
    strokeWidth: Dp,
    zIndex: Int = 0,
): List<Feature> {
    // unwrap 座標の単一パス。Mappls GL は ±180 超の経度を扱えるため分割不要（継ぎ目が出ない）。
    val path = buildUnwrappedPolylinePath(points, geodesic)
    if (path.size < 2) return emptyList()
    val pts = path.map { GeoPoint.from(it).toPoint() }
    val fid = "polyline-$id-0"
    return listOf(
        Feature.fromGeometry(
            LineString.fromLngLats(pts),
            JsonObject().apply {
                addProperty(MapplsPolylineLayer.Prop.STROKE_COLOR, strokeColor.toMapplsColorString())
                addProperty(MapplsPolylineLayer.Prop.STROKE_WIDTH, strokeWidth.value)
                addProperty("zIndex", zIndex)
                addProperty("id", fid)
            },
            fid,
        ),
    )
}

fun Color.toMapplsColorString(): String {
    val red = (this.red * 255).toInt()
    val green = (this.green * 255).toInt()
    val blue = (this.blue * 255).toInt()
    val alpha = this.alpha
    return "rgba($red, $green, $blue, $alpha)"
}

internal fun createMapplsPolygons(
    id: String,
    points: List<GeoPointInterface>,
    holes: List<List<GeoPointInterface>> = emptyList(),
    geodesic: Boolean,
    fillColor: Color,
    zIndex: Int,
): List<Feature> {
    // unwrap 座標の外周 1 リング + 全穴。Mappls GL は ±180 超の経度を扱えるため分割不要で、
    // ±180 跨ぎのポリゴンでも穴を保持できる。
    val polygonRings = buildUnwrappedPolygonRings(points, holes, geodesic)
    val outer = polygonRings.outerRings.firstOrNull() ?: return emptyList()
    val holeRings =
        polygonRings.holeRings.mapNotNull { hole ->
            val closed = closeRing(hole.map { GeoPoint.from(it).toPoint() })
            if (closed.size < 4) null else closed
        }

    val closed = closeRing(outer.map { GeoPoint.from(it).toPoint() })
    val fid = "polygon-$id-0"
    return listOf(
        Feature.fromGeometry(
            GLPolygon.fromLngLats(listOf(closed) + holeRings),
            JsonObject().apply {
                addProperty(MapplsPolygonLayer.Prop.FILL_COLOR, fillColor.toMapplsColorString())
                addProperty("zIndex", zIndex)
                addProperty("id", fid)
            },
            fid,
        ),
    )
}
