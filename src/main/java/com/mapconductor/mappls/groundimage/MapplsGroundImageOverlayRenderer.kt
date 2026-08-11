package com.mapconductor.mappls.groundimage

import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.AbstractGroundImageOverlayRenderer
import com.mapconductor.core.groundimage.GroundImageEntityInterface
import com.mapconductor.core.groundimage.GroundImageFingerPrint
import com.mapconductor.core.groundimage.GroundImageState
import com.mapconductor.mappls.MapplsActualGroundImage
import com.mapconductor.mappls.MapplsMapViewHolderInterface
import com.mappls.sdk.maps.Style
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.geometry.LatLngQuad
import com.mappls.sdk.maps.style.layers.Property
import com.mappls.sdk.maps.style.layers.PropertyFactory
import com.mappls.sdk.maps.style.layers.RasterLayer
import com.mappls.sdk.maps.style.sources.ImageSource
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapplsGroundImageOverlayRenderer(
    override val holder: MapplsMapViewHolderInterface,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractGroundImageOverlayRenderer<MapplsActualGroundImage>() {
    override suspend fun createGroundImage(state: GroundImageState): MapplsActualGroundImage? =
        withContext(coroutine.coroutineContext) {
            val style = holder.map.style ?: return@withContext null
            val coordinates = state.bounds.toLatLngQuad() ?: return@withContext null
            val handle =
                MapplsGroundImageHandle(
                    sourceId = sourceId(state.id),
                    layerId = layerId(state.id),
                    applied = state.fingerPrint().toAppliedGroundImage(),
                )

            removeSourceAndLayerIfExists(style, handle)
            addSourceAndLayer(style, handle, state, coordinates)
            handle
        }

    override suspend fun updateGroundImageProperties(
        groundImage: MapplsActualGroundImage,
        current: GroundImageEntityInterface<MapplsActualGroundImage>,
        prev: GroundImageEntityInterface<MapplsActualGroundImage>,
    ): MapplsActualGroundImage? =
        withContext(coroutine.coroutineContext) {
            val style = holder.map.style ?: return@withContext groundImage
            val source = style.getSourceAs<ImageSource>(groundImage.sourceId)
            val layer = style.getLayer(groundImage.layerId) as? RasterLayer

            if (source == null || layer == null) {
                removeSourceAndLayerIfExists(style, groundImage)
                return@withContext createGroundImage(current.state)
            }

            val finger = current.fingerPrint
            val prevFinger = groundImage.applied
            val coordinates = current.state.bounds.toLatLngQuad() ?: return@withContext groundImage

            if (finger.image != prevFinger.image) {
                source.setImage(current.state.image.toBitmap())
                source.setCoordinates(coordinates)
            } else if (finger.bounds != prevFinger.bounds) {
                source.setCoordinates(coordinates)
            }

            if (finger.opacity != prevFinger.opacity) {
                updateLayerOpacity(layer, current.state.opacity)
            }

            groundImage.copy(applied = finger.toAppliedGroundImage())
        }

    override suspend fun removeGroundImage(entity: GroundImageEntityInterface<MapplsActualGroundImage>) {
        coroutine.launch {
            val style = holder.map.style ?: return@launch
            removeSourceAndLayerIfExists(style, entity.groundImage)
        }
    }

    private fun addSourceAndLayer(
        style: Style,
        handle: MapplsGroundImageHandle,
        state: GroundImageState,
        coordinates: LatLngQuad,
    ) {
        val source = ImageSource(handle.sourceId, coordinates, state.image.toBitmap())
        val layer = RasterLayer(handle.layerId, handle.sourceId)
        layer.setProperties(
            PropertyFactory.rasterOpacity(state.opacity.coerceIn(0.0f, 1.0f)),
            PropertyFactory.visibility(Property.VISIBLE),
        )

        try {
            style.addSource(source)
        } catch (e: Exception) {
            Log.w("Mappls", "Failed to add ground image source: ${e.message}")
        }

        try {
            style.addLayerBelow(layer, BELOW_LAYER_ID)
        } catch (_: Exception) {
            try {
                style.addLayer(layer)
            } catch (e: Exception) {
                Log.w("Mappls", "Failed to add ground image layer: ${e.message}")
            }
        }
    }

    private fun removeSourceAndLayerIfExists(
        style: Style,
        handle: MapplsGroundImageHandle,
    ) {
        try {
            style.removeLayer(handle.layerId)
        } catch (_: Exception) {
        }
        try {
            style.removeSource(handle.sourceId)
        } catch (_: Exception) {
        }
    }

    private fun updateLayerOpacity(
        layer: RasterLayer,
        opacity: Float,
    ) {
        layer.setProperties(PropertyFactory.rasterOpacity(opacity.coerceIn(0.0f, 1.0f)))
    }

    private fun GeoRectBounds.toLatLngQuad(): LatLngQuad? {
        val sw = southWest ?: return null
        val ne = northEast ?: return null
        return LatLngQuad(
            LatLng(ne.latitude, sw.longitude),
            LatLng(ne.latitude, ne.longitude),
            LatLng(sw.latitude, ne.longitude),
            LatLng(sw.latitude, sw.longitude),
        )
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }

        val width = intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(bounds)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bounds = oldBounds
        return bitmap
    }

    private fun sourceId(id: String): String = "mc-gimg-src-${id.toStyleIdPart()}"

    private fun layerId(id: String): String = "mc-gimg-lyr-${id.toStyleIdPart()}"

    private fun String.toStyleIdPart(): String =
        buildString(length) {
            this@toStyleIdPart.forEach { ch ->
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '-' || ch == '_' -> append(ch)
                    else -> append('_')
                }
            }
        }

    private fun GroundImageFingerPrint.toAppliedGroundImage(): AppliedGroundImage =
        AppliedGroundImage(
            bounds = bounds,
            image = image,
            opacity = opacity,
        )

    companion object {
        private const val BELOW_LAYER_ID = "polyline-layer"
    }
}
