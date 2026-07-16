package com.stratum.core.ui.importing

/** Shared document MIME arrays for historical log and program template pickers. */
object ImportMimeTypes {
    val HISTORICAL: Array<String> =
        arrayOf(
            "text/*",
            "application/json",
            "text/csv",
            "text/comma-separated-values",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )

    val PROGRAM: Array<String> =
        arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/json",
            "text/*",
        )
}
