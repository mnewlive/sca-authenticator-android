/*
 * Copyright (c) 2024 Salt Edge Inc.
 */
package com.saltedge.authenticator

import com.saltedge.authenticator.cloud.PushTokenUpdater
import com.saltedge.authenticator.core.tools.secure.KeyManagerAbs
import com.saltedge.authenticator.models.Connection
import com.saltedge.authenticator.models.repository.ConnectionsRepositoryAbs
import com.saltedge.authenticator.models.repository.PreferenceRepositoryAbs
import com.saltedge.authenticator.sdk.v2.ScaServiceClientAbs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushTokenUpdaterTest {

    private lateinit var pushTokenUpdater: PushTokenUpdater
    private lateinit var testFactory: TestFactory

    private val mockConnectionsRepository = Mockito.mock(ConnectionsRepositoryAbs::class.java)
    private val mockKeyStoreManager = Mockito.mock(KeyManagerAbs::class.java)
    private val mockApiManagerV2 = Mockito.mock(ScaServiceClientAbs::class.java)
    private val mockPreferenceRepository = Mockito.mock(PreferenceRepositoryAbs::class.java)
    private val testCoroutineScope = TestCoroutineScope()

    @Before
    fun setUp() {
        testFactory = TestFactory()
        testFactory.mockConnections(mockConnectionsRepository)
        testFactory.mockRichConnections(mockKeyStoreManager)

        Mockito.doReturn("storedPushToken").`when`(mockPreferenceRepository).cloudMessagingToken
        runBlocking {
            given(mockConnectionsRepository.getActiveConnectionsWithoutToken(mockPreferenceRepository.cloudMessagingToken))
                .willReturn(listOf(testFactory.connection2))
        }

        pushTokenUpdater = PushTokenUpdater(
            apiManager = mockApiManagerV2,
            connectionsRepository = mockConnectionsRepository,
            keyStoreManager = mockKeyStoreManager,
            preferenceRepository = mockPreferenceRepository,
            coroutineScope = testCoroutineScope
        )
    }

    @After
    fun tearDown() {
        testCoroutineScope.cleanupTestCoroutines()
    }

    @Test
    @Throws(Exception::class)
    fun updatePushTokenTest() = runBlocking {
        pushTokenUpdater.updatePushToken()

        Mockito.verify(mockApiManagerV2).updatePushToken(
            richConnection = testFactory.richConnection2,
            currentPushToken = "currentPushToken",
            callback = pushTokenUpdater
        )

        Mockito.verify(mockConnectionsRepository).getActiveConnectionsWithoutToken(storedPushToken = "storedPushToken")

        pushTokenUpdater.onUpdatePushTokenSuccess(testFactory.richConnection2.connection.id)

        Mockito.verify(mockConnectionsRepository).saveModel(testFactory.richConnection2.connection as Connection)
        assertEquals(testFactory.richConnection2.connection.pushToken, "storedPushToken")
    }
}
