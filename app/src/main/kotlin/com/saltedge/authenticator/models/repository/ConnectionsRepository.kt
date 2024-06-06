/*
 * Copyright (c) 2019 Salt Edge Inc.
 */
package com.saltedge.authenticator.models.repository

import com.saltedge.authenticator.app.DB_KEY_ACCESS_TOKEN
import com.saltedge.authenticator.app.DB_KEY_API_VERSION
import com.saltedge.authenticator.app.DB_KEY_CONNECT_URL
import com.saltedge.authenticator.app.DB_KEY_CREATED_AT
import com.saltedge.authenticator.app.DB_KEY_PUSH_TOKEN
import com.saltedge.authenticator.app.KEY_CODE
import com.saltedge.authenticator.app.KEY_GUID
import com.saltedge.authenticator.core.api.KEY_ID
import com.saltedge.authenticator.core.api.KEY_STATUS
import com.saltedge.authenticator.core.model.ConnectionStatus
import com.saltedge.authenticator.core.model.GUID
import com.saltedge.authenticator.core.model.ID
import com.saltedge.authenticator.core.model.Token
import com.saltedge.authenticator.models.Connection
import com.saltedge.authenticator.models.realm.RealmManager
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.kotlin.executeTransactionAwait
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.Executors

object ConnectionsRepository : ConnectionsRepositoryAbs {
    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * Checks if the database doesn't contains a connections
     *
     * @return boolean, true if the number of connections is zero
     */
    override fun isEmpty(): Boolean {
        return RealmManager.getDefaultInstance().use { realm ->
            realm.where(Connection::class.java).count() == 0L
        }
    }

    /**
     * Get count of all connections in database
     *
     * @return the count of connections
     */
    override fun getConnectionsCount(): Long {
        return RealmManager.getDefaultInstance().use { it.where(Connection::class.java).count() }
    }

    /**
     * Get count of all connections in database by provider identifier
     *
     * @param providerCode - providerCode of Connection
     * @return the count of connections
     */
    override fun getConnectionsCountForProvider(providerCode: ID): Long {
        return RealmManager.getDefaultInstance().use { realm ->
            realm.where(Connection::class.java).equalTo(KEY_CODE, providerCode).count()
        }
    }

    /**
     * Check if valid connections contains in database
     *
     * @return boolean, true if count of valid connections is more than 0
     * @see queryActiveConnections
     */
    override fun hasActiveConnections(): Boolean {
        return RealmManager.getDefaultInstance().use { it.queryActiveConnections().count() > 0L }
    }

    /**
     * Get all connections from database, sorted by creation date
     *
     * @return list of connections
     */
    override fun getAllConnections(): List<Connection> {
        return RealmManager.getDefaultInstance().use { realm ->
            realm.copyFromRealm(realm.where(Connection::class.java).sort(DB_KEY_CREATED_AT).findAll())
        }
    }

    /**
     * Get all valid/active connections from database
     *
     * @return detached connections
     * @see queryActiveConnections
     */
    override fun getAllActiveConnections(): List<Connection> {
        return RealmManager.getDefaultInstance().use { it.copyFromRealm(it.queryActiveConnections().findAll()) }
    }

    /**
     * Get all valid/active Connections filtered by api version
     *
     * @return detached connections
     * @see queryActiveConnections
     */
    override fun getAllActiveConnectionsByApi(apiVersion: String): List<Connection> {
        return RealmManager.getDefaultInstance().use { realm ->
            realm.copyFromRealm(
                realm.queryActiveConnections()
                    .equalTo(DB_KEY_API_VERSION, apiVersion)
                    .findAll()
            )
        }
    }

    override fun getActiveConnectionsWithoutToken(storedPushToken: String): List<Connection> {
        return RealmManager.getDefaultInstance().use { realm ->
            realm.copyFromRealm(
                realm.queryActiveConnections()
                    .notEqualTo(DB_KEY_PUSH_TOKEN, storedPushToken)
                    .findAll()
            )
        }
    }

    /**
     * Get all valid/active Connections filtered by provider code
     *
     * @param providerID - code of provider
     * @return detached connections
     */
    override fun getAllActiveByProvider(providerID: ID): List<Connection> {
        return RealmManager.getDefaultInstance().use { realmDb ->
            realmDb.queryActiveConnections()
                .equalTo(KEY_CODE, providerID)
                .findAll()
        }
    }

    /**
     * Get all valid/active Connections filtered by Provider connection url
     *
     * @param connectionUrl Provider connection URL
     * @return Connections
     */
    override fun getAllActiveByConnectUrl(connectionUrl: String): List<Connection> {
        return RealmManager.getDefaultInstance().use { realmDb ->
            realmDb.queryActiveConnections()
                .equalTo(DB_KEY_CONNECT_URL, connectionUrl)
                .findAll()
                .let { realmDb.copyFromRealm(it) }
        }
    }

    /**
     * Delete all connections from database
     */
    override suspend fun deleteAllConnections() {
        withContext(singleThreadDispatcher) {
            RealmManager.getDefaultInstance().use { realmDb ->
                realmDb.executeTransactionAwait { transactionRealm ->
                    transactionRealm.delete(Connection::class.java)
                }
            }
        }
    }

    /**
     * Delete connection from database by guid
     *
     * @param connectionGuid - connection guid
     * @return boolean, false if guid is empty or raised exception while saving in db
     */
    override suspend fun deleteConnection(connectionGuid: GUID): Boolean {
        if (connectionGuid.isEmpty() || !connectionExists(connectionGuid)) return false

        withContext(singleThreadDispatcher) {
            RealmManager.getDefaultInstance().use { realmDb ->
                realmDb.executeTransactionAwait { transactionRealm ->
                    transactionRealm.where(Connection::class.java)
                        .equalTo(KEY_GUID, connectionGuid)
                        .findAll()
                        .deleteAllFromRealm()
                }
            }
        }
        return true
    }

    /**
     * Save model of Connection
     *
     * @param connection - model of Connection
     * @return saved Connection
     */
    override suspend fun saveModel(connection: Connection): Connection? {
        if (connection.createdAt == 0L) connection.createdAt = DateTime.now().withZone(DateTimeZone.UTC).millis
        connection.updatedAt = DateTime.now().withZone(DateTimeZone.UTC).millis

        var resultConnection: Connection? = null
        withContext(singleThreadDispatcher) {
            RealmManager.getDefaultInstance().use { realmDb ->
                realmDb.executeTransactionAwait { transactionRealm ->
                    resultConnection = transactionRealm.copyFromRealm(transactionRealm.copyToRealmOrUpdate(connection))
                }
            }
        }
        return resultConnection
    }

    /**
     * Invalidate connections by accessTokens.
     * Updates the status for each connection to ConnectionStatus.INACTIVE
     * and accessToken to empty string
     *
     * @param accessTokens - list of access tokens
     */
    override suspend fun invalidateConnectionsByTokens(accessTokens: List<Token>) {
        withContext(singleThreadDispatcher) {
            RealmManager.getDefaultInstance().use { realmDb ->
                realmDb.executeTransactionAwait { transactionRealm ->
                    transactionRealm.where(Connection::class.java)
                        .`in`(DB_KEY_ACCESS_TOKEN, accessTokens.toTypedArray())
                        .findAll().forEach { connection ->
                            connection?.status = ConnectionStatus.INACTIVE.toString()
                            connection?.accessToken = ""
                        }
                }
            }
        }
    }

    /**
     * Check if connection with specific guid exist in db
     *
     * @param connection - Connection model
     * @return boolean, true if connection exists
     */
    override fun connectionExists(connection: Connection): Boolean =
        connectionExists(connection.guid)

    /**
     * Check if connection with specific guid exist in db
     *
     * @param connectionGuid - guid of Connection model
     * @return boolean, true if connection exist
     * @see getByGuid
     */
    override fun connectionExists(connectionGuid: GUID?): Boolean =
        getByGuid(connectionGuid) != null

    /**
     * Get Connection by Guid
     *
     * @param connectionGuid - guid (optional) of Connection
     * @return Connection with a specific guid
     */
    override fun getByGuid(connectionGuid: GUID?): Connection? {
        return RealmManager.getDefaultInstance().use { realmDb ->
            if (connectionGuid.isNullOrEmpty()) null
            else {
                realmDb.where(Connection::class.java).equalTo(
                    KEY_GUID,
                    connectionGuid
                ).findFirst()?.let {
                    realmDb.copyFromRealm(it)
                }
            }
        }
    }

    /**
     * Get Connection by Id
     *
     * @param connectionID - id of Connection
     * @return Connection by id
     */
    override fun getById(connectionID: String): Connection? {
        return RealmManager.getDefaultInstance().use { realmDb ->
            realmDb.where(Connection::class.java).equalTo(KEY_ID, connectionID).findFirst()?.let {
                realmDb.copyFromRealm(it)
            }
        }
    }

    /**
     * Check by connection code count of with same code and guid is null.
     * Then we add to the connection name add one more value.
     *
     * @param connection - model of Connection
     * @see saveModel
     */
    override suspend fun fixNameAndSave(connection: Connection) {
        getConnectionsCountForProvider(connection.code).let {
            if (it > 0L) connection.name = "${connection.name} (${it + 1})"
        }
        saveModel(connection)
    }

    /**
     * Update name of connection and save the model
     *
     * @param connection - model of Connection
     * @param newName - new name of Connection
     * @see saveModel
     */
    override suspend fun updateNameAndSave(connection: Connection, newName: String) {
        connection.name = newName
        saveModel(connection)
    }

    /**
     * Creates connection query
     *
     * @receiver realm instance
     * @return RealmQuery object with conditions: Connection.status equal to ConnectionStatus.ACTIVE,
     * Connection.accessToke is not empty and result is sorted by creation date
     */
    private fun Realm.queryActiveConnections(): RealmQuery<Connection> {
        return this.where(Connection::class.java)
            .equalTo(KEY_STATUS, ConnectionStatus.ACTIVE.toString())
            .notEqualTo(DB_KEY_ACCESS_TOKEN, "")
            .sort(DB_KEY_CREATED_AT)
    }
}

interface ConnectionsRepositoryAbs {
    fun isEmpty(): Boolean
    fun getConnectionsCount(): Long
    fun getConnectionsCountForProvider(providerCode: ID): Long
    fun hasActiveConnections(): Boolean
    fun connectionExists(connection: Connection): Boolean
    fun connectionExists(connectionGuid: GUID?): Boolean
    fun getAllConnections(): List<Connection>
    fun getAllActiveConnections(): List<Connection>
    fun getAllActiveConnectionsByApi(apiVersion: String): List<Connection>
    fun getAllActiveByConnectUrl(connectionUrl: String): List<Connection>
    fun getActiveConnectionsWithoutToken(storedPushToken: String): List<Connection>
    fun getAllActiveByProvider(providerID: ID): List<Connection>
    fun getByGuid(connectionGuid: GUID?): Connection?
    fun getById(connectionID: ID): Connection?
    suspend fun deleteAllConnections()
    suspend fun deleteConnection(connectionGuid: GUID): Boolean
    suspend fun invalidateConnectionsByTokens(accessTokens: List<Token>)
    suspend fun saveModel(connection: Connection): Connection?
    suspend fun fixNameAndSave(connection: Connection)
    suspend fun updateNameAndSave(connection: Connection, newName: String)
}
