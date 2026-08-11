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
        val StandardDay =
            MapplsDesign(
                id = "standard-day",
                styleName = "standard-day",
            )
        val StandardNight =
            MapplsDesign(
                id = "standard-night",
                styleName = "standard-night",
            )
        val GreyDay =
            MapplsDesign(
                id = "grey-day",
                styleName = "grey-day",
            )
    }
}
