package com.osfans.trime.util

import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout

/** (This Kotlin file has been taken from florisboard project)
 * This file has been taken from the Android LatinIME project. Following modifications were done by
 * florisboard to the original source code:
 * - Convert the code from Java to Kotlin
 * - Change package name to match the current project's one
 * - Remove method newLayoutParam()
 * - Remove method placeViewAt()
 * - Remove unnecessary variable params in updateLayoutGravityOf(), lp can directly be used due to
 *    Kotlin's smart cast feature
 * - Remove unused imports
 *
 * The original source code can be found at the following location:
 *  https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/master/java/src/com/android/inputmethod/latin/utils/ViewLayoutUtils.java
 */
object ViewUtils {
    @JvmStatic
    fun updateLayoutHeightOf(window: Window, layoutHeight: Int) {
        val params = window.attributes
        if (params != null && params.height != layoutHeight) {
            params.height = layoutHeight
            window.attributes = params
        }
    }

    @JvmStatic
    fun updateLayoutHeightOf(view: View, layoutHeight: Int) {
        val params = view.layoutParams
        if (params != null && params.height != layoutHeight) {
            params.height = layoutHeight
            view.layoutParams = params
        }
    }

    @JvmStatic
    fun updateLayoutGravityOf(view: View, layoutGravity: Int) {
        val lp = view.layoutParams
        if (lp is LinearLayout.LayoutParams) {
            if (lp.gravity != layoutGravity) {
                lp.gravity = layoutGravity
                view.layoutParams = lp
            }
        } else if (lp is FrameLayout.LayoutParams) {
            if (lp.gravity != layoutGravity) {
                lp.gravity = layoutGravity
                view.layoutParams = lp
            }
        } else {
            throw IllegalArgumentException(
                "Layout parameter doesn't have gravity: " +
                    lp.javaClass.name
            )
        }
    }
}
