package com.stratum.domain.lift

/** User-facing message when an equipment name is blank (STR-169: the one name rule). */
const val EQUIPMENT_NAME_REQUIRED_MESSAGE: String = "Equipment name is required"

/** User-facing message when a lift name is blank (STR-169: the one name rule). */
const val LIFT_NAME_REQUIRED_MESSAGE: String = "Lift name is required"

/**
 * The catalog name rule, owned once on the domain side (STR-169).
 *
 * A catalog name is its trimmed form; a name that is blank after trimming is
 * invalid, with the user-facing message the editors and use cases surface.
 * Every equipment/lift name write path (rename, rename-and-set-tiers, add,
 * find-or-create, fork, proposal remedies) and the equipment editor's draft
 * validation consume this object instead of re-implementing trim/blank checks.
 */
object CatalogNames {
    /** Outcome of applying the name rule to a raw user-entered name. */
    sealed interface Validation {
        /** The name is usable; [name] is the trimmed form to persist/display. */
        data class Valid(val name: String) : Validation

        /** The name is unusable; [message] is the user-facing reason. */
        data class Invalid(val message: String) : Validation
    }

    fun validateEquipmentName(raw: String?): Validation = validate(raw, EQUIPMENT_NAME_REQUIRED_MESSAGE)

    fun validateLiftName(raw: String?): Validation = validate(raw, LIFT_NAME_REQUIRED_MESSAGE)

    /** The equipment name rule as a [Result] of the trimmed name, for use-case call sites. */
    fun equipmentNameResult(raw: String?): Result<String> = resultOf(validateEquipmentName(raw))

    /** The lift name rule as a [Result] of the trimmed name, for use-case call sites. */
    fun liftNameResult(raw: String?): Result<String> = resultOf(validateLiftName(raw))

    /**
     * The user-facing message for a raw equipment name, or null when the name is
     * valid. Editors call this for a draft they know has been touched; whether a
     * draft exists at all is the editor's own state, not this rule's concern.
     */
    fun equipmentValidationMessage(raw: String): String? =
        (validateEquipmentName(raw) as? Validation.Invalid)?.message

    /**
     * Optional-name form of the equipment rule: blank input means "no equipment
     * name" (null), otherwise the trimmed name. Used where the name is free-text
     * attached to another entity rather than the entity's own name.
     */
    fun normalizedEquipmentNameOrNull(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun validate(raw: String?, blankMessage: String): Validation {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.isEmpty()) {
            Validation.Invalid(blankMessage)
        } else {
            Validation.Valid(trimmed)
        }
    }

    private fun resultOf(validation: Validation): Result<String> =
        when (validation) {
            is Validation.Valid -> Result.success(validation.name)
            is Validation.Invalid -> Result.failure(IllegalArgumentException(validation.message))
        }
}
