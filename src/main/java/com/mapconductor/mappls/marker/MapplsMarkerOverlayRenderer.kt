package com.mapconductor.mappls.marker

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mapconductor.core.ResourceProvider
import com.mapconductor.core.calculateZIndex
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.marker.AbstractMarkerOverlayRenderer
import com.mapconductor.core.marker.BitmapIcon
import com.mapconductor.core.marker.DefaultMarkerIcon
import com.mapconductor.core.marker.MarkerEntityInterface
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.core.marker.MarkerManager
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.mappls.MapplsActualMarker
import com.mapconductor.mappls.MapplsMapViewHolderInterface
import com.mapconductor.mappls.toPoint
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.FeatureCollection
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class MapplsMarkerOverlayRenderer(
    holder: MapplsMapViewHolderInterface,
    val markerManager: MarkerManager<MapplsActualMarker>,
    val markerLayer: MarkerLayer,
    val dragLayer: MarkerDragLayer,
    coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractMarkerOverlayRenderer<MapplsMapViewHolderInterface, MapplsActualMarker>(
        holder = holder,
        coroutine = coroutine,
    ) {
    override val supportsAnimationOverlay: Boolean = true

    private val iconRefCounter: MutableMap<String, Int> = mutableMapOf()
    private val pendingStyleImageRemovals: MutableMap<String, Long> = mutableMapOf()
    private val iconBitmapCache: MutableMap<String, Bitmap> = mutableMapOf()
    private val defaultMarkerIcon: BitmapIcon = DefaultMarkerIcon().toBitmapIcon()

    object Prop {
        const val ICON_ID = "icon_id"
        const val DEFAULT_MARKER_ID = "default"
        const val SCALE = "scale"
        const val ICON_ANCHOR = "icon-offset"
        const val Z_INDEX = "zIndex"
    }

    object IconAnchor {
        const val CENTER = "center"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val BOTTOM = "bottom"
        const val TOP_LEFT = "top-left"
        const val TOP_RIGHT = "top-right"
        const val BOTTOM_LEFT = "bottom-left"
        const val BOTTOM_RIGHT = "bottom-right"
    }

    object IconTranslateAnchor {
        const val MAP = "map"
        const val VIEWPORT = "viewport"
    }

    init {
        val style = holder.map.style
        if (style != null) {
            style.addImage(Prop.DEFAULT_MARKER_ID, defaultMarkerIcon.bitmap)
        } else {
            holder.map.getStyle { style ->
                style.addImage(Prop.DEFAULT_MARKER_ID, defaultMarkerIcon.bitmap)
            }
        }
    }

    // Ensure marker images exist on the given style (used after style reload).
    fun ensureDefaultIcon(style: com.mappls.sdk.maps.Style) {
        ensureStyleImages(style)
    }

    fun ensureStyleImages(style: com.mappls.sdk.maps.Style) {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            style.addImage(Prop.DEFAULT_MARKER_ID, defaultMarkerIcon.bitmap)
        } catch (_: Exception) {
        }

        val entities = markerManager.allEntities()
        val styleIcons = linkedMapOf<String, Bitmap>()
        var nativeMarkerCount = 0
        entities.forEach { entity ->
            // Tiled markers have no Mappls Feature and are drawn by MarkerTileRenderer.
            // Registering their images with the style is both unnecessary and extremely
            // expensive for large data sets (the same bitmap may otherwise be added tens of
            // thousands of times during a style reload).
            if (entity.marker == null) return@forEach
            nativeMarkerCount++
            val icon = entity.state.icon ?: return@forEach
            val iconKey = icon.hashCode().toString()
            if (!styleIcons.containsKey(iconKey)) {
                styleIcons[iconKey] = iconBitmapCache[iconKey] ?: icon.toBitmapIcon().bitmap
            }
        }

        styleIcons.forEach { (iconKey, bitmap) ->
            try {
                style.addImage(iconKey, bitmap, false)
            } catch (_: Exception) {
            }
            iconBitmapCache[iconKey] = bitmap
        }
    }

    private fun decrementIconRef(iconKey: String) {
        if (iconKey == Prop.DEFAULT_MARKER_ID) return
        val next = (iconRefCounter[iconKey] ?: 0) - 1
        if (next <= 0) {
            iconRefCounter.remove(iconKey)
            // Mappls can render one frame behind GeoJSON source updates during marker animation.
            // Keep images briefly after last use so icon-image references never point at a missing image.
            pendingStyleImageRemovals[iconKey] = System.currentTimeMillis() + STYLE_IMAGE_REMOVAL_GRACE_MS
        } else {
            iconRefCounter[iconKey] = next
        }
    }

    private fun incrementIconRef(iconKey: String) {
        if (iconKey == Prop.DEFAULT_MARKER_ID) return
        pendingStyleImageRemovals.remove(iconKey)
        iconRefCounter[iconKey] = (iconRefCounter[iconKey] ?: 0) + 1
    }

    override fun setMarkerVisible(
        markerEntity: MarkerEntityInterface<MapplsActualMarker>,
        visible: Boolean,
    ) {
        markerEntity.visible = visible
        redraw()
    }

    override fun setMarkerPosition(
        markerEntity: MarkerEntityInterface<MapplsActualMarker>,
        position: GeoPoint,
    ) {
        val entities = markerManager.allEntities()
        val props = (markerEntity.marker?.properties() ?: JsonObject()).deepCopy()
        val previousZIndex =
            props
                .get(Prop.Z_INDEX)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt
        props.addProperty(
            Prop.Z_INDEX,
            markerEntity.state.zIndex
                ?: previousZIndex
                ?: calculateZIndex(position),
        )
        val feature =
            Feature.fromGeometry(
                position.toPoint(),
                props,
                "marker-${markerEntity.state.id}",
            )
        markerEntity.marker = feature
        val features =
            entities.map {
                if (it.state.id == markerEntity.state.id) {
                    feature
                } else {
                    it.marker
                }
            }
        // Execute directly instead of launching a new coroutine
        holder.map.style?.let { style ->
            val styleSource = style.getSourceAs<com.mappls.sdk.maps.style.sources.GeoJsonSource>(markerLayer.sourceId)
            styleSource?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    override suspend fun onAdd(
        data: List<MarkerOverlayRendererInterface.AddParamsInterface>,
    ): List<MapplsActualMarker?> =
        withContext(Dispatchers.Main) {
            val style = holder.map.style ?: return@withContext emptyList()

            data.forEach {
                it.state.icon?.let { icon ->
                    val iconKey = icon.hashCode().toString()
                    try {
                        style.addImage(iconKey, it.bitmapIcon.bitmap, false)
                    } catch (_: Exception) {
                    }
                    iconBitmapCache[iconKey] = it.bitmapIcon.bitmap
                    if (!iconRefCounter.contains(iconKey)) iconRefCounter[iconKey] = 0
                }
            }

            data.map {
                val featureId = "marker-${it.state.id}"
                val position = GeoPoint.from(it.state.position).toPoint()
                val properties =
                    JsonObject().apply {
                        if (it.state.icon != null) {
                            it.state.icon?.let { icon ->
                                val iconKey = icon.hashCode().toString()
                                incrementIconRef(iconKey)
                                addProperty(Prop.ICON_ID, iconKey)
                                // icon offset property
                                add(Prop.ICON_ANCHOR, createIconOffset(icon))
                            }
                        } else {
                            addProperty(Prop.ICON_ID, Prop.DEFAULT_MARKER_ID)
                            add(Prop.ICON_ANCHOR, getDefaultIconOffsetProperty())
                        }
                        // We don't use the Mappls SDK's scaling system
                        // addProperty(Prop.SCALE, 1.0)
                        addProperty(Prop.Z_INDEX, it.state.zIndex ?: calculateZIndex(it.state.position))
                    }
                Feature.fromGeometry(position, properties, featureId)
            }
        }

    private fun getDefaultIconOffsetProperty(): JsonArray = createIconOffset(defaultMarkerIcon)

    private fun createIconOffset(icon: BitmapIcon): JsonArray =
        JsonArray().apply {
            add(-(icon.size.width * icon.anchor.x) / ResourceProvider.getDensity())
            add(-(icon.size.height * icon.anchor.y) / ResourceProvider.getDensity())
        }

    private fun createIconOffset(icon: MarkerIconInterface): JsonArray = createIconOffset(icon.toBitmapIcon())

    override suspend fun onRemove(data: List<MarkerEntityInterface<MapplsActualMarker>>) {
        data.forEach { entity ->
            val iconKey =
                entity.marker
                    ?.properties()
                    ?.get(Prop.ICON_ID)
                    ?.asString
                    ?: entity.state.icon
                        ?.hashCode()
                        ?.toString()
            if (iconKey != null) {
                decrementIconRef(iconKey)
            }
        }
    }

    fun drawDragLayer() {
        holder.map.style?.let {
            coroutine.launch {
                dragLayer.draw(it)
            }
        }
    }

    fun redraw() {
        val entities = markerManager.allEntities()
        // Get style from controller to use the same instance
        holder.map.style?.let {
            coroutine.launch {
                markerLayer.draw(entities, it)
            }
        }
    }

    override suspend fun onPostProcess() {
        val target = markerManager.allEntities().filter { !it.tiling }

        // withContext(Dispatchers.Main) below is a real cross-thread hop when onPostProcess()
        // is invoked from a background ingest dispatcher (as the React Native wrapper does for
        // large marker batches) rather than already running on Main (as the native Compose
        // sample does). That hop, immediately following a large tiled-marker ingest, lines up
        // with severe GC stalls on-device even though the Main-side work below is a few ms at
        // most - so skip the hop entirely when there's nothing for it to do.
        if (markerLayer.wouldSkipDraw(target) && pendingStyleImageRemovals.isEmpty()) {
            return
        }

        return withContext(Dispatchers.Main) {
            val style = holder.map.style ?: return@withContext
            markerLayer.draw(target, style)
            yield()

            val now = System.currentTimeMillis()
            val expired =
                pendingStyleImageRemovals
                    .asSequence()
                    .filter { (_, deadline) -> deadline <= now }
                    .map { (key, _) -> key }
                    .toList()

            expired.forEach { iconKey ->
                if (iconRefCounter.containsKey(iconKey)) {
                    pendingStyleImageRemovals.remove(iconKey)
                    return@forEach
                }
                try {
                    style.removeImage(iconKey)
                } catch (_: Exception) {
                } finally {
                    pendingStyleImageRemovals.remove(iconKey)
                    iconBitmapCache.remove(iconKey)
                }
            }
        }
    }

    override suspend fun onChange(
        data: List<MarkerOverlayRendererInterface.ChangeParamsInterface<MapplsActualMarker>>,
    ): List<MapplsActualMarker?> =
        withContext(Dispatchers.Main) {
            val style = holder.map.style ?: return@withContext emptyList()

            data.map { params ->
                val prevFinger = params.prev.fingerPrint
                val currFinger = params.current.fingerPrint
                val prevProperties = params.prev.marker?.properties()

                val properties =
                    JsonObject().apply {
                        // No additional scaling needed - bitmap is created with device density
                        // and Bitmap.density is set to prevent Mappls's automatic scaling
                        // addProperty(Prop.SCALE, 1.0)
                        if (currFinger.icon == prevFinger.icon) {
                            addProperty(
                                Prop.ICON_ID,
                                prevProperties?.get(Prop.ICON_ID)?.asString ?: Prop.DEFAULT_MARKER_ID,
                            )

                            add(
                                Prop.ICON_ANCHOR,
                                prevProperties?.get(Prop.ICON_ANCHOR) ?: getDefaultIconOffsetProperty(),
                            )
                        } else {
                            val prevIconKey =
                                prevProperties
                                    ?.get(Prop.ICON_ID)
                                    ?.asString
                                    ?: params.prev.state.icon
                                        ?.hashCode()
                                        ?.toString()
                                    ?: Prop.DEFAULT_MARKER_ID
                            decrementIconRef(prevIconKey)

                            if (currFinger.icon == null) {
                                addProperty(Prop.ICON_ID, Prop.DEFAULT_MARKER_ID)
                                add(Prop.ICON_ANCHOR, getDefaultIconOffsetProperty())
                            } else {
                                params.current.state.icon?.let { icon ->
                                    // icon id
                                    val iconKey = icon.hashCode().toString()
                                    style.addImage(iconKey, params.bitmapIcon.bitmap, false)
                                    iconBitmapCache[iconKey] = params.bitmapIcon.bitmap
                                    if (!iconRefCounter.contains(iconKey)) iconRefCounter[iconKey] = 0
                                    incrementIconRef(iconKey)
                                    addProperty(Prop.ICON_ID, iconKey)
                                    add(Prop.ICON_ANCHOR, createIconOffset(icon))
                                }
                            }
                        }
                        addProperty(
                            Prop.Z_INDEX,
                            resolveZIndexForChange(
                                current = params.current,
                                prev = params.prev,
                                prevProperties = prevProperties,
                            ),
                        )
                    }

                val position =
                    GeoPoint.from(params.current.state.position).toPoint()
                val featureId = "marker-${params.current.state.id}"
                Feature.fromGeometry(position, properties, featureId)
            }
        }

    private fun resolveZIndexForChange(
        current: MarkerEntityInterface<MapplsActualMarker>,
        prev: MarkerEntityInterface<MapplsActualMarker>,
        prevProperties: JsonObject?,
    ): Int {
        current.state.zIndex?.let { return it }

        if (current.fingerPrint.zIndex == prev.fingerPrint.zIndex) {
            prevProperties
                ?.get(Prop.Z_INDEX)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt
                ?.let { return it }
        }

        return calculateZIndex(current.state.position)
    }

    /**
     * ドラッグ対象の入れ替え。
     *
     * ドラッグ中のマーカーは通常のレイヤ（[markerLayer]）から外して専用の
     * [dragLayer] へ移す。そうしないと GeoJSON ソースを丸ごと描き直すたびに
     * 指の位置と食い違う。以前は各プロバイダのマーカーコントローラと
     * ストラテジ用イベントコントローラに同じものが 2 本ずつあった。
     */
    override fun onDragSelectionChanged(
        previous: MarkerEntityInterface<MapplsActualMarker>?,
        current: MarkerEntityInterface<MapplsActualMarker>?,
    ) {
        if (current == null) {
            previous?.let {
                dragLayer.updatePosition(GeoPoint.from(it.state.position))
                dragLayer.selected = null
                drawDragLayer()
                markerManager.registerEntity(it)
                redraw()
            }
            return
        }
        markerManager.removeEntity(current.state.id)
        dragLayer.selected = current
        dragLayer.updatePosition(GeoPoint.from(current.state.position))
        redraw()
        drawDragLayer()
    }

    override fun onDragPositionChanged(position: GeoPointInterface) {
        dragLayer.updatePosition(GeoPoint.from(position))
        drawDragLayer()
    }
}

private const val STYLE_IMAGE_REMOVAL_GRACE_MS: Long = 1500L
