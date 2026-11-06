package com.stratum.core.testing.auth

import com.stratum.application.auth.AccountDeletionOutcome
import com.stratum.application.auth.AccountDeletionPort
import kotlinx.coroutines.CancellationException

class FakeAccountDeletionPort : AccountDeletionPort {
    var deleteDataCount: Int = 0
        private set
    var deleteAuthCount: Int = 0
        private set

    /**
     * When non-null, [deleteRemoteUserData] returns [AccountDeletionOutcome.Failed]
     * instead of succeeding. A [CancellationException] still throws — cancellation
     * is never an outcome branch.
     */
    var deleteDataFailure: Throwable? = null

    /** Same contract as [deleteDataFailure], for [deleteRemoteAuthUser]. */
    var deleteAuthFailure: Throwable? = null

    /** Total remote calls (data + auth phases). */
    val deleteCount: Int get() = deleteDataCount + deleteAuthCount

    override suspend fun deleteRemoteUserData(): AccountDeletionOutcome {
        deleteDataCount++
        val failure = deleteDataFailure ?: return AccountDeletionOutcome.Success
        if (failure is CancellationException) throw failure
        return AccountDeletionOutcome.Failed
    }

    override suspend fun deleteRemoteAuthUser(): AccountDeletionOutcome {
        deleteAuthCount++
        val failure = deleteAuthFailure ?: return AccountDeletionOutcome.Success
        if (failure is CancellationException) throw failure
        return AccountDeletionOutcome.Failed
    }
}
