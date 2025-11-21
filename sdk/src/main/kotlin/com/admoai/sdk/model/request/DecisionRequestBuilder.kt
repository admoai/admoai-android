@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.admoai.sdk.model.request

import com.admoai.sdk.exception.AdMoaiConfigurationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class DecisionRequestBuilder {
    private var placements: MutableList<Placement> = mutableListOf()
    private var _targeting: Targeting = Targeting()
    private var _user: User = User()
    private var _collectAppData: Boolean = true
    private var _collectDeviceData: Boolean = true 

    fun disableAppCollection(): DecisionRequestBuilder {
        this._collectAppData = false
        return this
    }

    fun disableDeviceCollection(): DecisionRequestBuilder {
        this._collectDeviceData = false
        return this
    }

    fun addPlacement(
        key: String,
        count: Int? = null,
        format: PlacementFormat? = null,
        advertiserId: String? = null,
        templateId: String? = null
    ): DecisionRequestBuilder {
        this.placements.add(Placement(
            key = key,
            count = count,
            format = format,
            advertiserId = advertiserId,
            templateId = templateId
        ))
        return this
    }

    fun addPlacement(placement: Placement): DecisionRequestBuilder {
        this.placements.add(placement)
        return this
    }

    fun setPlacements(placements: List<Placement>): DecisionRequestBuilder {
        this.placements = placements.toMutableList()
        return this
    }

    fun addGeoTarget(id: Int): DecisionRequestBuilder {
        val currentGeo = _targeting.geo?.toMutableList() ?: mutableListOf()
        currentGeo.add(id)
        _targeting = _targeting.copy(geo = currentGeo.distinct())
        return this
    }

    fun setGeoTargets(ids: List<Int>): DecisionRequestBuilder {
        _targeting = _targeting.copy(geo = ids.distinct())
        return this
    }

    fun addLocationTarget(latitude: Double, longitude: Double): DecisionRequestBuilder {
        val currentLocation = _targeting.location?.toMutableList() ?: mutableListOf()
        currentLocation.add(LocationTargetingInfo(latitude, longitude))
        _targeting = _targeting.copy(location = currentLocation)
        return this
    }

    fun setLocationTargets(locations: List<LocationTargetingInfo>): DecisionRequestBuilder {
        _targeting = _targeting.copy(location = locations)
        return this
    }

    fun addCustomTarget(key: String, value: JsonElement): DecisionRequestBuilder {
        val currentCustom = _targeting.custom?.toMutableList() ?: mutableListOf()
        currentCustom.removeAll { it.key == key }
        currentCustom.add(CustomTargetingInfo(key, value))
        _targeting = _targeting.copy(custom = currentCustom)
        return this
    }

    fun addCustomTarget(key: String, value: String): DecisionRequestBuilder {
        return addCustomTarget(key, JsonPrimitive(value))
    }

    fun addCustomTarget(key: String, value: Number): DecisionRequestBuilder {
        return addCustomTarget(key, JsonPrimitive(value))
    }

    fun addCustomTarget(key: String, value: Boolean): DecisionRequestBuilder {
        return addCustomTarget(key, JsonPrimitive(value))
    }

    fun setCustomTargets(customTargets: List<CustomTargetingInfo>): DecisionRequestBuilder {
        _targeting = _targeting.copy(custom = customTargets)
        return this
    }

    fun setUserId(id: String?): DecisionRequestBuilder {
        _user = _user.copy(id = id)
        return this
    }

    fun setUserIp(ip: String?): DecisionRequestBuilder {
        _user = _user.copy(ip = ip)
        return this
    }

    fun setUserTimezone(timezone: String?): DecisionRequestBuilder {
        _user = _user.copy(timezone = timezone)
        return this
    }

    fun setUserConsent(gdpr: Boolean?): DecisionRequestBuilder {
        _user = _user.copy(consent = Consent(gdpr = gdpr))
        return this
    }

    fun setUserConsent(consent: Consent?): DecisionRequestBuilder {
        _user = _user.copy(consent = consent)
        return this
    }

    fun build(): DecisionRequest {
        if (placements.isEmpty()) {
            throw AdMoaiConfigurationException("At least one placement is required")
        }

        val finalTargeting = if (_targeting.geo.isNullOrEmpty() &&
            _targeting.location.isNullOrEmpty() &&
            _targeting.custom.isNullOrEmpty()) {
            null
        } else {
            _targeting
        }

        val finalUser = if (_user.id == null &&
            _user.ip == null &&
            _user.timezone == null &&
            _user.consent == null) {
            null
        } else {
            _user
        }

        return DecisionRequest(
            placements = this.placements.toList(),
            targeting = finalTargeting,
            user = finalUser,
            collectAppData = this._collectAppData,      
            collectDeviceData = this._collectDeviceData
        )
    }
}
