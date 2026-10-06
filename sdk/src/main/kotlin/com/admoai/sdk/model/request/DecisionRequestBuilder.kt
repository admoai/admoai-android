package com.admoai.sdk.model.request

import com.admoai.sdk.exception.AdMoaiConfigurationException
import com.admoai.sdk.model.common.JourneyOpt
import com.admoai.sdk.model.common.normalizeSessionId
import com.admoai.sdk.model.common.sessionIdRejectionReason
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fluent builder for a [DecisionRequest].
 *
 * @param initialSessionId sticky session seed from the SDK instance (already publisher-provided).
 * @param onSessionRejected PII-safe callback invoked with a rejection-reason token (never the value)
 *   when [setSessionId] receives a blank or over-length id.
 */
class DecisionRequestBuilder internal constructor(
    initialSessionId: String?,
    private val onSessionRejected: ((String) -> Unit)?
) {
    /** Public constructor for direct/manual use (no config seeding, no logging callback). */
    constructor() : this(null, null)

    private val placements: MutableList<Placement> = mutableListOf()
    private var targeting: Targeting = Targeting()
    private var user: User = User()
    private var sessionId: String? = normalizeSessionId(initialSessionId)
    private var journeyOpt: JourneyOpt? = null
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
        val entry = LocationTargetingInfo(latitude, longitude)
        if (currentLocation.none { it.latitude == latitude && it.longitude == longitude }) {
            currentLocation.add(entry)
        }
        targeting = targeting.copy(location = currentLocation)
    }

    fun setLocationTargets(locations: List<LocationTargetingInfo>) = apply {
        val deduped = locations.distinctBy { Pair(it.latitude, it.longitude) }
        targeting = targeting.copy(location = deduped)
    }

    fun addDestinationTarget(latitude: Double, longitude: Double, minConfidence: Double) = apply {
        require(minConfidence in 0.0..1.0) {
            "minConfidence must be in [0.0, 1.0], was $minConfidence"
        }
        val currentDestination = targeting.destination?.toMutableList() ?: mutableListOf()
        if (currentDestination.none {
                it.latitude == latitude && it.longitude == longitude && it.minConfidence == minConfidence
            }) {
            currentDestination.add(DestinationTargetingInfo(latitude, longitude, minConfidence))
        }
        targeting = targeting.copy(destination = currentDestination)
    }

    fun setDestinationTargets(destinations: List<DestinationTargetingInfo>) = apply {
        destinations.forEach { entry ->
            require(entry.minConfidence in 0.0..1.0) {
                "minConfidence must be in [0.0, 1.0], was ${entry.minConfidence}"
            }
        }
        val deduped = destinations.distinctBy { Triple(it.latitude, it.longitude, it.minConfidence) }
        targeting = targeting.copy(destination = deduped)
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

    /**
     * Replaces the custom-targeting list, keeping the LAST entry for any repeated key.
     *
     * The dedupe is not cosmetic. The engine rejects a duplicate custom key with
     * `ErrDuplicateCustomKey` and returns immediately from validation, so a single repeated key
     * fails the ENTIRE decision request — every placement in it, not just the offending target.
     * `addCustomTarget` has always deduped; this bulk setter did not, so the two paths disagreed
     * and only the bulk one could produce that 422. iOS and Flutter both fold-dedupe here.
     */
    fun setCustomTargets(customTargets: List<CustomTargetingInfo>) = apply {
        val deduped = customTargets.fold(mutableListOf<CustomTargetingInfo>()) { acc, entry ->
            acc.removeAll { it.key == entry.key }
            acc.add(entry)
            acc
        }
        targeting = targeting.copy(custom = deduped)
    }

    fun setUserId(id: String?) = apply { user = user.copy(id = id) }

    fun setUserIp(ip: String?) = apply { user = user.copy(ip = ip) }

    fun setUserTimezone(timezone: String?) = apply { user = user.copy(timezone = timezone) }

    /**
     * Sets GDPR consent. A null argument means "not granted" (`false`) rather than "unspecified" —
     * the engine has no unspecified state and treats an absent flag as false, so this makes the
     * wire body identical to iOS and Flutter for the same call.
     */
    fun setUserConsent(gdpr: Boolean?) =
        apply { user = user.copy(consent = Consent(gdpr = gdpr ?: false)) }

    fun setUserConsent(consent: Consent?) = apply { user = user.copy(consent = consent) }

    /**
     * Sets the Journey session id for this request, overriding the sticky seed. Stored normalized
     * (trimmed; blank → null). Blank/over-length triggers a PII-safe warning but is still sent.
     */
    fun setSessionId(sessionId: String?) = apply {
        sessionIdRejectionReason(sessionId)?.let { onSessionRejected?.invoke(it) }
        this.sessionId = normalizeSessionId(sessionId)
    }

    fun clearSessionId() = apply { this.sessionId = null }

    fun setJourneyOpt(journeyOpt: JourneyOpt?) = apply { this.journeyOpt = journeyOpt }

    fun clearJourneyOpt() = apply { this.journeyOpt = null }

    fun clearGeoTargeting() = apply {
        targeting = targeting.copy(geo = null)
    }

    fun clearLocationTargeting() = apply {
        targeting = targeting.copy(location = null)
    }

    fun clearDestinationTargeting() = apply {
        targeting = targeting.copy(destination = null)
    }

    fun clearCustomTargeting() = apply {
        targeting = targeting.copy(custom = null)
    }

    /**
     * Asks for the campaign's pins within [radiusMeters] of a point (Sponsored Pin Locations).
     *
     * This is a search the publisher is running, not a statement about where the viewer is —
     * see [Targeting.distance]. Rendering the pins that come back is the app's job; the SDK
     * draws nothing and never infers that a pin was seen.
     *
     * The radius *ceiling* is server policy and is deliberately not checked here: an SDK that
     * hard-codes it ships a client that refuses what a newer engine would accept.
     *
     * @param limit narrows the campaign's own cap on how many points come back. It can never
     *   widen it, and omitting it means "as many as the campaign allows".
     * @throws IllegalArgumentException on an impossible origin, a radius of zero or less, or a
     *   limit of zero or less.
     */
    fun setDistanceTargeting(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        limit: Int? = null
    ) = apply {
        requireDistanceOrigin(latitude, longitude)
        require(radiusMeters > 0) { "distance radius must be greater than 0 metres, was $radiusMeters" }
        requireDistanceLimit(limit)
        targeting = targeting.copy(
            distance = Distance(latitude, longitude, radius = radiusMeters, limit = limit)
        )
    }

    /**
     * Asks for the campaign's pins inside a rectangle, measured from a point.
     *
     * The origin stays required: "nearest first" needs somewhere to measure from, and the centre
     * of the rectangle is not necessarily where the viewer is. The diagonal *ceiling* is server
     * policy and is not checked here.
     *
     * @throws IllegalArgumentException on an impossible origin, a rectangle that encloses
     *   nothing or crosses the antimeridian, or a limit of zero or less.
     */
    fun setDistanceTargeting(
        latitude: Double,
        longitude: Double,
        bounds: DistanceBounds,
        limit: Int? = null
    ) = apply {
        requireDistanceOrigin(latitude, longitude)
        require(bounds.north in -90.0..90.0 && bounds.south in -90.0..90.0 &&
            bounds.east in -180.0..180.0 && bounds.west in -180.0..180.0) {
            "distance bounds latitudes must be in [-90, 90] and longitudes in [-180, 180]"
        }
        require(bounds.north > bounds.south) {
            "distance bounds require north greater than south"
        }
        // The engine refuses a rectangle crossing the antimeridian, so west < east is flat.
        require(bounds.west < bounds.east) {
            "distance bounds must not cross the antimeridian: west must be less than east"
        }
        requireDistanceLimit(limit)
        targeting = targeting.copy(
            distance = Distance(latitude, longitude, bounds = bounds, limit = limit)
        )
    }

    /** Removes a previously set Sponsored Pin search, leaving every other targeting axis alone. */
    fun clearDistanceTargeting() = apply {
        targeting = targeting.copy(distance = null)
    }

    private fun requireDistanceOrigin(latitude: Double, longitude: Double) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "distance latitude must be in [-90, 90] and longitude in [-180, 180], " +
                "was ($latitude, $longitude)"
        }
    }

    private fun requireDistanceLimit(limit: Int?) {
        if (limit != null) {
            require(limit > 0) { "distance limit must be greater than 0, was $limit" }
        }
    }

    fun clearTargeting() = apply {
        targeting = Targeting()
    }

    fun clearPlacements() = apply {
        placements.clear()
    }

    fun clearUser() = apply {
        user = User()
    }

    fun clearAll() = apply {
        clearPlacements()
        clearTargeting()
        clearUser()
        // Also stop automatic app/device collection, matching the iOS and Flutter SDKs. Without
        // these, `clearAll()` put a different request on the wire per platform: the same call left
        // Android still sending `app` and `device` while the other two sent neither.
        disableAppCollection()
        disableDeviceCollection()
        // Clear journeyOpt (a stale opt-out would change eligibility) but preserve the sticky sessionId.
        journeyOpt = null
    }

    fun build(): DecisionRequest {
        if (placements.isEmpty()) {
            throw AdMoaiConfigurationException("At least one placement is required")
        }

        // `distance` has to be in this test: a Sponsored Pin request often sets nothing else,
        // and omitting it here would drop the whole targeting object and with it the search.
        val finalTargeting = targeting.takeIf {
            !it.geo.isNullOrEmpty() || !it.location.isNullOrEmpty() || !it.destination.isNullOrEmpty() ||
                !it.custom.isNullOrEmpty() || it.distance != null
        }

        val finalUser = user.takeIf {
            it.id != null || it.ip != null || it.timezone != null || it.consent != null
        }

        return DecisionRequest(
            placements = placements.toList(),
            targeting = finalTargeting,
            user = finalUser,
            sessionId = sessionId,
            journeyOpt = journeyOpt,
            collectAppData = collectAppData,
            collectDeviceData = collectDeviceData
        )
    }
}
