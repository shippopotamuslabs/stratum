package com.stratum.domain.program

/**
 * The kind of a [LiftSet]. OS-7 v1 supports straight sets, myo-reps, and drop sets. This maps
 * the §2.4 set-type taxonomy into the persistence + generation schemas.
 */
enum class SetType { Standard, MyoRep, DropSet }

/**
 * Stratum progression scheme (vertical-slices §2.3). Exactly two variants — do not port
 * LiftLab's wider `ProgressionScheme` enum.
 *
 * - [Linear]: assigned to a `WorkoutLift` when the user's `TrainingAge` is `Beginner`.
 * - [Double]: assigned for every other `TrainingAge`.
 *
 * Assigned per `WorkoutLift` by the server post-pass during program generation (D2); the model
 * never authors this value.
 */
enum class ProgressionScheme { Linear, Double }
