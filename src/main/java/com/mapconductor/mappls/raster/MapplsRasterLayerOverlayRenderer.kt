package com.mapconductor.mappls.raster

import com.mapconductor.core.raster.RasterLayerEntityInterface
import com.mapconductor.core.raster.RasterLayerOverlayRendererInterface
import com.mapconductor.core.raster.RasterLayerSource
import com.mapconductor.core.raster.RasterLayerState
import com.mapconductor.core.raster.TileScheme
import com.mapconductor.mappls.MapplsMapViewHolderInterface
import com.mappls.sdk.maps.style.layers.Property
import com.mappls.sdk.maps.style.layers.PropertyFactory
import com.mappls.sdk.maps.style.layers.RasterLayer
import com.mappls.sdk.maps.style.sources.RasterSource as MapplsRasterSource
import com.mappls.sdk.maps.style.sources.TileSet
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MapplsRasterLayerOverlayRenderer(
    private val holder: MapplsMapViewHolderInterface,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : RasterLayerOverlayRendererInterface<MapplsRasterLayerHandle> {
    private val stateById: MutableMap<String, RasterLayerState> = mutableMapOf()
    private val handleById: MutableMap<String, MapplsRasterLayerHandle> = mutableMapOf()

    private fun isMarkerTileRaster(state: RasterLayerState): Boolean = state.id.startsWith(MARKER_TILE_RASTER_ID_PREFIX)

    override suspend fun onAdd(
        data: List<RasterLayerOverlayRendererInterface.AddParamsInterface>,
    ): List<MapplsRasterLayerHandle?> =
        data
            .map { params ->
                addLayer(params.state).also { handle ->
                    if (handle != null) {
                        stateById[params.state.id] = params.state.copy()
                        handleById[params.state.id] = handle
                    }
                }
            }.also {
                holder.map.style?.let { style -> rebuildNonMarkerRasterLayers(style) }
            }

    override suspend fun onChange(
        data: List<RasterLayerOverlayRendererInterface.ChangeParamsInterface<MapplsRasterLayerHandle>>,
    ): List<MapplsRasterLayerHandle?> =
        data
            .map { params ->
                val prev = params.prev
                val next = params.current.state
                val handle =
                    if (prev.state.source != next.source) {
                        removeLayer(prev)
                        addLayer(next)
                    } else {
                        updateLayer(prev.layer, next)
                        prev.layer
                    }
                if (handle != null) {
                    stateById[next.id] = next.copy()
                    handleById[next.id] = handle
                }
                handle
            }.also {
                holder.map.style?.let { style -> rebuildNonMarkerRasterLayers(style) }
            }

    override suspend fun onRemove(data: List<RasterLayerEntityInterface<MapplsRasterLayerHandle>>) {
        data.forEach { entity ->
            stateById.remove(entity.state.id)
            handleById.remove(entity.state.id)
            removeLayer(entity)
        }
        holder.map.style?.let { style -> rebuildNonMarkerRasterLayers(style) }
    }

    override suspend fun onPostProcess() {}

    private fun addLayer(state: RasterLayerState): MapplsRasterLayerHandle? {
        val sourceId = "raster-source-${state.id}"
        val layerId = "raster-layer-${state.id}"
        val source = buildSource(sourceId, state.source) ?: return null
        val handle = MapplsRasterLayerHandle(sourceId = sourceId, layerId = layerId)
        val style = holder.map.style ?: return handle
        val layer = RasterLayer(layerId, sourceId)
        val opacity = state.opacity.coerceIn(0.0f, 1.0f)
        layer.setProperties(
            PropertyFactory.rasterOpacity(opacity),
            PropertyFactory.visibility(
                if (state.visible) Property.VISIBLE else Property.NONE,
            ),
        )
        try {
            style.addSource(source)
        } catch (e: Exception) {
            Log.w("Mappls", "Failed to add raster source: ${e.message}")
        }
        try {
            if (isMarkerTileRaster(state)) {
                addLayerForMarkerTile(style, layer)
            } else {
                style.addLayer(layer)
            }
        } catch (e: Exception) {
            Log.w("Mappls", "Failed to add raster layer: ${e.message}")
        }
        return handle
    }

    private fun addLayerForMarkerTile(
        style: com.mappls.sdk.maps.Style,
        layer: RasterLayer,
    ) {
        // Insert the raster tiles below the marker symbol layer so they don't cover markers,
        // but remain above vector overlays that are anchored below markers (polyline/circle/etc).
        try {
            style.addLayerBelow(layer, MARKERS_LAYER_ID)
            return
        } catch (_: Exception) {
        }

        // Best-effort fallback: place above polylines if marker layer isn't present yet.
        try {
            style.addLayerAbove(layer, POLYLINE_LAYER_ID)
            return
        } catch (_: Exception) {
        }

        style.addLayer(layer)
    }

    private fun updateLayer(
        handle: MapplsRasterLayerHandle,
        state: RasterLayerState,
    ) {
        val style = holder.map.style ?: return
        val layer = style.getLayer(handle.layerId) as? RasterLayer ?: return
        val opacity = state.opacity.coerceIn(0.0f, 1.0f)
        layer.setProperties(
            PropertyFactory.rasterOpacity(opacity),
            PropertyFactory.visibility(
                if (state.visible) Property.VISIBLE else Property.NONE,
            ),
        )
    }

    private fun removeLayer(entity: RasterLayerEntityInterface<MapplsRasterLayerHandle>) {
        val style = holder.map.style ?: return
        val handle = entity.layer
        try {
            style.removeLayer(handle.layerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(handle.sourceId)
        } catch (_: Exception) {
        }
    }

    private fun rebuildNonMarkerRasterLayers(style: com.mappls.sdk.maps.Style) {
        val ordered =
            stateById.values
                .asSequence()
                .filter { !isMarkerTileRaster(it) }
                .sortedBy { it.zIndex }
                .mapNotNull { state -> handleById[state.id]?.let { handle -> state to handle } }
                .toList()

        ordered.forEach { (_, handle) ->
            try {
                style.removeLayer(handle.layerId)
            } catch (_: Exception) {
            }
        }

        ordered.forEach { (state, handle) ->
            val layer = RasterLayer(handle.layerId, handle.sourceId)
            val opacity = state.opacity.coerceIn(0.0f, 1.0f)
            layer.setProperties(
                PropertyFactory.rasterOpacity(opacity),
                PropertyFactory.visibility(
                    if (state.visible) Property.VISIBLE else Property.NONE,
                ),
            )
            try {
                style.addLayer(layer)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildSource(
        sourceId: String,
        source: RasterLayerSource,
    ): MapplsRasterSource? =
        when (source) {
            is RasterLayerSource.UrlTemplate -> {
                val tileSet =
                    TileSet("2.2.0", source.template).apply {
                        source.minZoom?.let { setMinZoom(it.toFloat()) }
                        source.maxZoom?.let { setMaxZoom(it.toFloat()) }
                        scheme = if (source.scheme == TileScheme.TMS) "tms" else "xyz"
                    }
                MapplsRasterSource(sourceId, tileSet, source.tileSize)
            }
            is RasterLayerSource.TileJson -> MapplsRasterSource(sourceId, source.url)
            is RasterLayerSource.ArcGisService -> {
                val base = source.serviceUrl.trimEnd('/')
                val tileSet =
                    TileSet("2.2.0", "$base/tile/{z}/{y}/{x}").apply {
                        scheme = "xyz"
                    }
                MapplsRasterSource(sourceId, tileSet, RasterLayerSource.DEFAULT_TILE_SIZE)
            }
        }

    private companion object {
        private const val MARKER_TILE_RASTER_ID_PREFIX = "marker-tile-"
        private const val MARKERS_LAYER_ID = "markers-layer"
        private const val POLYLINE_LAYER_ID = "polyline-layer"
    }
}

data class MapplsRasterLayerHandle(
    val sourceId: String,
    val layerId: String,
)
