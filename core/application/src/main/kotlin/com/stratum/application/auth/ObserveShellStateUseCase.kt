package com.stratum.application.auth

import com.stratum.application.sync.ObserveCloudSessionStateUseCase
import com.stratum.domain.auth.AuthState
import com.stratum.domain.sync.CloudSessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Application-layer read model for the app shell (STR-211).
 *
 * Auth plus cloud session plus the entered-app stickiness, so the shell
 * ViewModel can map presentation state from a single durable source per
 * §12.4. [authenticatedUserId] lets write paths (retry) snapshot the uid
 * without a second collector.
 */
data class ShellSnapshot(
    val auth: AuthState,
    val session: CloudSessionState,
    val authenticatedUserId: String?,
    val hasEnteredApp: Boolean,
)

/**
 * Owns the app-shell multi-source composition (STR-211).
 *
 * Combines auth state and cloud session state into one [ShellSnapshot]
 * stream, owning the entered-app latch: once the shell has entered Ready for
 * the current authenticated user (auth Authenticated + session Ready), it
 * stays entered until sign-out. This keeps logout teardown (session →
 * Inactive) from unmounting the NavHost mid-flight. The latch is
 * per-collection state, mirroring the former ViewModel `init` collector.
 */
class ObserveShellStateUseCase @Inject constructor(
    private val getAuthState: GetAuthStateUseCase,
    private val observeSessionState: ObserveCloudSessionStateUseCase,
) {
    operator fun invoke(): Flow<ShellSnapshot> {
        var entered = false
        return combine(getAuthState(), observeSessionState()) { auth, session ->
            val uid = (auth as? AuthState.Authenticated)?.user?.uid
            if (auth !is AuthState.Authenticated) {
                entered = false
            } else if (session is CloudSessionState.Ready) {
                entered = true
            }
            ShellSnapshot(
                auth = auth,
                session = session,
                authenticatedUserId = uid,
                hasEnteredApp = entered,
            )
        }
    }
}
