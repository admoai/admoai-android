@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.admoai.sdk.model.request

import com.admoai.sdk.exception.AdMoaiConfigurationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builder for creating [DecisionRequest] instances.
 * 
 * @see DecisionRequest
 */

class DecisionRequestBuilder {
    private var placements: MutableList<Placement> = mutableListOf()
    private var _targeting: Targeting = Targeting()
    private var _user: User = User()
    private var _collectAppData: Boolean = true
    private var _collectDeviceData: Boolean = true 

    /**
     * Disables the collection of app-related data for this specific request.
     */
    fun disableAppCollection(): DecisionRequestBuilder {
        this._collectAppData = false
        return this
    }

    /**
     * Disables the collection of device-related data for this specific request.
     */
    fun disableDeviceCollection(): DecisionRequestBuilder {
        this._collectDeviceData = false
        return this
    }

    // --- Placement Methods ---

    /**
     * Adds a placement to the request.
     * @param key The unique key for the placement.
     * @param count The number of ads requested for this placement (optional).
     * @param format The format of the placement (optional, defaults to NATIVE).
     * @param advertiserId Specific advertiser ID to target for this placement (optional).
     * @param templateId Specific template ID to target for this placement (optional).
     */
    fun addPlacement(
        key: String,
        count: Int? = null,
        format: PlacementFormat? = null, // Corrected: added format parameter
        advertiserId: String? = null,
        templateId: String? = null
    ): DecisionRequestBuilder {
        this.placements.add(Placement( // Corrected: using named arguments
            key = key,
            count = count,
            format = format,
            advertiserId = advertiserId,
            templateId = templateId
        ))
        return this
    }

    /**
     * Adds a pre-configured [Placement] object.
     */
    fun addPlacement(placement: Placement): DecisionRequestBuilder {
        this.placements.add(placement)
        return this
    }

    /**
     * Sets all placements, replacing any previously added ones.
     */
    fun setPlacements(placements: List<Placement>): DecisionRequestBuilder {
        this.placements = placements.toMutableList()
        return this
    }

    // --- Targeting Methods ---

    /** Adds a geo targeting ID. */
    fun addGeoTarget(id: Int): DecisionRequestBuilder {
        val currentGeo = _targeting.geo?.toMutableList() ?: mutableListOf()
        currentGeo.add(id)
        _targeting = _targeting.copy(geo = currentGeo.distinct()) // Ensure distinct IDs
        return this
    }

    /** Sets all geo targeting IDs, replacing previous ones. */
    fun setGeoTargets(ids: List<Int>): DecisionRequestBuilder {
        _targeting = _targeting.copy(geo = ids.distinct())
        return this
    }

    /** Adds location targeting information. */
    fun addLocationTarget(latitude: Double, longitude: Double): DecisionRequestBuilder {
        val currentLocation = _targeting.location?.toMutableList() ?: mutableListOf()
        currentLocation.add(LocationTargetingInfo(latitude, longitude))
        _targeting = _targeting.copy(location = currentLocation)
        return this
    }

    /** Sets all location targeting information, replacing previous ones. */
    fun setLocationTargets(locations: List<LocationTargetingInfo>): DecisionRequestBuilder {
        _targeting = _targeting.copy(location = locations)
        return this
    }

    /** Adds a custom targeting parameter with a JsonElement value. */
    fun addCustomTarget(key: String, value: JsonElement): DecisionRequestBuilder {
        val currentCustom = _targeting.custom?.toMutableList() ?: mutableListOf()
        // Remove existing with same key to effectively update if key exists
        currentCustom.removeAll { it.key == key }
        currentCustom.add(CustomTargetingInfo(key, value))
        _targeting = _targeting.copy(custom = currentCustom)
        return this
    }

    /** Convenience method to add custom targeting with a String value. */
    fun addCustomTarget(key: String, value: String): DecisionRequestBuilder {
        return addCustomTarget(key, JsonPrimitive(value))
    }

    /** Convenience method to add custom targeting with a Number value. */
    fun addCustomTarget(key: String, value: Number): DecisionRequestBuilder {
        return addCustomTarget(key, JsonPrimitive(value))
    }

    /** Convenience method to add custom targeting with a Boolean value. */
    fun addCustomTarget(key: String, value: Boolean): DecisionRequestBuilder {
        return addCustomTarget(key, JsonPrimitive(value))
    }

    /** Sets all custom targeting parameters, replacing previous ones. */
    fun setCustomTargets(customTargets: List<CustomTargetingInfo>): DecisionRequestBuilder {
        _targeting = _targeting.copy(custom = customTargets)
        return this
    }

    // --- User Methods ---

    /** Sets the end-user ID. */
    fun setUserId(id: String?): DecisionRequestBuilder {
        _user = _user.copy(id = id)
        return this
    }

    /** Sets the end-user IP address. */
    fun setUserIp(ip: String?): DecisionRequestBuilder {
        _user = _user.copy(ip = ip)
        return this
    }

    /** Sets the end-user timezone. */
    fun setUserTimezone(timezone: String?): DecisionRequestBuilder {
        _user = _user.copy(timezone = timezone)
        return this
    }

    /** Sets the user's GDPR consent status. */
    fun setUserConsent(gdpr: Boolean?): DecisionRequestBuilder {
        _user = _user.copy(consent = Consent(gdpr = gdpr))
        return this
    }

    /** Sets the user's consent information using a [Consent] object. */
    fun setUserConsent(consent: Consent?): DecisionRequestBuilder {
        _user = _user.copy(consent = consent)
        return this
    }

    // --- Build Method ---

    /**
     * Constructs the [DecisionRequest] object.
     * @throws AdMoaiConfigurationException if no placements have been added.
     */
    fun build(): DecisionRequest {
        if (placements.isEmpty()) {
            throw AdMoaiConfigurationException("At least one placement is required for DecisionRequest.")
        }

        // Only include targeting if any of its fields are non-null/non-empty
        val finalTargeting = if (_targeting.geo.isNullOrEmpty() &&
            _targeting.location.isNullOrEmpty() &&
            _targeting.custom.isNullOrEmpty()) {
            null
        } else {
            _targeting
        }

        // Only include user if any of its fields are non-null
        val finalUser = if (_user.id == null &&
            _user.ip == null &&
            _user.timezone == null &&
            _user.consent == null) { // Or _user.consent?.gdpr == null if Consent only has gdpr
            null
        } else {
            _user
        }

        return DecisionRequest(
            placements = this.placements.toList(), // Ensure immutable list
            targeting = finalTargeting,
            user = finalUser,
            collectAppData = this._collectAppData,      
            collectDeviceData = this._collectDeviceData
        )
    }
}
