package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * User consent information for privacy regulations.
 *
 * @property gdpr GDPR consent status. Defaults to `false` (not granted), matching the engine,
 *   which types the field as a non-nullable bool defaulting to false — there is no "unspecified"
 *   state to express. It was nullable here, so a consent object with no explicit value serialized
 *   as `{}` while iOS and Flutter always emitted `{"gdpr": false}` for the same input: the same
 *   publisher call produced a different request body per platform, for identical engine behaviour.
 */
@Serializable
data class Consent(
    val gdpr: Boolean = false
)
