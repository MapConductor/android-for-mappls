package com.mapconductor.mappls.circle

import com.mapconductor.core.circle.CircleEntityInterface
import com.mapconductor.mappls.MapplsActualCircle
import com.mappls.sdk.geojson.FeatureCollection
import com.mappls.sdk.maps.style.expressions.Expression
import com.mappls.sdk.maps.style.layers.FillLayer
import com.mappls.sdk.maps.style.layers.LineLayer
import com.mappls.sdk.maps.style.layers.Property
import com.mappls.sdk.maps.style.layers.PropertyFactory.fillColor
import com.mappls.sdk.maps.style.layers.PropertyFactory.fillSortKey
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineCap
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineColor
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineJoin
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineSortKey
import com.mappls.sdk.maps.style.layers.PropertyFactory.lineWidth
import com.mappls.sdk.maps.style.sources.GeoJsonSource

/**
 * 円を「塗り（FillLayer）＋枠線（LineLayer）」で描画するレイヤ。
 *
 * 以前は native CircleLayer（画面ピクセル半径の式）で描画していたが、geodesic な円
 * （大圏距離で等距離のリング。メルカトル上では真円にならない）を表現できないため、
 * コア共通の circleToRing が生成するポリゴンを描画する方式（Mapbox と同方針）へ統一した。
 */
class MapplsCircleLayer(
    val sourceId: String,
    val layerId: String,
) {
    val strokeLayerId = "$layerId-stroke"

    object Prop {
        const val FILL_COLOR = "fillColor"
        const val STROKE_COLOR = "strokeColor"
        const val STROKE_WIDTH = "strokeWidth"
        const val Z_INDEX = "zIndex"
    }

    val source: GeoJsonSource =
        GeoJsonSource(
            sourceId,
            FeatureCollection.fromFeatures(emptyList()),
        )

    /** 塗りレイヤ。レイヤ順序の互換のため id は従来の layerId を引き継ぐ。 */
    val layer: FillLayer =
        FillLayer(layerId, sourceId).apply {
            setProperties(
                fillColor(Expression.get(Prop.FILL_COLOR)),
                fillSortKey(Expression.get(Prop.Z_INDEX)),
            )
        }

    val strokeLayer: LineLayer =
        LineLayer(strokeLayerId, sourceId).apply {
            setProperties(
                lineJoin(Property.LINE_JOIN_ROUND),
                lineCap(Property.LINE_CAP_ROUND),
                lineColor(Expression.get(Prop.STROKE_COLOR)),
                lineWidth(Expression.get(Prop.STROKE_WIDTH)),
                lineSortKey(Expression.get(Prop.Z_INDEX)),
            )
        }

    fun draw(
        entities: List<CircleEntityInterface<MapplsActualCircle>>,
        style: com.mappls.sdk.maps.Style,
    ) {
        val features = entities.map { it.circle }
        val styleSource =
            try {
                style.getSource(sourceId)
            } catch (_: IllegalStateException) {
                null
            }
        if (styleSource is GeoJsonSource) {
            try {
                styleSource.setGeoJson(FeatureCollection.fromFeatures(features))
                return
            } catch (_: IllegalStateException) {
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}
