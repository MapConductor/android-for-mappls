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
         * これ以外のスタイルは**契約に紐づく**。コンソールで割り当てた名前を
         * `MapplsDesign(id, styleName)` で指定する（契約に無い名前は
         * `setMapplsStyle` が「style not found」で弾く。実行時の一覧は
         * `MapplsMap.getMapplsAvailableStyles()`）。
         */
        val StandardDay =
            MapplsDesign(
                id = "standard_day",
                styleName = "standard_day",
            )

        /**
         * 標準（夜）。**追加料金の有料オプション**。
         *
         * 契約に含まれていないアカウントでは `setMapplsStyle` が弾くため、
         * このリポジトリのサンプルでは選択肢に出していない（サンプルは追加料金を払っていない）。
         * ライブラリとしては、契約済みのアプリがそのまま使えるよう公開しておく。
         */
        val StandardNight =
            MapplsDesign(
                id = "standard_night",
                styleName = "standard_night",
            )

        /** グレー（昼）。**追加料金の有料オプション**。[StandardNight] と同じ扱い。 */
        val GreyDay =
            MapplsDesign(
                id = "grey_day",
                styleName = "grey_day",
            )
    }
}
