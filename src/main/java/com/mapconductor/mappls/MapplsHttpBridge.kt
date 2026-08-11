package com.mapconductor.mappls

import com.mapconductor.core.raster.RasterHeaderRuleSet
import com.mappls.sdk.maps.LibraryLoaderProvider
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.maps.ModuleProvider
import com.mappls.sdk.maps.ModuleProviderImpl
import com.mappls.sdk.maps.http.HttpRequest
import com.mappls.sdk.maps.http.HttpResponder
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Mappls SDK の HTTP モジュールは、URL に `mappls` / `mapmyindia` を含まない
 * リクエストを「Invalid resourceUrl」として**黙って破棄する**
 * （`HttpRequestImpl.executeRequest` のホワイトリスト。実測で確認）。
 * そのままだと外部・ローカル配信のラスタタイルが一切取得されず、
 * ラスタレイヤ・GeoJSON レイヤ・ヒートマップ・タイル方式マーカーが全滅する。
 *
 * ここでは公開 API の [Mappls.setModuleProvider] で HTTP モジュールを差し替え、
 *
 * - mappls / mapmyindia 宛 → **純正実装へ委譲**（OAuth / 公開鍵の認証
 *   インターセプタを保つ。`HttpRequestUtil.setOkHttpClient` での差し替えは
 *   この認証が失われてベースマップまで 401 になるため使わないこと）
 * - それ以外 → 素の OkHttp で取得。[RasterHeaderRuleSet.shared] の
 *   `userAgent` / `extraHeaders` はここで載せる
 *
 * とルーティングする。
 */
internal object MapplsHttpBridge {
    private val stockProvider: ModuleProvider by lazy { ModuleProviderImpl() }

    @Volatile
    private var installed = false

    /** 地図生成前に一度呼ぶ。何度呼んでも安全。 */
    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            Mappls.setModuleProvider(
                object : ModuleProvider {
                    override fun createHttpRequest(): HttpRequest = RoutingHttpRequest()

                    override fun createLibraryLoaderProvider(): LibraryLoaderProvider =
                        stockProvider.createLibraryLoaderProvider()
                },
            )
            installed = true
        }
    }

    /**
     * 外部タイル用クライアント。純正（`HttpRequestImpl.DEFAULT_CLIENT`）と同じく
     * ホストあたりの同時リクエスト数だけ広げる。
     */
    private val externalClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
            .build()
    }

    private fun isMapplsUrl(url: String): Boolean = url.contains("mappls") || url.contains("mapmyindia")

    /**
     * リクエスト 1 本ぶんのルータ。ネイティブ側はリクエストごとに
     * `createHttpRequest()` を呼ぶので、状態（実行中の call）はこの単位で持つ。
     */
    private class RoutingHttpRequest : HttpRequest {
        private var delegated: HttpRequest? = null
        private var call: Call? = null

        override fun executeRequest(
            responder: HttpResponder,
            nativePtr: Long,
            resourceUrl: String?,
            etag: String?,
            modified: String?,
            extra: String?,
            offlineUsage: Boolean,
        ) {
            val url = resourceUrl ?: ""
            if (isMapplsUrl(url)) {
                val delegate = stockProvider.createHttpRequest()
                delegated = delegate
                delegate.executeRequest(responder, nativePtr, resourceUrl, etag, modified, extra, offlineUsage)
                return
            }

            // 純正実装と同じく、スキームが無ければ https を補う
            val fullUrl = if (url.startsWith("http")) url else "https://$url"
            val builder = Request.Builder().url(fullUrl)
            if (!etag.isNullOrEmpty()) {
                builder.header("If-None-Match", etag)
            } else if (!modified.isNullOrEmpty()) {
                builder.header("If-Modified-Since", modified)
            }
            RasterHeaderRuleSet.shared.headersFor(fullUrl)?.let { rule ->
                rule.userAgent?.let { builder.header("User-Agent", it) }
                rule.extraHeaders.forEach { (key, value) -> builder.header(key, value) }
            }

            val newCall = externalClient.newCall(builder.build())
            call = newCall
            newCall.enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val body =
                            try {
                                response.body?.bytes() ?: ByteArray(0)
                            } catch (e: IOException) {
                                responder.handleFailure(HttpRequest.CONNECTION_ERROR, e.message ?: "read failed")
                                return
                            } finally {
                                response.close()
                            }
                        responder.onResponse(
                            response.code,
                            response.header("ETag"),
                            response.header("Last-Modified"),
                            response.header("Cache-Control"),
                            response.header("Expires"),
                            response.header("Retry-After"),
                            response.header("x-rate-limit-reset"),
                            body,
                        )
                    }

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (call.isCanceled()) return
                        responder.handleFailure(HttpRequest.CONNECTION_ERROR, e.message ?: "request failed")
                    }
                },
            )
        }

        override fun cancelRequest() {
            delegated?.cancelRequest()
            call?.cancel()
        }
    }
}
