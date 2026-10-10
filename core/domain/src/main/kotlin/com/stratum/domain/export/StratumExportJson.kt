package com.stratum.domain.export

import com.stratum.domain.json.jsonArray
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.json.jsonStringOrNull
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.program.importing.NormalizedProgramTemplate
import com.stratum.domain.program.importing.NormalizedProgramTemplateLift
import com.stratum.domain.program.importing.NormalizedProgramTemplateSet
import com.stratum.domain.program.importing.NormalizedProgramTemplateWorkout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.normalizedPartialReps
import java.time.Instant

object StratumExportJson {
    fun encodeLogsEnvelope(
        exportedAt: Instant,
        range: ExportLogRange,
        rangeBounds: Pair<Instant, Instant>?,
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
    ): String {
        val rangeJson = encodeRange(range, rangeBounds)
        val sessionsJson =
            jsonArray(
                sessions.map { session ->
                    val fields = mutableListOf(
                        "id" to jsonString(session.id.value),
                        "startedAt" to jsonString(session.startedAt.toString()),
                    )
                    session.endedAt?.let { fields += "endedAt" to jsonString(it.toString()) }
                    val sourceApp = session.sourceApp ?: StratumExportSchemas.SOURCE
                    fields += "sourceApp" to jsonString(sourceApp)
                    jsonObject(fields)
                },
            )
        val setsJson =
            jsonArray(
                sets.map { set ->
                    // Contract: training-log export always emits Lb (canonical storage unit).
                    val weightLb = set.weight.toStorageLb()
                    val fields = mutableListOf(
                        "id" to jsonString(set.id.value),
                        "sessionId" to jsonString(set.sessionId.value),
                        "exerciseNameRaw" to jsonString(set.exerciseNameRaw),
                        "setIndex" to jsonNumber(set.setIndex),
                        "weightAmount" to jsonNumber(weightLb.amount),
                        "weightUnit" to jsonString(weightUnitLabel(Weight.WeightUnit.Lb)),
                        "reps" to jsonNumber(set.reps),
                        "completedAt" to jsonString(set.completedAt.toString()),
                    )
                    set.liftId?.let { fields += "liftId" to jsonString(it.value) }
                    set.equipmentId?.let { fields += "equipmentId" to jsonString(it.value) }
                    set.rir?.let { fields += "rir" to jsonNumber(it) }
                    set.partialReps.normalizedPartialReps()?.let {
                        fields += "partialReps" to jsonNumber(it)
                    }
                    jsonObject(fields)
                },
            )
        val history =
            jsonObject(
                "sessions" to sessionsJson,
                "sets" to setsJson,
            )
        return jsonObject(
            "schema" to jsonString(StratumExportSchemas.LOGS),
            "schemaVersion" to jsonNumber(StratumExportSchemas.VERSION),
            "source" to jsonString(StratumExportSchemas.SOURCE),
            "exportedAt" to jsonString(exportedAt.toString()),
            "range" to rangeJson,
            "history" to history,
        )
    }

    fun encodeProgramsEnvelope(
        exportedAt: Instant,
        programs: List<Pair<ProgramId, NormalizedProgramTemplate>>,
    ): String {
        val programsJson =
            jsonArray(
                programs.map { (id, template) ->
                    jsonObject(
                        "id" to jsonString(id.value),
                        "template" to encodeTemplate(template),
                    )
                },
            )
        return jsonObject(
            "schema" to jsonString(StratumExportSchemas.PROGRAMS),
            "schemaVersion" to jsonNumber(StratumExportSchemas.VERSION),
            "source" to jsonString(StratumExportSchemas.SOURCE),
            "exportedAt" to jsonString(exportedAt.toString()),
            "programs" to programsJson,
        )
    }

    private fun encodeRange(
        range: ExportLogRange,
        rangeBounds: Pair<Instant, Instant>?,
    ): String {
        val kind =
            when (range) {
                ExportLogRange.AllTime -> "all_time"
                is ExportLogRange.LastDays -> "last_days"
                is ExportLogRange.YearToDate -> "year_to_date"
                is ExportLogRange.Custom -> "custom"
            }
        val fields = mutableListOf("kind" to jsonString(kind))
        if (range is ExportLogRange.LastDays) {
            fields += "days" to jsonNumber(range.days)
        }
        fields +=
            "startInstant" to
            jsonStringOrNull(rangeBounds?.first?.toString())
        fields +=
            "endInstantExclusive" to
            jsonStringOrNull(rangeBounds?.second?.toString())
        return jsonObject(fields)
    }

    private fun encodeTemplate(template: NormalizedProgramTemplate): String =
        jsonObject(
            "programName" to jsonStringOrNull(template.programName),
            "cycleLength" to
                if (template.cycleLength == null) {
                    "null"
                } else {
                    jsonNumber(template.cycleLength)
                },
            "workouts" to
                jsonArray(template.workouts.map { encodeWorkout(it) }),
        )

    private fun encodeWorkout(workout: NormalizedProgramTemplateWorkout): String {
        val fields = mutableListOf(
            "name" to jsonString(workout.name),
            "weekday" to jsonStringOrNull(workout.weekday),
            "position" to jsonNumber(workout.position),
            "restDay" to if (workout.restDay) "true" else "false",
            "lifts" to jsonArray(workout.lifts.map { encodeLift(it) }),
        )
        return jsonObject(fields)
    }

    private fun encodeLift(lift: NormalizedProgramTemplateLift): String {
        val fields = mutableListOf(
            "name" to jsonString(lift.name),
            "position" to jsonNumber(lift.position),
            "sets" to jsonArray(lift.sets.map { encodeSet(it) }),
        )
        lift.supersetGroupKey?.let {
            fields += "supersetGroupKey" to jsonString(it)
        }
        return jsonObject(fields)
    }

    private fun encodeSet(set: NormalizedProgramTemplateSet): String {
        val fields = mutableListOf(
            "position" to jsonNumber(set.position),
            "setTypeLabel" to jsonStringOrNull(set.setTypeLabel),
            "repMin" to if (set.repMin == null) "null" else jsonNumber(set.repMin),
            "repMax" to if (set.repMax == null) "null" else jsonNumber(set.repMax),
            "rirGoal" to if (set.rirGoal == null) "null" else jsonNumber(set.rirGoal),
            "restSeconds" to if (set.restSeconds == null) "null" else jsonNumber(set.restSeconds),
            "isTopSet" to if (set.isTopSet) "true" else "false",
        )
        return jsonObject(fields)
    }

    private fun weightUnitLabel(unit: Weight.WeightUnit): String =
        when (unit) {
            Weight.WeightUnit.Lb -> "Lb"
            Weight.WeightUnit.Kg -> "Kg"
        }
}
