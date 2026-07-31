package com.monsteraltech.habitly.feature.widget.domain

/**
 * Tells the home-screen widget its data has changed.
 *
 * It is an interface so the data layers do not depend on Glance or drag a `Context` into the tests:
 * in tests, binding nothing is enough.
 */
interface WidgetRefresher {
    /**
     * Repaints every widget instance. Non-blocking: it runs in the background and swallows
     * failures, because refreshing the widget must never take down the write that triggered it.
     */
    fun refresh()
}
