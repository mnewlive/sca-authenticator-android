/*
 * Copyright (c) 2024 Salt Edge Inc.
 */
package com.saltedge.authenticator.features.actions

import com.saltedge.authenticator.core.model.ActionAppLinkData
import com.saltedge.authenticator.core.model.GUID
import com.saltedge.authenticator.core.model.RichConnection
import com.saltedge.authenticator.core.tools.secure.KeyManagerAbs
import com.saltedge.authenticator.models.Connection
import com.saltedge.authenticator.models.repository.ConnectionsRepositoryAbs
import com.saltedge.authenticator.models.toRichConnection
import com.saltedge.authenticator.sdk.v2.api.API_V2_VERSION
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SubmitActionInteractor(
    private val defaultDispatcher: CoroutineDispatcher,
    private val connectionsRepository: ConnectionsRepositoryAbs,
    private val keyStoreManager: KeyManagerAbs
    ): SubmitActionInteractorAbs {

    override var contract: SubmitActionInteractorCallback? = null
    private var appLinkData: ActionAppLinkData? = null

    override fun collectAndProcessConnections(actionAppLinkData: ActionAppLinkData) {
        contract?.coroutineScope?.launch(defaultDispatcher) {
            val connections = collectConnections(actionAppLinkData)
            appLinkData = actionAppLinkData
            contract?.processConnections(connections)
        }
    }

    override fun getReturnTo(): String {
        return appLinkData?.returnTo ?: ""
    }

    override fun getId(): String {
        return appLinkData?.actionIdentifier ?: ""
    }

    override fun getConnection(guid: GUID): RichConnection? {
        return connectionsRepository.getByGuid(guid)?.toRichConnection(keyStoreManager)
    }

    private suspend fun collectConnections(actionAppLinkData: ActionAppLinkData): List<Connection> {
        val connections = if (actionAppLinkData.apiVersion == API_V2_VERSION) {
            actionAppLinkData.providerID?.let {
                connectionsRepository.getAllActiveByProvider(providerID = it)
            }
        } else {
            actionAppLinkData.connectUrl?.let {
                connectionsRepository.getAllActiveByConnectUrl(connectionUrl = it)
            }
        }
        return connections ?: emptyList()
    }
}

interface SubmitActionInteractorAbs {
    fun collectAndProcessConnections(actionAppLinkData: ActionAppLinkData)
    fun getConnection(guid: GUID): RichConnection?
    var contract: SubmitActionInteractorCallback?
    fun getId(): String
    fun getReturnTo(): String
}

interface SubmitActionInteractorCallback {
    val coroutineScope: CoroutineScope
    fun processConnections(connections: List<Connection>)
}
