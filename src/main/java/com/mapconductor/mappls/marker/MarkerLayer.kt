package com.mapconductor.mappls.marker

import com.mapconductor.core.marker.MarkerEntityInterface
import com.mapconductor.mappls.MapplsActualMarker
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.FeatureCollection
import com.mappls.sdk.maps.style.expressions.Expression.get
import com.mappls.sdk.maps.style.layers.PropertyFactory
import com.mappls.sdk.maps.style.layers.PropertyFactory.iconAllowOverlap
import com.mappls.sdk.maps.style.layers.PropertyFactory.iconAnchor
import com.mappls.sdk.maps.style.layers.PropertyFactory.iconIgnorePlacement
import com.mappls.sdk.maps.style.layers.PropertyFactory.iconImage
import com.mappls.sdk.maps.style.layers.PropertyFactory.iconOffset
import com.mappls.sdk.maps.style.layers.PropertyFactory.iconTranslateAnchor
import com.mappls.sdk.maps.style.layers.SymbolLayer
import com.mappls.sdk.maps.style.sources.GeoJsonSource

open class MarkerLayer(
    open val sourceId: String,
    open val layerId: String,
) {
    val layer =
        SymbolLayer(layerId, sourceId).apply {
            setProperties(
                iconImage(get(MapplsMarkerOverlayRenderer.Prop.ICON_ID)),
                // iconSize(get(MapplsMarkerOverlayRenderer.Prop.SCALE)),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                PropertyFactory.symbolSortKey(get(MapplsMarkerOverlayRenderer.Prop.Z_INDEX)),
                iconAnchor(MapplsMarkerOverlayRenderer.IconAnchor.TOP_LEFT),
                iconTranslateAnchor(MapplsMarkerOverlayRenderer.IconTranslateAnchor.MAP),
                // Each feature always carries icon-offset in properties; use it directly
                iconOffset(get(MapplsMarkerOverlayRenderer.Prop.ICON_ANCHOR)),
            )
        }

    val source: GeoJsonSource =
        GeoJsonSource(
            sourceId,
            FeatureCollection.fromFeatures(emptyList<MapplsActualMarker>()),
        )

    // GeoJsonSource() starts out empty, so the first draw() call has nothing to clear.
    // @Volatile because callers on a different thread (e.g. MapplsMarkerOverlayRenderer.
    // onPostProcess() on its ingest thread) need to read this without hopping onto the
    // thread that writes it, to decide whether that hop is even necessary in the first place.
    @Volatile
    private var lastDrawnEmpty = true

    // Lets a caller on any thread check, before paying for a dispatcher hop onto the thread
    // that owns the style, whether draw() would actually have anything to do for this set of
    // entities. Mirrors the emptiness check draw() itself performs.
    fun wouldSkipDraw(entities: List<MarkerEntityInterface<Feature>>): Boolean =
        lastDrawnEmpty && entities.none { it.visible && it.marker != null }

    fun draw(
        entities: List<MarkerEntityInterface<Feature>>,
        style: com.mappls.sdk.maps.Style,
    ) {
        val visibleEntities = entities.filter { it.visible && it.marker != null }
        val features = visibleEntities.mapNotNull { it.marker }

        // setGeoJson() always forces Mappls GL Native to re-tile and invalidate the source's
        // render pass, even when the data is identical to what's already there. When tiling is
        // active, onPostProcess() calls draw() with an empty list on every ingest regardless of
        // whether anything actually changed, so an empty-to-empty call here is pure waste -
        // worst of all, it lands right after a large marker ingest, when the heap is already
        // under GC pressure from that ingest's allocations.
        if (features.isEmpty() && lastDrawnEmpty) return

        val collection = FeatureCollection.fromFeatures(features)

        try {
            // Always update the source attached to the current style
            var styleSource = style.getSourceAs<GeoJsonSource>(sourceId)
            if (styleSource == null) {
                // Source might not be attached yet (e.g., after style reload). Try to attach ours.
                try {
                    style.addSource(source)
                } catch (_: Exception) {
                    // ignore if already added or style busy
                }
                styleSource = style.getSourceAs(sourceId)
            }
            styleSource?.setGeoJson(collection)
            lastDrawnEmpty = features.isEmpty()
        } catch (e: Exception) {
            android.util.Log.w("Mappls", "Failed to update marker source: ${e.message}")
        }
    }
}
