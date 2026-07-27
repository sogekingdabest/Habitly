package com.monsteraltech.habitly.navigation

/**
 * Represents external navigation requests (push notifications or launcher shortcuts).
 */
enum class ExternalDestination {
    /** Routines tab: reminder notification or "Today's Routines" launcher shortcut. */
    ROUTINES,

    /** Shopping list tab with quick-add modal open: "Add to Shopping" launcher shortcut. */
    SHOPPING_QUICK_ADD;

    companion object {
        /** Action for routines shortcut (`res/xml/shortcuts.xml`). */
        const val ACTION_VIEW_ROUTINES = "com.monsteraltech.habitly.action.VIEW_ROUTINES"

        /** Action for quick-add shopping shortcut (`res/xml/shortcuts.xml`). */
        const val ACTION_ADD_SHOPPING = "com.monsteraltech.habitly.action.ADD_SHOPPING"

        /** Resolves ExternalDestination from intent action string. */
        fun fromAction(action: String?): ExternalDestination? = when (action) {
            ACTION_VIEW_ROUTINES -> ROUTINES
            ACTION_ADD_SHOPPING -> SHOPPING_QUICK_ADD
            else -> null
        }
    }
}
