package br.com.taparelli.bigword

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import kotlin.math.roundToInt

internal fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

@Suppress("DEPRECATION")
internal fun Activity.configureWindow(root: View, immersive: Boolean) {
    if (Build.VERSION.SDK_INT >= 30) {
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.windowInsetsController?.apply {
                    setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    )
                    if (immersive) {
                        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsets.Type.systemBars())
                    }
                }
                view.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    } else {
        if (immersive) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        window.statusBarColor = 0xff26626d.toInt()
        window.navigationBarColor = 0xff134e5c.toInt()
    }
    root.setOnApplyWindowInsetsListener { view, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val safe = if (immersive) {
                // A swipe can reveal the bars briefly without moving the message.
                insets.getInsets(WindowInsets.Type.displayCutout() or WindowInsets.Type.captionBar())
            } else {
                insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            }
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
        } else if (immersive) {
            if (Build.VERSION.SDK_INT >= 28) {
                insets.displayCutout?.let { cutout ->
                    view.setPadding(cutout.safeInsetLeft, cutout.safeInsetTop,
                        cutout.safeInsetRight, cutout.safeInsetBottom)
                } ?: view.setPadding(0, 0, 0, 0)
            }
        } else {
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.stableInsetBottom)
        }
        insets
    }
    setContentView(root)
    root.requestApplyInsets()
}
