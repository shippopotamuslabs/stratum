package com.stratum.core.testing.auth

import com.stratum.application.auth.AccountDeletionPort

class FakeAccountDeletionPort : AccountDeletionPort {
    var deleteDataCount: Int = 0
        private set
    var deleteAuthCount: Int = 0
        private set
    var deleteDataResult: Result<Unit> = Result.success(Unit)
    var deleteAuthResult: Result<Unit> = Result.success(Unit)

    /** Total remote calls (data + auth phases). */
    val deleteCount: Int get() = deleteDataCount + deleteAuthCount

    override suspend fun deleteRemoteUserData(): Result<Unit> {
        deleteDataCount++
        return deleteDataResult
    }

    override suspend fun deleteRemoteAuthUser(): Result<Unit> {
        deleteAuthCount++
        return deleteAuthResult
    }
}
