package com.admoai.sdk.interactive

/** Why the form asks the app to open a link. */
@ExperimentalInteractiveExperienceApi
enum class ExternalLinkPurpose(internal val wire: String) {
    /** The advertiser's privacy policy; available any time the form is visible. */
    PRIVACY_POLICY("privacy_policy"),

    /** The advertiser's website; offered only after the lead was submitted. */
    WEBSITE_CTA("website_cta")
}

/**
 * The app's side of a [LeadFormController]: what the form cannot do by itself. Callbacks arrive on
 * the thread that delivered the form's message, which is the main thread for the SDK's own
 * WebView transport.
 */
@ExperimentalInteractiveExperienceApi
interface LeadFormHost {
    /** The form asks to be closed. Remove its view and call [LeadFormController.dispose]. */
    fun onRequestClose(controller: LeadFormController)

    /**
     * The form asks the app to open [url] for [purpose]. Open it in a separate browser surface (a
     * Custom Tab), never inside the form, and without adding anything to the URL: no token, no
     * form value, no identifier.
     */
    fun onOpenExternalLink(controller: LeadFormController, purpose: ExternalLinkPurpose, url: String)
}
