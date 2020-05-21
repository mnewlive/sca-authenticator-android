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
package com.saltedge.authenticator.features.authorizations.list

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.saltedge.authenticator.R
import com.saltedge.authenticator.features.authorizations.common.ViewMode
import com.saltedge.authenticator.features.authorizations.common.toAuthorizationViewModel
import com.saltedge.authenticator.models.Connection
import com.saltedge.authenticator.models.ViewModelEvent
import com.saltedge.authenticator.models.repository.ConnectionsRepositoryAbs
import com.saltedge.authenticator.sdk.AuthenticatorApiManagerAbs
import com.saltedge.authenticator.sdk.constants.ERROR_CLASS_CONNECTION_NOT_FOUND
import com.saltedge.authenticator.sdk.model.authorization.AuthorizationData
import com.saltedge.authenticator.sdk.model.connection.ConnectionAndKey
import com.saltedge.authenticator.sdk.model.connection.ConnectionStatus
import com.saltedge.authenticator.sdk.model.error.ApiErrorData
import com.saltedge.authenticator.sdk.model.error.createRequestError
import com.saltedge.authenticator.sdk.model.error.getErrorMessage
import com.saltedge.authenticator.sdk.model.response.ConfirmDenyResponseData
import com.saltedge.authenticator.sdk.polling.PollingServiceAbs
import com.saltedge.authenticator.sdk.tools.crypt.CryptoToolsAbs
import com.saltedge.authenticator.sdk.tools.keystore.KeyStoreManagerAbs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import java.security.PrivateKey

@RunWith(RobolectricTestRunner::class)
class AuthorizationsListViewModelTest {

    @Test
    @Throws(Exception::class)
    fun processDecryptedAuthorizationsResultTestCase1() {
        //given
        val viewModel = createViewModel()
        viewModel.listItems.postValue(emptyList())

        //when
        viewModel.processDecryptedAuthorizationsResult(result = listOf(authorizationData2, authorizationData1))

        //then
        assertThat(viewModel.listItemsValues, equalTo(listOf(viewModel1, viewModel2)))
        assertThat(viewModel.emptyViewVisibility.value, equalTo(View.GONE))
        assertThat(viewModel.listVisibility.value, equalTo(View.VISIBLE))
    }

    @Test
    @Throws(Exception::class)
    fun processDecryptedAuthorizationsResultTestCase2() {
        //given
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.processDecryptedAuthorizationsResult(result = emptyList())

        //then
        assertThat(viewModel.listItemsValues, equalTo(emptyList()))
        assertThat(viewModel.emptyViewVisibility.value, equalTo(View.VISIBLE))
        assertThat(viewModel.listVisibility.value, equalTo(View.GONE))
    }

    @Test
    @Throws(Exception::class)
    fun processDecryptedAuthorizationsResultTestCase3() {
        //given
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.processDecryptedAuthorizationsResult(result = listOf(authorizationData1, authorizationData2))

        //then
        assertThat(viewModel.listItemsValues, equalTo(listOf(viewModel1, viewModel2)))
    }

    @Test
    @Throws(Exception::class)
    fun processDecryptedAuthorizationsResultTestCase4() {
        //given
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2.copy(viewMode = ViewMode.DENY_SUCCESS)))

        //when
        viewModel.processDecryptedAuthorizationsResult(result = listOf(authorizationData1, authorizationData2))

        //then
        assertThat(viewModel.listItemsValues.size, equalTo(2))
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(viewModel1, viewModel2.copy(viewMode = ViewMode.DENY_SUCCESS)))
        )
    }

    @Test
    @Throws(Exception::class)
    fun getCurrentConnectionsAndKeysForPollingTestCase1() {
        //given
        given(mockConnectionsRepository.getAllActiveConnections()).willReturn(emptyList<Connection>())
        val viewModel = createViewModel()

        //when
        val result = viewModel.getCurrentConnectionsAndKeysForPolling()

        //then
        assertThat(result, `is`(nullValue()))
    }

    @Test
    @Throws(Exception::class)
    fun getCurrentConnectionsAndKeysForPollingTestCase2() {
        //given
        val viewModel = createViewModel()

        //when
        val result = viewModel.getCurrentConnectionsAndKeysForPolling()

        //then
        assertThat(result, equalTo(listOf(mockConnectionAndKey)))
    }

    @Test
    @Throws(Exception::class)
    fun onTimeUpdateTestCase1() {
        //given list with expired item
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2.copy(expiresAt = DateTime(0))))

        //when
        viewModel.onTimeUpdate()

        //then
        assertThat(
            viewModel.listItemsValues[1],
            equalTo(viewModel2.copy(expiresAt = DateTime(0), viewMode = ViewMode.TIME_OUT))
        )
    }

    @Test
    @Throws(Exception::class)
    fun onTimeUpdateTestCase2() {
        //given list with expired item
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1,
            viewModel2.copy(viewMode = ViewMode.TIME_OUT).apply { destroyAt = DateTime(0) }
        ))

        //when
        viewModel.onTimeUpdate()

        //then
        assertThat(viewModel.listItemsValues, equalTo(listOf(viewModel1)))
    }

    @Test
    @Throws(Exception::class)
    fun onListItemClickTestCase1() {
        //given invalid itemIndex
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.onListItemClick(itemIndex = 5, itemCode = "", itemViewId = 1)

        //then
        verify(mockApiManager).createAuthorizationsPollingService()
        verifyNoMoreInteractions(mockApiManager)
    }

    @Test
    @Throws(Exception::class)
    fun onListItemClickTestCase2() {
        //given no connections
        given(mockConnectionsRepository.getAllActiveConnections()).willReturn(emptyList<Connection>())
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.onListItemClick(itemIndex = 0, itemCode = "", itemViewId = R.id.titleTextView)

        //then
        verify(mockApiManager).createAuthorizationsPollingService()
        verifyNoMoreInteractions(mockApiManager)
    }

    @Test
    @Throws(Exception::class)
    fun onListItemClickTestCase3() {
        //given invalid itemViewId
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.onListItemClick(itemIndex = 0, itemCode = "", itemViewId = R.id.titleTextView)

        //then
        verify(mockApiManager).createAuthorizationsPollingService()
        verifyNoMoreInteractions(mockApiManager)
    }

    @Test
    @Throws(Exception::class)
    fun onListItemClickTestCase4() {
        //given itemViewId = R.id.positiveActionView
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.onListItemClick(
            itemIndex = 0,
            itemCode = "",
            itemViewId = R.id.positiveActionView
        )

        //then
        assertThat(viewModel.listItemUpdateEvent.value, equalTo(ViewModelEvent(0)))
        assertThat(
            viewModel.listItemsValues.first(),
            equalTo(viewModel1.copy(viewMode = ViewMode.CONFIRM_PROCESSING))
        )
        verify(mockApiManager).confirmAuthorization(
            connectionAndKey = mockConnectionAndKey,
            authorizationId = viewModel1.authorizationID,
            authorizationCode = viewModel1.authorizationCode,
            resultCallback = viewModel
        )
    }

    @Test
    @Throws(Exception::class)
    fun onListItemClickTestCase5() {
        //given itemViewId = R.id.negativeActionView
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(viewModel1, viewModel2))

        //when
        viewModel.onListItemClick(
            itemIndex = 0,
            itemCode = "",
            itemViewId = R.id.negativeActionView
        )

        //then
        assertThat(viewModel.listItemUpdateEvent.value, equalTo(ViewModelEvent(0)))
        assertThat(
            viewModel.listItemsValues.first(),
            equalTo(viewModel1.copy(viewMode = ViewMode.DENY_PROCESSING))
        )
        verify(mockApiManager).denyAuthorization(
            connectionAndKey = mockConnectionAndKey,
            authorizationId = viewModel1.authorizationID,
            authorizationCode = viewModel1.authorizationCode,
            resultCallback = viewModel
        )
    }

    @Test
    @Throws(Exception::class)
    fun onFetchEncryptedDataResultTestCase1() {
        //given Authorizations errors
        val viewModel = createViewModel()
        clearInvocations(mockConnectionsRepository)

        //when
        viewModel.onFetchEncryptedDataResult(
            result = emptyList(),
            errors = listOf(createRequestError(404))
        )

        //then
        verifyNoMoreInteractions(mockConnectionsRepository)
    }

    @Test
    @Throws(Exception::class)
    fun onFetchEncryptedDataResultTestCase2() {
        //given Authorizations errors
        val viewModel = createViewModel()
        clearInvocations(mockConnectionsRepository)

        //when
        viewModel.onFetchEncryptedDataResult(
            result = emptyList(),
            errors = listOf(ApiErrorData(errorClassName = ERROR_CLASS_CONNECTION_NOT_FOUND))
        )

        //then
        verifyNoMoreInteractions(mockConnectionsRepository)
    }

    @Test
    @Throws(Exception::class)
    fun onFetchEncryptedDataResultTestCase3() {
        //given Authorizations errors
        val viewModel = createViewModel()
        clearInvocations(mockConnectionsRepository)

        //when
        viewModel.onFetchEncryptedDataResult(
            result = emptyList(),
            errors = listOf(
                ApiErrorData(
                    errorClassName = ERROR_CLASS_CONNECTION_NOT_FOUND,
                    accessToken = "token"
                )
            )
        )

        //then
        verify(mockConnectionsRepository).invalidateConnectionsByTokens(listOf("token"))
        verify(mockConnectionsRepository).getAllActiveConnections()
    }

    //TODO INVESTIGATE TEST OF COROUTINES (https://github.com/saltedge/sca-authenticator-android/issues/39)
//    @Test
//    @Throws(Exception::class)
//    fun onFetchEncryptedDataResultTest_processEncryptedAuthorizationsResult() {
//
//    }

    @Test
    @Throws(Exception::class)
    fun onConfirmDenySuccessTestCase1() {
        //given success result of CONFIRM
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1.copy(),
            viewModel2.copy(viewMode = ViewMode.CONFIRM_PROCESSING)
        ))
        val result = ConfirmDenyResponseData(success = true, authorizationID = "2")

        //when
        viewModel.onConfirmDenySuccess(result = result, connectionID = "1")

        //then
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(viewModel1, viewModel2.copy(viewMode = ViewMode.CONFIRM_SUCCESS)))
        )
    }

    @Test
    @Throws(Exception::class)
    fun onConfirmDenySuccessTestCase2() {
        //given success result of DENY
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1.copy(),
            viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
        ))
        val result = ConfirmDenyResponseData(success = true, authorizationID = "2")

        //when
        viewModel.onConfirmDenySuccess(result = result, connectionID = "1")

        //then
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(viewModel1, viewModel2.copy(viewMode = ViewMode.DENY_SUCCESS)))
        )
    }

    @Test
    @Throws(Exception::class)
    fun onConfirmDenySuccessTestCase3() {
        //given invalid params
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1.copy(),
            viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
        ))
        val result = ConfirmDenyResponseData(success = true, authorizationID = "101")

        //when
        viewModel.onConfirmDenySuccess(result = result, connectionID = "1")

        //then
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(
                viewModel1,
                viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
            ))
        )
    }

    @Test
    @Throws(Exception::class)
    fun onConfirmDenySuccessTestCase4() {
        //given invalid params
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1.copy(),
            viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
        ))
        val result = ConfirmDenyResponseData(success = true, authorizationID = "1")

        //when
        viewModel.onConfirmDenySuccess(result = result, connectionID = "101")

        //then
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(
                viewModel1.copy(),
                viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
            ))
        )
    }

    @Test
    @Throws(Exception::class)
    fun onConfirmDenyFailureTestCase1() {
        //given
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1.copy(),
            viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
        ))
        val error = createRequestError(404)

        //when
        viewModel.onConfirmDenyFailure(error = error, connectionID = "1", authorizationID = "2")

        //then
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(viewModel1, viewModel2.copy(viewMode = ViewMode.ERROR)))
        )
        assertThat(viewModel.onConfirmErrorEvent.value, equalTo(ViewModelEvent(error.getErrorMessage(context))))
    }

    @Test
    @Throws(Exception::class)
    fun onConfirmDenyFailureTestCase2() {
        //given invalid params
        val viewModel = createViewModel()
        viewModel.listItems.postValue(listOf(
            viewModel1.copy(),
            viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)
        ))
        val error = createRequestError(404)

        //when
        viewModel.onConfirmDenyFailure(error = error, connectionID = "101", authorizationID = "102")

        //then
        assertThat(
            viewModel.listItemsValues,
            equalTo(listOf(viewModel1, viewModel2.copy(viewMode = ViewMode.DENY_PROCESSING)))
        )
        assertThat(viewModel.onConfirmErrorEvent.value, equalTo(ViewModelEvent("Request Error (404)")))
    }

    @Before
    fun setUp() {
        doReturn(mockPollingService).`when`(mockApiManager).createAuthorizationsPollingService()
        given(mockConnectionsRepository.getAllActiveConnections()).willReturn(listOf(mockConnection1))
        given(mockKeyStoreManager.createConnectionAndKeyModel(mockConnection1)).willReturn(mockConnectionAndKey)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockPrivateKey = Mockito.mock(PrivateKey::class.java)
    private val mockKeyStoreManager = Mockito.mock(KeyStoreManagerAbs::class.java)
    private val mockConnectionsRepository = Mockito.mock(ConnectionsRepositoryAbs::class.java)
    private val mockCryptoTools = Mockito.mock(CryptoToolsAbs::class.java)
    private val mockApiManager = Mockito.mock(AuthenticatorApiManagerAbs::class.java)
    private val mockPollingService = Mockito.mock(PollingServiceAbs::class.java)

    private val mockConnection1 = Connection().apply {
        guid = "guid1"
        id = "1"
        code = "demobank3"
        name = "Demobank3"
        status = "${ConnectionStatus.ACTIVE}"
        accessToken = "token1"
        logoUrl = "url"
        createdAt = 200L
        updatedAt = 200L
    }
    private val mockConnectionAndKey = ConnectionAndKey(mockConnection1, mockPrivateKey)
    private val authorizationData1 = createAuthorization(id = 1)
    private val authorizationData2 = createAuthorization(id = 2)
    private val viewModel1 = authorizationData1.toAuthorizationViewModel(mockConnection1)
    private val viewModel2 = authorizationData2.toAuthorizationViewModel(mockConnection1)

    private fun createViewModel(): AuthorizationsListViewModel {
        return AuthorizationsListViewModel(
            appContext = context,
            connectionsRepository = mockConnectionsRepository,
            keyStoreManager = mockKeyStoreManager,
            cryptoTools = mockCryptoTools,
            apiManager = mockApiManager
        )
    }

    private fun createAuthorization(id: Int): AuthorizationData {
        val createdAt = DateTime.now(DateTimeZone.UTC)
        return AuthorizationData(
            id = "$id",
            authorizationCode = "$id$id$id",
            createdAt = createdAt,
            expiresAt = createdAt.plusMinutes(3),
            title = "title$id",
            description = "desc$id",
            connectionId = "1"
        )
    }
}