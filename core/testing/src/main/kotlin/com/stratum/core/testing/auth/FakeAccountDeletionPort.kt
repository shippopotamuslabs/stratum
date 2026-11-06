package com.stratum.core.testing.auth

import com.stratum.application.auth.AccountDeletionPort

class FakeAccountDeletionPort : AccountDeletionPort {
    var deleteDataCount: Int = 0
        private set
    var deleteAuthCount: Int = 0
        private set
    /** When non-null, [deleteRemoteUserData] throws this instead of succeeding. */
    var deleteDataFailure: Throwable? = null
    /** When non-null, [deleteRemoteAuthUser] throws this instead of succeeding. */
    var deleteAuthFailure: Throwable? = null

    /** Total remote calls (data + auth phases). */
    val deleteCount: Int get() = deleteDataCount + deleteAuthCount

    override suspend fun deleteRemoteUserData() {
        deleteDataCount++
        deleteDataFailure?.let { throw it }
    }

    override suspend fun deleteRemoteAuthUser() {
        deleteAuthCount++
        deleteAuthFailure?.let { throw it }
    }
}
