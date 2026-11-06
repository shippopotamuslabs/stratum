package com.stratum.domain.telemetry

/**
 * Stable vocabulary for product analytics events recorded at presentation
 * boundaries (STR-237: moved from the deleted analytics use case;
 * boundary components record through their module's analytics recorder,
 * never through a use case).
 */
const val USER_VISIBLE_ERROR_EVENT = "user_visible_error"
const val PARAM_SCREEN = "screen"
const val PARAM_ACTION = "action"
