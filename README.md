# Mappls SDK for MapConductor Android

## Description

MapConductor provides a unified API for Android Jetpack Compose.
You can use Mappls with Compose, but you can also switch to other Maps SDKs (such as
MapLibre, Mapbox, and so on), anytime.
Even using the wrapper API, you can still access the native Mappls map if you want.

`MapplsMapView` supports the same MapConductor content types as the iOS `ios-for-mappls`
provider: Marker, Polyline, Polygon, Circle, GroundImage, RasterLayer and InfoBubble.

## Setup

https://mapconductor.com/setup/

### API key

MapConductor does not read Mappls credentials from the manifest. Initialise the
Mappls SDK yourself — with its own API, from `Application.onCreate()` — before the
first map is created. Credentials come from the Mappls (MapmyIndia) developer
console. Keep them out of source control.

Mappls credentials come from the Mappls (MapmyIndia) developer console. Configure them the
way the Mappls SDK expects before the first map is created.

## Usage

```kotlin
@Composable
fun MapExample() {
    val mapViewState = rememberMapplsMapViewState(
        mapDesign = MapplsDesign.Default,
        cameraPosition = MapCameraPosition(
            position = GeoPoint(latitude = 28.6139, longitude = 77.2090),
            zoom = 11.0,
        ),
    )

    MapplsMapView(state = mapViewState, modifier = Modifier.fillMaxSize()) {
        Marker(
            MarkerState(
                position = GeoPoint(latitude = 28.6139, longitude = 77.2090),
                icon = DefaultMarkerIcon().copy(label = "Delhi"),
            ),
        )
    }
}
```

## Available designs

`MapplsDesign` exposes `Default`, `StandardDay`, `StandardNight` and `GreyDay`.
Arbitrary styles can be given with `MapplsDesign(id, styleName)`.

> **Styles are contract-bound.** `StandardNight` and `GreyDay` are paid options on the
> Mappls side. If your account does not include them the SDK rejects the style name and
> falls back — this is an account issue, not a bug in this module.

## Supported overlays

Marker (including clustering and tile-rendered large marker sets), Polyline, Polygon
(holes supported), Circle, GroundImage, RasterLayer and InfoBubble — the same unified API
as the other providers.

## Components

### MapplsMapView [[docs]](https://mapconductor.com/mapview/)

```kotlin
@Composable
fun MapExample() {
    val initCameraPosition = MapCameraPosition(
        position = GeoPoint(
            latitude = 34.091,
            longitude = -117.886,
        ),
        zoom = 9.0,
        tilt = 60.0,
        bearing = 30.0,
    )

    val mapViewState = rememberMapplsMapViewState(
        cameraPosition = initCameraPosition,
    )

    MapplsMapView(mapViewState)
}
```

------------------------------------------------------------------------

### Marker [[docs]](https://mapconductor.com/markers/)

```kotlin
@Composable
fun MarkerExample() {
    val markerState = remember { MarkerState(
        position = GeoPoint(...),
        icon = DefaultMarkerIcon().copy(
            label = "Mappls",
        ),
        onClick = {
            it.animate(MarkerAnimation.Bounce)
        },
    ) }

    MapplsMapView(...) {
        Marker(markerState)
    }
}
```

------------------------------------------------------------------------

### InfoBubble [[docs]](https://mapconductor.com/info-bubble/)

```kotlin
@Composable
fun InfoBubbleExample() {
    var selectedMarker by remember { mutableStateOf<MarkerState?>(null) }

    val markerState = remember { MarkerState(
        ...,
        onClick = {
            selectedMarker = it
        },
    ) }

    MapplsMapView(...) {
        Marker(markerState)
        selectedMarker?.let {
            InfoBubble(
                marker = it,
            ) {
                Text("Hello, world!")
            }
        }
    }
}
```

------------------------------------------------------------------------

### Circle [[docs]](https://mapconductor.com/circle/)

```kotlin
@Composable
fun CircleExample() {

    val circleState = remember { CircleState(
        center = GeoPoint(...),
        radiusMeters = 50.0,
        fillColor = Color.Blue.copy(alpha = 0.5f),
        onClick = {
            it.state.fillColor = Color.Red.copy(alpha = 0.5f)
        }
    ) }

    MapplsMapView(...) {
        Circle(circleState)
    }
}
```

------------------------------------------------------------------------

### Polyline [[docs]](https://mapconductor.com/polyline/)

```kotlin
@Composable
fun PolylineExample() {

    val polylineState = remember { PolylineState(
            points = airports,
            strokeColor = Color.Blue.copy(alpha = 0.5f),
            strokeWidth = 4.dp,
            geodesic = true,
        ) }

    MapplsMapView(...) {
        Polyline(polylineState)
    }
}
```

------------------------------------------------------------------------

### Polygon [[docs]](https://mapconductor.com/polygon/)

```kotlin
@Composable
fun PolygonExample() {

    val polygonState = remember { PolygonState(
        points = goryokaku,
        strokeColor = Color.Blue.copy(alpha = 0.5f),
        fillColor =  Color.Red.copy(alpha = 0.7f),
    ) }

    MapplsMapView(...) {
        Polygon(polygonState)
    }
}
```

------------------------------------------------------------------------

### Polygon Hole

```kotlin
@Composable
fun PolygonHoleExample() {

    val polygonState =
        remember {
            PolygonState(
                points = listOf(...),
                holes = listOf(
                            listOf(...),
                            listOf(...),
                        ),
                fillColor = Color(0xCC787880),
                strokeColor = Color.Red,
                strokeWidth = 2.dp,
            )
        }

    MapplsMapView(...) {
        Polygon(polygonState)
    }
}
```

------------------------------------------------------------------------

### GroundImage [[docs]](https://mapconductor.com/ground-image/)

```kotlin
@Composable
fun GroundImageExample() {
    val groundImageState = remember { GroundImageState(
        bounds = GeoRectBounds(
            southWest = GeoPoint.fromLatLong(...),
            northEast = GeoPoint.fromLatLong(...),
        ),
        image = image,
        opacity = 0.5f,
    ) }

    MapplsMapView(state = mapViewState) {
        GroundImage(groundImageState)
    }
}
```

## Implementation notes / known limitations

- **HTTP module replacement**: the Mappls SDK's HTTP module **silently drops** any request
  whose URL does not contain `mappls` or `mapmyindia` (a whitelist in
  `HttpRequestImpl.executeRequest`, confirmed by measurement). Left as is, no externally or
  locally served raster tile is ever fetched. `MapplsHttpBridge` installs a replacement via
  `Mappls.setModuleProvider` so RasterLayer, Heatmap and GeoJSON layers work.
  The iOS module solves the same problem differently (an `mcmappls=1` query parameter).
- **Zoom calibration**: the unified zoom is Google-equivalent, so a fixed offset
  (`MAPPLS_TO_GOOGLE_ZOOM_OFFSET`) is applied when converting to and from Mappls-native zoom.

## Files

| File | Role |
| --- | --- |
| `MapplsMapView.kt` | Compose entry point / controller construction |
| `MapplsViewController.kt` | Central controller for camera, markers and design |
| `MapplsMapViewHolder.kt` | `MapView` / `MapplsMap` wrapper, coordinate conversion |
| `MapplsDesign.kt` | Style (map design) definitions |
| `MapplsHttpBridge.kt` | Replacement HTTP module (see notes above) |
| `MapplsCapabilities.kt` | Capability declarations for the core |
| `MapplsPolyUtils.kt` | Polygon / polyline geometry helpers |
| `MapCameraPosition.kt` | Camera position conversions |
| `GeoPoint.kt` / `GeoRectBounds.kt` | Coordinate type conversions |

## License

Apache License 2.0
