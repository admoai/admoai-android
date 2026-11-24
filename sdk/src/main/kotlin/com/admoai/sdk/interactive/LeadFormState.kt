package com.admoai.sdk.interactive

/** Where a [LeadFormController] is in the life of one form. */
sealed class LeadFormState {
    /** Nothing was loaded yet. */
    data object Idle : LeadFormState()

    /** The form page is loading and preparing in the background. */
    data object Preparing : LeadFormState()

    /** The form is prepared and waits for the viewer's tap. */
    data object Prepared : LeadFormState()

    /** The form is being activated and its prefill applied. */
    data object Activating : LeadFormState()

    /** The form is ready to be shown. Report it with [LeadFormController.setVisible]. */
    data object ReadyToShow : LeadFormState()

    /** The app reported the form on screen. */
    data object Visible : LeadFormState()

    /** The viewer submitted and the form waits for the outcome. */
    data object Submitting : LeadFormState()

    /** The lead was accepted. */
    class Submitted(val leadId: String, val submittedAt: String) : LeadFormState() {
        override fun equals(other: Any?): Boolean =
            other is Submitted && leadId == other.leadId && submittedAt == other.submittedAt

        override fun hashCode(): Int = 31 * leadId.hashCode() + submittedAt.hashCode()

        override fun toString(): String = "Submitted(leadId=$leadId, submittedAt=$submittedAt)"
    }

    /** The preparation passed its expiry. The controller never opens. */
    data object Expired : LeadFormState()

    /** The form failed with [errorCode]. When [retryable], [LeadFormController.retry] tries again. */
    class Failed(val errorCode: String, val retryable: Boolean) : LeadFormState() {
        override fun equals(other: Any?): Boolean =
            other is Failed && errorCode == other.errorCode && retryable == other.retryable

        override fun hashCode(): Int = 31 * errorCode.hashCode() + retryable.hashCode()

        override fun toString(): String = "Failed(errorCode=$errorCode, retryable=$retryable)"
    }

    /** The controller was disposed. Every call and message is ignored. */
    data object Disposed : LeadFormState()
}
