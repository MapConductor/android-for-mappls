package com.mapconductor.mappls

import com.mapconductor.core.conformance.MapDriverConformance
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.mappls.zoom.ZoomAltitudeConverter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ドライバーの適合テスト。
 *
 * Mappls の `MapView` / `MapplsMap` は JNI を要求するので、素の JVM で
 * 確かめられるのは「地図SDKに触らない純粋な計算」だけ:
 *
 *  - ズームの往復換算（512px タイルなのでオフセット +1.0）
 *  - カスケードの探索順（コアの定数）
 *  - capability の宣言に理由がついているか
 *
 * **描画・タップ・ドラッグ・InfoBubble の追従・外部タイル
 * （[MapplsHttpBridge] のホワイトリスト回避）は実機で確かめること。**
 */
class MapplsDriverConformanceTest {
    @Test
    fun `ズームが往復する`() {
        MapDriverConformance.checkZoomConverter(ZoomAltitudeConverter())
    }

    @Test
    fun `統一ズームはネイティブズーム+1`() {
        assertEquals(13.0, ZoomAltitudeConverter.mapplsZoomToGoogleZoom(12.0), 1e-9)
        assertEquals(12.0, ZoomAltitudeConverter.googleZoomToMapplsZoom(13.0), 1e-9)
    }

    @Test
    fun `カスケードの探索順はコアの定数`() {
        MapDriverConformance.checkCascadeOrder()
    }

    @Test
    fun `capability の宣言が適合する`() {
        val registry = MutableMapServiceRegistry()
        MapplsCapabilities.declare(registry)
        MapDriverConformance.checkCapabilityDeclarations(registry)
    }
}
