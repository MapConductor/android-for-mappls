package com.mapconductor.mappls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.mapconductor.compose.map.BaseMapViewSaver
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapViewState
import com.mapconductor.core.map.MapViewStateInterface
import java.util.UUID
import android.os.Bundle

interface MapplsViewStateInterface : MapViewStateInterface<MapplsMapDesignTypeInterface>

class MapplsViewState(
    mapDesignType: MapplsMapDesignTypeInterface,
    override val id: String,
    cameraPosition: MapCameraPosition = MapCameraPosition.Default,
) : MapViewState<MapplsMapDesignTypeInterface>(cameraPosition),
    MapplsViewStateInterface {
    private var controller: MapplsViewControllerInterface? = null
    private var _mapDesignType: MapplsMapDesignTypeInterface = mapDesignType

    override var mapDesignType: MapplsMapDesignTypeInterface
        set(value) {
            _mapDesignType = value
            this.controller?.setMapDesignType(value)
        }
        get() = _mapDesignType

    internal fun setController(controller: MapplsViewControllerInterface) {
        this.controller = controller
        attachController(controller)
    }

    internal fun onMapDesignTypeChange(value: MapplsMapDesignTypeInterface) {
        _mapDesignType = value
    }

    /** 戻り型をこのプロバイダのホルダーへ絞る（アプリが `?.map` を取れる形を保つため）。 */
    override fun getMapViewHolder(): MapplsMapViewHolderInterface? =
        super.getMapViewHolder() as? MapplsMapViewHolderInterface

    internal fun updateCameraPosition(cameraPosition: MapCameraPosition) {
        setCameraPositionInternal(cameraPosition)
    }
}

class MapplsMapViewSaver : BaseMapViewSaver<MapplsViewState>() {
    override fun saveMapDesign(
        state: MapplsViewState,
        bundle: Bundle,
    ) {
        bundle.putString("styleName", state.mapDesignType.styleName)
    }

    override fun createState(
        stateId: String,
        mapDesignBundle: Bundle?,
        cameraPosition: MapCameraPosition,
    ): MapplsViewState =
        MapplsViewState(
            id = stateId,
            mapDesignType =
                MapplsDesign(
                    id =
                        mapDesignBundle?.getString("id")
                            ?: MapplsDesign.Default.id,
                    styleName =
                        mapDesignBundle?.getString("styleName")
                            ?: MapplsDesign.Default.styleName,
                ),
            cameraPosition = cameraPosition,
        )

    override fun getStateId(state: MapplsViewState): String = state.id
}

@Composable
fun rememberMapplsMapViewState(
    mapDesign: MapplsMapDesignTypeInterface = MapplsDesign.Default,
    cameraPosition: MapCameraPositionInterface = MapCameraPosition.Default,
): MapplsViewState {
    val stateId by rememberSaveable {
        val uuid = UUID.randomUUID().toString()
        mutableStateOf(uuid)
    }
    val state =
        rememberSaveable(
            stateSaver = MapplsMapViewSaver().createSaver(),
        ) {
            mutableStateOf(
                MapplsViewState(
                    id = stateId,
                    mapDesignType = mapDesign,
                    cameraPosition = MapCameraPosition.from(cameraPosition),
                ),
            )
        }

    return state.value
}
