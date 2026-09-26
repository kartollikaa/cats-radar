package dev.catsradar.presentation.locationpicker

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.usecase.LocatePhone
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.SetLocationByHand
import dev.catsradar.domain.usecase.WhereToLook
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LocationPickerStore(
    private val encounterId: String,
    observeEncounter: ObserveEncounter,
    private val whereToLook: WhereToLook,
    private val locatePhone: LocatePhone,
    private val setLocationByHand: SetLocationByHand,
    private val stateMapper: LocationPickerStateMapper,
) : Store<LocationPickerState, LocationPickerIntent, LocationPickerEffect>(LocationPickerState.Loading) {

    private var leaving = false

    init {
        observeEncounter(encounterId)
            .onEach(::onCat)
            .launchIn(viewModelScope)
    }

    private suspend fun onCat(cat: Encounter?) {
        when {
            cat == null || cat.locationSource != LocationSource.NONE -> close()
            state.value == LocationPickerState.Loading -> {
                val start = whereToLook(encounterId)
                setState { if (this == LocationPickerState.Loading) stateMapper.picking(start) else this }
            }
        }
    }

    override suspend fun handle(intent: LocationPickerIntent) {
        when (intent) {
            LocationPickerIntent.BackClicked -> close()
            LocationPickerIntent.WhereAmIClicked -> onWhereAmI()
            is LocationPickerIntent.LocationPermissionResult -> onPermission(intent.granted)
            LocationPickerIntent.MoveReached -> update { copy(moveTo = null) }
            is LocationPickerIntent.SaveClicked -> save(intent.latitude, intent.longitude)
        }
    }

    private suspend fun onWhereAmI() {
        if (picking()?.locating != false) return
        update { copy(locating = true) }
        emit(LocationPickerEffect.RequestLocationPermission)
    }

    private suspend fun onPermission(granted: Boolean) {
        if (picking()?.locating != true) return
        val point = if (granted) locatePhone() else null
        update { copy(locating = false, moveTo = point?.let(stateMapper::areaAround) ?: moveTo) }
        if (point == null) emit(LocationPickerEffect.PositionUnknown)
    }

    private suspend fun save(latitude: Double, longitude: Double) {
        if (picking()?.saving != false) return
        update { copy(saving = true) }
        var saved = false
        var failed = false
        runStorageWrite(onFailure = { failed = true }) { saved = setLocationByHand(encounterId, latitude, longitude) }
        if (saved) close() else update { copy(saving = false) }
        if (failed) emit(LocationPickerEffect.NotSaved)
    }

    private suspend fun close() {
        if (leaving) return
        leaving = true
        emit(LocationPickerEffect.Close)
    }

    private fun picking(): LocationPickerState.Picking? = state.value as? LocationPickerState.Picking

    private fun update(change: LocationPickerState.Picking.() -> LocationPickerState.Picking) {
        setState { (this as? LocationPickerState.Picking)?.change() ?: this }
    }
}
