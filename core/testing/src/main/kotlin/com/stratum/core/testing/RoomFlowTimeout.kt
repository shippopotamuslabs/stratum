package com.stratum.core.testing

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Wall-clock budget for the first emission of Room query Flows under test. Room serves
// observations on shared background executors, so on a loaded host (parallel CI jobs) the
// first emission can arrive later than Turbine's 1s default and flake with a timeout.
val roomFirstEmissionTimeout: Duration = 10.seconds
