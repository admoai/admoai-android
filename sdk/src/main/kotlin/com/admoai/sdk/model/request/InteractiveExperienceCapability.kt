package com.admoai.sdk.model.request

import kotlinx.serialization.Serializable

/**
 * The Lead Generation support a decision request declares. Set by the SDK from the app's opt-in,
 * never by hand: whatever a built request carries here is replaced when it is sent.
 *
 * @property supported Whether this app and device can open a Lead Generation form
 * @property availableAttributeKeys Names of the standard attributes the app holds (keys only, never values)
 */
@Serializable
data class InteractiveExperienceCapability(val supported: Boolean, val availableAttributeKeys: List<String>)
