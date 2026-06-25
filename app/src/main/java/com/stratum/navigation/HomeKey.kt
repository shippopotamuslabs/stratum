package com.stratum.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Nav3 key for the home destination (owned by the app shell). */
@Serializable
data object HomeKey : NavKey
