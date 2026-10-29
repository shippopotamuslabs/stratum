package com.stratum.domain.program

import com.stratum.domain.onboarding.TrainingAge

/**
 * The kind of a [LiftSet]. OS-7 v1 supports straight sets, myo-reps, and drop sets. This maps
 * the §2.4 set-type taxonomy into the persistence + generation schemas.
 */
enum class SetType { Standard, MyoRep, DropSet }

/**
 * Stratum progression scheme (vertical-slices §2.3). Exactly two variants — do not port
 * LiftLab's wider `ProgressionScheme` enum.
 *
 * - [Linear]: assigned to a **compound** `WorkoutLift` when the user's `TrainingAge` is
 *   `Beginner` (or long layoff). Isolation lifts never use Linear (D8).
 * - [Double]: isolation always; compounds for every other training-age / eligibility case.
 *
 * Assigned per `WorkoutLift` by the server post-pass during program generation (D2); the model
 * never authors this value. Imports default every lift to Double; older rows may still carry
 * Linear on isolation — use [coerceForCompound] to heal those (unlocks intensifiers + correct
 * progression engine).
 */
enum class ProgressionScheme { Linear, Double }

/**
 * Isolation lifts never run Linear (D8 Rejected e/f). When [isCompound] is false, always
 * returns [ProgressionScheme.Double]; otherwise returns this scheme unchanged.
 */
fun ProgressionScheme.coerceForCompound(isCompound: Boolean): ProgressionScheme =
    if (isCompound) this else ProgressionScheme.Double
