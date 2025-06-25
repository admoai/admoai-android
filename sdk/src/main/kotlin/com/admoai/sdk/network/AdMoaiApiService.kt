package com.admoai.sdk.network

import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.response.DecisionResponse
import kotlinx.coroutines.flow.Flow

/**
 * Interface for interacting with the AdMoai backend API.
 */
interface AdMoaiApiService {
    /**
     * Asynchronously requests ads from the decision engine.
     * @param request The [DecisionRequest] object containing all request parameters.
     * @return A [Flow] emitting a single [DecisionResponse].
     *         The flow will emit an error if the request fails.
     */
    fun requestAds(request: DecisionRequest): Flow<DecisionResponse>

    /**
     * Asynchronously fires a tracking URL.
     * Typically used for impressions, clicks, or custom events.
     * @param url The absolute URL to be tracked.
     * @return A [Flow] emitting [Unit] upon successful dispatch (e.g., HTTP 2xx response).
     *         The flow will emit an error if the request fails.
     */
    fun fireTrackingUrl(url: String): Flow<Unit>

    /**
     * Constructs the [AdmoaiHttpRequest] data for a given [DecisionRequest] without sending it.
     * Useful for debugging and inspecting the request details.
     * @param request The [DecisionRequest] object.
     * @return An [AdmoaiHttpRequest] object representing the HTTP request.
     */
    fun getHttpRequestData(request: DecisionRequest): AdmoaiHttpRequest
}
