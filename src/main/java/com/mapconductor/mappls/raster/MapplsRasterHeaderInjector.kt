package com.mapconductor.mappls.raster

import com.mapconductor.core.raster.RasterHeaderRuleSet
import com.mapconductor.core.raster.RasterLayerState

/**
 * [RasterLayerState] の `userAgent` / `extraHeaders` の規則を [RasterHeaderRuleSet.shared] に
 * 出し入れするだけの帳簿。
 *
 * 実際にヘッダを載せるのは `MapplsHttpBridge`（外部 URL の取得経路そのもの）。
 * 他プロバイダのように `HttpRequestUtil.setOkHttpClient()` でクライアントを
 * 差し替えては**いけない** — Mappls の既定クライアントには OAuth / 公開鍵の
 * 認証インターセプタが入っており、差し替えるとベースマップのタイルまで
 * 401 になる（実測）。
 */
object MapplsRasterHeaderInjector {
    /** 登録元 1 つ分の規則を差し替える。 */
    fun apply(
        states: List<RasterLayerState>,
        owner: Any,
    ) {
        RasterHeaderRuleSet.shared.setRules(RasterHeaderRuleSet.makeRules(states), owner)
    }

    /** 登録元 1 つ分の規則を外す。 */
    fun remove(owner: Any) {
        RasterHeaderRuleSet.shared.removeRules(owner)
    }
}
