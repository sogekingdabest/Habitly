package com.monsteraltech.habitly.navigation

/**
 * A screen requested from outside the app: a reminder notification or a launcher shortcut.
 *
 * `MainActivity` sets it when the intent arrives, `MainContent` navigates and reports it as
 * consumed. Single channel on purpose — one boolean per destination does not scale.
 */
enum class ExternalDestination {
    /** Routines tab: reminder notification and the "Today's routines" shortcut. */
    ROUTINES,

    /** Shopping list with the quick-add sheet open: the "Add to shopping" shortcut. */
    SHOPPING_QUICK_ADD;

    companion object {
        /** Action of the static routines shortcut (`res/xml/shortcuts.xml`). */
        const val ACTION_VIEW_ROUTINES = "com.monsteraltech.habitly.action.VIEW_ROUTINES"

        /** Action of the static quick-add shortcut (`res/xml/shortcuts.xml`). */
        const val ACTION_ADD_SHOPPING = "com.monsteraltech.habitly.action.ADD_SHOPPING"

        /** Destination matching a shortcut action, or null if the action is not a shortcut. */
        fun fromAction(action: String?): ExternalDestination? = when (action) {
            ACTION_VIEW_ROUTINES -> ROUTINES
            ACTION_ADD_SHOPPING -> SHOPPING_QUICK_ADD
            else -> null
        }
    }
}
