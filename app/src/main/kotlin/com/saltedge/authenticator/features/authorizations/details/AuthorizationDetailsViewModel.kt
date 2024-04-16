/*
 * Copyright (c) 2020 Salt Edge Inc.
 */
package com.saltedge.authenticator.features.authorizations.details

import android.util.Log
import androidx.lifecycle.*
import com.saltedge.authenticator.R
import com.saltedge.authenticator.core.api.model.DescriptionData
import com.saltedge.authenticator.core.api.model.error.ApiErrorData
import com.saltedge.authenticator.core.model.ID
import com.saltedge.authenticator.features.authorizations.common.AuthorizationItemViewModel
import com.saltedge.authenticator.features.authorizations.common.AuthorizationStatus
import com.saltedge.authenticator.features.authorizations.common.BaseAuthorizationViewModel
import com.saltedge.authenticator.features.authorizations.common.computeConfirmedStatus
import com.saltedge.authenticator.models.ViewModelEvent
import com.saltedge.authenticator.models.location.DeviceLocationManagerAbs
import com.saltedge.authenticator.sdk.api.model.authorization.AuthorizationIdentifier
import com.saltedge.authenticator.sdk.constants.API_V1_VERSION
import com.saltedge.authenticator.sdk.v2.api.API_V2_VERSION
import com.saltedge.authenticator.tools.ResId
import com.saltedge.authenticator.tools.postUnitEvent
import kotlinx.coroutines.CoroutineScope
import org.joda.time.DateTime

class AuthorizationDetailsViewModel(
    private val interactorV1: AuthorizationDetailsInteractorAbs,
    private val interactorV2: AuthorizationDetailsInteractorAbs,
    private val locationManager: DeviceLocationManagerAbs
) : BaseAuthorizationViewModel(locationManager),
    LifecycleObserver,
    AuthorizationDetailsInteractorCallback {

    override val coroutineScope: CoroutineScope
        get() = viewModelScope
    val onErrorEvent = MutableLiveData<ViewModelEvent<ApiErrorData>>()
    val onCloseAppEvent = MutableLiveData<ViewModelEvent<Unit>>()
    val onCloseViewEvent = MutableLiveData<ViewModelEvent<Unit>>()
    val onTimeUpdateEvent = MutableLiveData<ViewModelEvent<Unit>>()
    val authorizationModel = MutableLiveData<AuthorizationItemViewModel>()
    val onShowAuthorizationsListEvent = MutableLiveData<ViewModelEvent<Unit>>()
    var titleRes: ResId = R.string.authorization_feature_title
        private set
    private lateinit var interactor: AuthorizationDetailsInteractorAbs
    private var isConfirmationInProgress = false
    private var closeAppOnBackPress: Boolean = true
    private val currentStatus: AuthorizationStatus
        get() = authorizationModel.value?.status ?: AuthorizationStatus.LOADING
    private val authorizationHasFinalMode: Boolean
        get() = authorizationModel.value?.hasFinalStatus ?: false

    fun setInitialData(
        identifier: AuthorizationIdentifier?,
        closeAppOnBackPress: Boolean?,
        titleRes: ResId?
    ) {
        this.closeAppOnBackPress = closeAppOnBackPress ?: true
        this.titleRes = titleRes ?: R.string.authorization_feature_title
        if (this.titleRes == 0) this.titleRes = R.string.authorization_feature_title

        val connectionID = identifier?.connectionID ?: ""
        val apiVersion = AuthorizationDetailsInteractor.getApiVersion(connectionID)

        interactor = if (apiVersion == API_V2_VERSION) {
            interactorV2
        } else {
            interactorV1
        }
        interactor.setInitialData(connectionID = connectionID)
        interactor.contract = this

        val status = if (interactor.noConnection || identifier == null || !identifier.hasAuthorizationID) {
                AuthorizationStatus.UNAVAILABLE
            } else {
                AuthorizationStatus.LOADING
            }
        createInitialItem(identifier, status, interactor.connectionApiVersion)
    }

    fun bindLifecycleObserver(lifecycle: Lifecycle) {
        lifecycle.let {
            it.removeObserver(this)
            it.addObserver(this)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onFragmentResume() {
        startPolling()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onFragmentPause() {
        interactor.stopPolling()
    }

    fun onViewClick(itemViewId: Int) {
        onViewItemClick(itemViewId, authorizationModel.value)
    }

    fun onTimerTick() {
        authorizationModel.value?.also { model ->
            when {
                model.shouldBeSetTimeOutMode -> {
                    interactor.stopPolling()
                    updateToFinalViewMode(AuthorizationStatus.TIME_OUT)
                }
                model.shouldBeDestroyed -> closeView()
                !model.ignoreTimeUpdate -> onTimeUpdateEvent.postUnitEvent()
            }
        }
    }

    fun onBackPress(): Boolean {
        closeView()
        return true
    }

    override fun onAuthorizationReceived(
        data: AuthorizationItemViewModel?,
        newModelApiVersion: String
    ) {
        if (currentStatus.isProcessing()) return //skip polling result if confirm/deny is in progress
        if (!authorizationHasFinalMode && authorizationModel.value != data) {
            if (data == null) updateToFinalViewMode(AuthorizationStatus.ERROR)
            else authorizationModel.postValue(data)
        }
    }

    override fun onConnectionNotFoundError() {
        updateToFinalViewMode(AuthorizationStatus.ERROR)
    }

    override fun onAuthorizationNotFoundError() {
        if ((currentStatus === AuthorizationStatus.LOADING || currentStatus === AuthorizationStatus.PENDING)) {
            updateToFinalViewMode(AuthorizationStatus.UNAVAILABLE)
        }
    }

    override fun onConnectivityError(error: ApiErrorData) {
        onErrorEvent.postValue(ViewModelEvent(error))
    }

    override fun onError(error: ApiErrorData) {
        if (currentStatus != AuthorizationStatus.ERROR) {
            onErrorEvent.postValue(ViewModelEvent(error))
            updateToFinalViewMode(AuthorizationStatus.ERROR)
        }
    }

    override fun onConfirmDenySuccess(newStatus: AuthorizationStatus?) {
        isConfirmationInProgress = false
        updateAuthorizationStatus(newStatus = newStatus ?: currentStatus.computeConfirmedStatus())
    }

    override fun updateAuthorization(item: AuthorizationItemViewModel, confirm: Boolean) {
        isConfirmationInProgress = true
        updateAuthorizationStatus(if (confirm) AuthorizationStatus.CONFIRM_PROCESSING else AuthorizationStatus.DENY_PROCESSING)
        interactor.updateAuthorization(
            authorizationID = item.authorizationID,
            authorizationCode = item.authorizationCode,
            confirm = confirm,
            locationDescription = locationManager.locationDescription
        )
    }

    private fun updateToFinalViewMode(newStatus: AuthorizationStatus) {
        updateAuthorizationStatus(newStatus)
        onTimeUpdateEvent.postUnitEvent()
    }

    private fun updateAuthorizationStatus(newStatus: AuthorizationStatus) {
        authorizationModel.value?.let {
            it.setNewStatus(newStatus = newStatus)
            authorizationModel.postValue(it)
        }
    }

    private fun startPolling() {
        val authorizationID = authorizationModel.value?.authorizationID ?: return
        if (currentStatus != AuthorizationStatus.UNAVAILABLE &&
            !currentStatus.isFinal() && !isConfirmationInProgress) {
            interactor.startPolling(authorizationID = authorizationID)
        } else {
            onShowAuthorizationsListEvent.postUnitEvent()
        }
    }

    private fun closeView() {
        if (closeAppOnBackPress) onCloseAppEvent.postUnitEvent()
        else onCloseViewEvent.postUnitEvent()
    }

    private fun createInitialItem(
        identifier: AuthorizationIdentifier?,
        status: AuthorizationStatus,
        apiVersion: String?
    ) {
        authorizationModel.value = AuthorizationItemViewModel(
            authorizationID = identifier?.authorizationID ?: "",
            authorizationCode = "",
            title = "",
            description = DescriptionData(),
            validSeconds = 0,
            endTime = DateTime(0L),
            startTime = DateTime(0L),
            connectionID = identifier?.connectionID ?: "",
            connectionName = "",
            connectionLogoUrl = "",
            status = status,
            apiVersion = apiVersion ?: API_V1_VERSION,
            geolocationRequired = false
        )
    }
}
