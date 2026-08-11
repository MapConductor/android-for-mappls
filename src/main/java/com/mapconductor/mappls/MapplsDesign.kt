package com.mapconductor.mappls

import com.mapconductor.core.map.AttributionRule
import com.mapconductor.core.map.MapDesignTypeInterface

/**
 * Mappls のスタイルは URL ではなく**スタイル名**で切り替える
 * （`MapplsMap.setMapplsStyle(name)`）。使えるスタイル名はアカウントに
 * 紐づいていて、`MapplsMap.getMapplsAvailableStyles()` で実行時に取れる。
 * どのアカウントにも既定スタイルが 1 つ設定されている。
 */
interface MapplsMapDesignTypeInterface : MapDesignTypeInterface<String> {
    /** `setMapplsStyle` に渡すスタイル名。空文字は「アカウントの既定スタイル」。 */
    val styleName: String
}

data class MapplsDesign(
    override val id: String,
    override val styleName: String,
    override val attributionRules: List<AttributionRule> = emptyList(),
) : MapplsMapDesignTypeInterface {
    override fun getValue(): String = "mapDesign_id=$id,style=$styleName"

    companion object {
        /** アカウントの既定スタイル（`setMapplsStyle` を呼ばずに SDK に任せる）。 */
        val Default =
            MapplsDesign(
                id = "default",
                styleName = "",
            )

        /**
         * 標準（昼）。どのアカウントにも入っている基本スタイル。
         *
         * これ以外のスタイルは**アカウント紐付き**で、コンソールで割り当てた名前を
         * `MapplsDesign(id, styleName)` で指定する（存在しない名前は
         * `setMapplsStyle` が「style not found」で弾く。実行時の一覧は
         * `MapplsMap.getMapplsAvailableStyles()`）。
         */
        val StandardDay =
            MapplsDesign(
                id = "standard_day",
                styleName = "standard_day",
            )
    }
}
