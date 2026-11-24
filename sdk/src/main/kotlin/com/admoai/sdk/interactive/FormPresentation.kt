package com.admoai.sdk.interactive

/**
 * How the app shows a Lead Generation form. The form draws its own close button only on
 * [FULL_SCREEN]; on [INLINE] closing is left to the app's own card.
 */
enum class FormPresentation(internal val wire: String) {
    /** The form sits inside the app's own card or screen. */
    INLINE("inline"),

    /** The form fills the screen and shows its own close button. */
    FULL_SCREEN("full_screen")
}
