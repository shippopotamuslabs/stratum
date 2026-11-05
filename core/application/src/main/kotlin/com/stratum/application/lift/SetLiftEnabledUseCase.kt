package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of setting a lift's enabled flag (STR-243; STR-237 Route (i)). The
 * originating throwable of a failed write rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once
 * through its module's DiagnosticDrain. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface SetLiftEnabledResult {
    data object Success : SetLiftEnabledResult

    /** The enabled-flag write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SetLiftEnabledResult
}

/**
 * Flips a single lift's availability. Used by the manual on/off toggle on the Gym & Lifts
 * step. Clearing the preset (when the user toggles a lift against the preset's defaults) is
 * handled by [SetSelectedPresetUseCase]; this use case only owns the per-lift flag.
 */
class SetLiftEnabledUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(id: LiftId, enabled: Boolean): SetLiftEnabledResult =
        try {
            repository.setLiftEnabled(id, enabled)
            SetLiftEnabledResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            SetLiftEnabledResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set lift enabled: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetLiftEnabled"
    }
}
