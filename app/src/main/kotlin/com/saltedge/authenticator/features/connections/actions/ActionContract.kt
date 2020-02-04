/*
 * This file is part of the Salt Edge Authenticator distribution
 * (https://github.com/saltedge/sca-authenticator-android).
 * Copyright (c) 2019 Salt Edge Inc.
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
package com.saltedge.authenticator.features.connections.actions

import com.saltedge.authenticator.sdk.tools.ActionDeepLinkData

interface ActionContract {

    interface View {
        fun updateViewsContent()
        fun closeView()
        fun showErrorAndFinish(message: String)
        fun returnActionWithConnectionId(authorizationID: String, connectionID: String)
    }

    interface Presenter {
        var viewContract: View?
        val iconResId: Int
        val completeTitle: String
        val completeMessage: String
        val mainActionTextResId: Int
        var showCompleteView: Boolean
        fun setInitialData(connectionGuid: String, actionDeepLinkData: ActionDeepLinkData)
        fun onDestroyView()
        fun getTitleResId(): Int
        fun onViewCreated()
        fun onViewClick(viewId: Int)
    }
}
