package com.admoai.sdk.model.request

import com.admoai.sdk.exception.AdMoaiConfigurationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class DecisionRequestBuilder {
    private val placements: MutableList<Placement> = mutableListOf()
    private var targeting: Targeting = Targeting()
    private var user: User = User()
    private var collectAppData: Boolean = true
    private var collectDeviceData: Boolean = true 

    fun disableAppCollection() = apply { collectAppData = false }

    fun disableDeviceCollection() = apply { collectDeviceData = false }

    fun addPlacement(
        key: String,
        count: Int? = null,
        format: PlacementFormat? = null,
        advertiserId: String? = null,
        templateId: String? = null
    ) = apply {
        placements.add(Placement(key, count, format, advertiserId, templateId))
    }

    fun addPlacement(placement: Placement) = apply { placements.add(placement) }

    fun setPlacements(placements: List<Placement>) = apply {
        this.placements.clear()
        this.placements.addAll(placements)
    }

    fun addGeoTarget(id: Int) = apply {
        val currentGeo = targeting.geo?.toMutableList() ?: mutableListOf()
        currentGeo.add(id)
        targeting = targeting.copy(geo = currentGeo.distinct())
    }

    fun setGeoTargets(ids: List<Int>) = apply {
        targeting = targeting.copy(geo = ids.distinct())
    }

    fun addLocationTarget(latitude: Double, longitude: Double) = apply {
        val currentLocation = targeting.location?.toMutableList() ?: mutableListOf()
        currentLocation.add(LocationTargetingInfo(latitude, longitude))
        targeting = targeting.copy(location = currentLocation)
    }

    fun setLocationTargets(locations: List<LocationTargetingInfo>) = apply {
        targeting = targeting.copy(location = locations)
    }

    fun addDestinationTarget(latitude: Double, longitude: Double, minConfidence: Double) = apply {
        val currentDestination = targeting.destination?.toMutableList() ?: mutableListOf()
        currentDestination.add(DestinationTargetingInfo(latitude, longitude, minConfidence))
        targeting = targeting.copy(destination = currentDestination)
    }

    fun setDestinationTargets(destinations: List<DestinationTargetingInfo>) = apply {
        targeting = targeting.copy(destination = destinations)
    }

    fun addCustomTarget(key: String, value: JsonElement) = apply {
        val currentCustom = targeting.custom?.toMutableList() ?: mutableListOf()
        currentCustom.removeAll { it.key == key }
        currentCustom.add(CustomTargetingInfo(key, value))
        targeting = targeting.copy(custom = currentCustom)
    }

    fun addCustomTarget(key: String, value: String) = addCustomTarget(key, JsonPrimitive(value))

    fun addCustomTarget(key: String, value: Number) = addCustomTarget(key, JsonPrimitive(value))

    fun addCustomTarget(key: String, value: Boolean) = addCustomTarget(key, JsonPrimitive(value))

    fun setCustomTargets(customTargets: List<CustomTargetingInfo>) = apply {
        targeting = targeting.copy(custom = customTargets)
    }

    fun setUserId(id: String?) = apply { user = user.copy(id = id) }

    fun setUserIp(ip: String?) = apply { user = user.copy(ip = ip) }

    fun setUserTimezone(timezone: String?) = apply { user = user.copy(timezone = timezone) }

    fun setUserConsent(gdpr: Boolean?) = apply { user = user.copy(consent = Consent(gdpr = gdpr)) }

    fun setUserConsent(consent: Consent?) = apply { user = user.copy(consent = consent) }

    fun build(): DecisionRequest {
        if (placements.isEmpty()) {
            throw AdMoaiConfigurationException("At least one placement is required")
        }

        val finalTargeting = targeting.takeIf {
            !it.geo.isNullOrEmpty() || !it.location.isNullOrEmpty() || !it.destination.isNullOrEmpty() || !it.custom.isNullOrEmpty()
        }

        val finalUser = user.takeIf {
            it.id != null || it.ip != null || it.timezone != null || it.consent != null
        }

        return DecisionRequest(
            placements = placements.toList(),
            targeting = finalTargeting,
            user = finalUser,
            collectAppData = collectAppData,
            collectDeviceData = collectDeviceData
        )
    }
}
