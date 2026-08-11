package com.mapconductor.mappls.zoom

import com.mapconductor.core.zoom.WebMercatorZoomAltitudeConverter

/**
 * 統一ズーム（Google Maps 基準・256px タイル）⇄ 高度の変換。
 *
 * Mappls は 512px タイルのベクタエンジンなので、統一ズームはネイティブズーム + 1。
 * 換算式はコアの [WebMercatorZoomAltitudeConverter] にある。
 */
class ZoomAltitudeConverter(
    zoom0Altitude: Double = DEFAULT_ZOOM0_ALTITUDE,
) : WebMercatorZoomAltitudeConverter(zoom0Altitude, zoomOffset = MAPPLS_TO_GOOGLE_ZOOM_OFFSET) {
    companion object {
        /**
         * 実測のオフセット:
         * GoogleZoom ≈ MapplsSDK.zoom + 1.0
         */
        const val MAPPLS_TO_GOOGLE_ZOOM_OFFSET = 1.0

        fun mapplsZoomToGoogleZoom(mapplsZoom: Double): Double =
            (mapplsZoom + MAPPLS_TO_GOOGLE_ZOOM_OFFSET).coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL)

        fun googleZoomToMapplsZoom(googleZoom: Double): Double =
            (googleZoom - MAPPLS_TO_GOOGLE_ZOOM_OFFSET).coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL)
    }
}
