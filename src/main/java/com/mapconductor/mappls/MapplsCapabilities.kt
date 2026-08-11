package com.mapconductor.mappls

import com.mapconductor.core.map.MapCapability
import com.mapconductor.core.map.MapCapabilityStatus
import com.mapconductor.core.map.MutableMapServiceRegistry

/**
 * このドライバーで何ができるかの宣言。
 *
 * Mappls は Mapbox GL Android v9 系のリネームフォークなので、機能面は
 * android-for-maplibre と同じくフルセットが動く。宣言だけ特記事項:
 *
 * - スタイルは URL ではなく**アカウント紐付きのスタイル名**
 *   （[MapplsDesign] のコメント参照）。機能としては Supported。
 * - GestureRotate は SDK の UiSettings がそのまま提供する。
 */
object MapplsCapabilities {
    fun declare(registry: MutableMapServiceRegistry) {
        registry.declare(MapCapability.ScreenProjectionSync, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.PolygonHoles, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.ClickPassthrough, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.MarkerDrag, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.CameraTilt, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.CameraRotate, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.CameraRestriction, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.GestureScroll, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.GestureZoom, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.GestureRotate, MapCapabilityStatus.Supported)
        registry.declare(MapCapability.GestureTilt, MapCapabilityStatus.Supported)
    }
}
