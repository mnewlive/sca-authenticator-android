/*
 * This file is part of the Salt Edge Authenticator distribution
 * (https://github.com/saltedge/sca-authenticator-android).
 * Copyright (c) 2021 Salt Edge Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 or later.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * For the additional permissions granted for Salt Edge Authenticator
 * under Section 7 of the GNU General Public License see THIRD_PARTY_NOTICES.md
 */
package com.saltedge.authenticator.sdk.v2.polling

import com.saltedge.authenticator.core.model.RichConnection
import com.saltedge.authenticator.core.polling.PollingServiceAbs
import com.saltedge.authenticator.sdk.v2.api.connector.AuthorizationShowConnector
import com.saltedge.authenticator.sdk.v2.api.contract.FetchAuthorizationListener
import com.saltedge.authenticator.sdk.v2.api.retrofit.RestClient

/**
 * Periodically query authorization
 *
 * @see PollingServiceAbs
 */
open class SingleAuthorizationPollingService : PollingServiceAbs<PollingAuthorizationContract>() {

    internal var connector: AuthorizationShowConnector? = null
    override var contract: PollingAuthorizationContract? = null
    private var authorizationID: String = ""

    fun start(authorizationID: String) {
        this.authorizationID = authorizationID
        connector = AuthorizationShowConnector(
            apiInterface = RestClient.apiInterface,
            resultCallback = contract
        )
        super.start()
    }

    override fun stop() {
        super.stop()
        connector = null
    }

    override fun forcedFetch() {
        try {
            contract?.getConnectionDataForAuthorizationPolling()?.let {
                connector?.showAuthorization(
                    connection = it.connection,
                    authorizationId = authorizationID
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

interface PollingAuthorizationContract : FetchAuthorizationListener {
    fun getConnectionDataForAuthorizationPolling(): RichConnection?
}
