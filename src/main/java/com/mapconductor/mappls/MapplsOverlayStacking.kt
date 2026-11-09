package com.mapconductor.mappls

import android.util.Log
import com.mapconductor.core.controller.OverlayKind
import com.mapconductor.core.controller.OverlayLayerStack
import com.mapconductor.core.controller.OverlayStacking
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.style.layers.Layer
import com.mappls.sdk.maps.style.layers.SymbolLayer
import java.util.WeakHashMap

/**
 * オーバーレイの層をスタイルのどこへ入れるか（[OverlayStacking] の帯の順）。
 *
 * 下から: ラスター < GroundImage < ポリゴン < サークル < ポリライン < マーカー
 * （マーカーのタイルはマーカーの帯の一番下、ドラッグ中の層は一番上。
 * ポリゴンのタイルはポリゴンの帯の一番下）。
 *
 * 位置は毎回スタイルの今の並びから決めるので、追加した順・送り直し・
 * スタイルの読み直し（デザイン変更）に左右されない。
 *
 * ラスターだけはベースマップのラベル（SDK の層でない最初の symbol 層）の下に入れる。
 * それ以外の帯はラベルの上に乗る（従来どおり）。
 */
internal object MapplsOverlayStacking {
    private val stacks = WeakHashMap<MapplsMap, OverlayLayerStack>()

    fun stackOf(map: MapplsMap): OverlayLayerStack = synchronized(stacks) { stacks.getOrPut(map) { OverlayLayerStack() } }

    fun key(
        kind: OverlayKind,
        zIndex: Int = 0,
    ) = OverlayStacking.Key(kind, zIndex)

    /** マーカーのタイル（ラスター）: マーカーの帯の一番下。 */
    val MARKER_TILE = key(OverlayKind.Marker, Int.MIN_VALUE)

    /** ポリゴンのタイル（ラスター）: ポリゴンの帯の一番下。 */
    val POLYGON_TILE = key(OverlayKind.Polygon, Int.MIN_VALUE)

    /** ポリラインのタイル（ラスター）: ポリラインの帯の一番下（サークルの上、ネイティブのポリラインの下）。 */
    val POLYLINE_TILE = key(OverlayKind.Polyline, Int.MIN_VALUE)

    /** ドラッグ中のマーカーの層: マーカーの帯の一番上。 */
    val MARKER_DRAG = key(OverlayKind.Marker, Int.MAX_VALUE)

    /**
     * [layer] を [key] の位置へ足す。同じ id の層が既にあれば何もしない
     * （位置を変えたいときは先に [remove] する）。
     */
    fun add(
        map: MapplsMap,
        style: Style,
        layer: Layer,
        key: OverlayStacking.Key,
    ) {
        val stack = stackOf(map)
        stack.put(layer.id, key)
        if (style.getLayer(layer.id) != null) return
        val layers = style.layers
        val ids = layers.map { it.id }
        var before = stack.beforeId(ids, key)
        if (key.kind == OverlayKind.RasterLayer) {
            val label = layers.firstOrNull { it is SymbolLayer && stack.keyOf(it.id) == null }?.id
            if (label != null && (before == null || ids.indexOf(label) < ids.indexOf(before))) {
                before = label
            }
        }
        try {
            if (before != null) style.addLayerBelow(layer, before) else style.addLayer(layer)
        } catch (e: Exception) {
            if (style.getLayer(layer.id) != null) return
            try {
                style.addLayer(layer)
            } catch (e2: Exception) {
                Log.w("Mappls", "Failed to add layer: ${layer.id} (${e2.message})")
            }
        }
    }

    fun remove(
        map: MapplsMap,
        style: Style?,
        layerId: String,
    ) {
        stackOf(map).remove(layerId)
        try {
            style?.removeLayer(layerId)
        } catch (_: Exception) {
        }
    }
}
