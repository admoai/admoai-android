package com.admoai.sdk.model.response

import com.admoai.sdk.interactive.StandardAttributeKey

/**
 * What a creative opens when tapped, when it is more than a link. Its version and prefill keys are
 * provisional: the form's version is fixed only when the viewer opens it.
 */
sealed class InteractiveExperience {
    /** A Lead Generation form. */
    class LeadGeneration internal constructor(
        val formId: String,
        val formInstanceId: String,
        val preparedVersionId: String,
        val expiresAtEpochMillis: Long,
        val prefillAttributeKeys: List<StandardAttributeKey>,
        internal val launchUrl: String,
        internal val preparationToken: String
    ) : InteractiveExperience() {
        override fun equals(other: Any?): Boolean = other is LeadGeneration &&
            formId == other.formId &&
            formInstanceId == other.formInstanceId &&
            preparedVersionId == other.preparedVersionId &&
            expiresAtEpochMillis == other.expiresAtEpochMillis &&
            prefillAttributeKeys == other.prefillAttributeKeys &&
            launchUrl == other.launchUrl &&
            preparationToken == other.preparationToken

        override fun hashCode(): Int {
            var h = formId.hashCode()
            h = 31 * h + formInstanceId.hashCode()
            h = 31 * h + preparedVersionId.hashCode()
            h = 31 * h + expiresAtEpochMillis.hashCode()
            h = 31 * h + prefillAttributeKeys.hashCode()
            h = 31 * h + launchUrl.hashCode()
            h = 31 * h + preparationToken.hashCode()
            return h
        }

        override fun toString(): String = "LeadGeneration(formId=$formId, formInstanceId=$formInstanceId, " +
            "preparedVersionId=$preparedVersionId, expiresAtEpochMillis=$expiresAtEpochMillis, " +
            "prefillAttributeKeys=$prefillAttributeKeys, preparationToken=REDACTED)"
    }

    /**
     * The creative needs an experience this SDK cannot open (an unknown [type], or a descriptor that
     * is malformed). Do not render that creative: a tap would lead nowhere.
     */
    class Unsupported internal constructor(val type: String?) : InteractiveExperience() {
        override fun equals(other: Any?): Boolean = other is Unsupported && type == other.type

        override fun hashCode(): Int = type.hashCode()

        override fun toString(): String = "Unsupported(type=$type)"
    }
}
