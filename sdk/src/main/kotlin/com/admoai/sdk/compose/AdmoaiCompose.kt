package com.admoai.sdk.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admoai.sdk.Admoai
import com.admoai.sdk.exception.AdMoaiConfigurationException
import com.admoai.sdk.exception.AdMoaiValidationException
import com.admoai.sdk.model.request.DecisionRequest
import com.admoai.sdk.model.response.DecisionResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/**
 * State of an ad request within a Jetpack Compose UI.
 */
sealed class AdState {
    /** Initial state before any ad request is made. */
    object Idle : AdState()

    /** Ad request is currently in progress. */
    object Loading : AdState()

    /**
     * Ad request has successfully completed.
     * @param response The [DecisionResponse] received from the Admoai SDK.
     */
    data class Success(val response: DecisionResponse) : AdState()

    /**
     * Ad request has failed.
     * @param exception The [Exception] that occurred during the ad request.
     */
    data class Error(val exception: Exception) : AdState()
}

/**
 * Remember and manage the state of an Admoai ad request.
 *
 * @param admoai An instance of the [Admoai] SDK.
 * @param decisionRequest The [DecisionRequest] to be sent.
 * @param key An optional key to control recomposition and effect launching.
 *            If the key changes, the ad request will be re-launched. Defaults to [decisionRequest].
 * @return The current [AdState] of the ad request.
 */
@Composable
fun rememberAdState(
    admoai: Admoai,
    decisionRequest: DecisionRequest,
    key: Any = decisionRequest
): AdState {
    // Create a Flow that encapsulates the request logic and state transitions
    val adRequestFlow = remember(key) {
        createAdRequestFlow(admoai, decisionRequest)
    }
    
    // Collect the flow with lifecycle awareness
    val adState by adRequestFlow.collectAsStateWithLifecycle(initialValue = AdState.Idle)
    
    return adState
}

/**
 * Creates a Flow that handles the ad request and state transitions.
 * 
 * @param admoai The Admoai SDK instance
 * @param decisionRequest The request to send
 * @return Flow emitting AdState values representing the current state of the request
 */
private fun createAdRequestFlow(admoai: Admoai, decisionRequest: DecisionRequest): Flow<AdState> = flow {
    // Initial validation
    if (!Admoai.isInitialized()) {
        emit(AdState.Error(AdMoaiConfigurationException("Admoai SDK not initialized. Call Admoai.initialize() first.")))
        return@flow
    }
    
    if (decisionRequest.placements.isNullOrEmpty()) {
        emit(AdState.Error(AdMoaiValidationException("DecisionRequest must contain at least one placement.")))
        return@flow
    }
    
    // Begin the actual request
    emit(AdState.Loading)
    
    // Collect and transform the original Flow
    admoai.requestAds(decisionRequest)
        .catch { exception ->
            emit(AdState.Error(exception as? Exception ?: RuntimeException("Unknown error during ad request", exception)))
        }
        .collect { response ->
            emit(AdState.Success(response))
        }
}
