package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * By-id lift resolution (unfiltered by tombstone — STR-131): history and
 * grandfathered sessions resolve retained tombstoned rows through here.
 */
class GetLiftsByIdsUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(ids: Collection<LiftId>): List<Lift> = repository.getLiftsByIds(ids)
}
