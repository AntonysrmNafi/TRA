package com.blockveil.tracker.remover.util

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the system status bar for a fully immersive screen — no clock,
 * network, or battery icons while using the app. A swipe down from the
 * top edge briefly reveals it again (standard Android "swipe" behavior),
 * rather than requiring a special gesture to get it back.
 */
fun Activity.hideStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.statusBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
