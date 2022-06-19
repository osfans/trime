/**
 * Adapted from [fcitx5-android/Logview.kt](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/ui/common/LogView.kt)
 */
package com.osfans.trime.settings.components

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ColorUtils
import com.blankj.utilcode.util.SizeUtils
import com.osfans.trime.R
import com.osfans.trime.util.Logcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LogView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null) :
    NestedScrollView(context, attributeSet) {

    private var logcat: Logcat? = null

    private val textView = TextView(context, attributeSet).apply {
        setPadding(SizeUtils.dp2px(4F))
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTextClassifier(object : TextClassifier {})
        }
    }

    private val scrollView = HorizontalScrollView(context, attributeSet).apply {
        addView(
            textView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    init {
        addView(
            scrollView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onDetachedFromWindow() {
        logcat?.shutdownLogFlow()
        super.onDetachedFromWindow()
    }

    fun setLogcat(logcat: Logcat) {
        this.logcat = logcat
        logcat.initLogFlow()
        logcat.logFlow.onEach {
            val color = ColorUtils.getColor(
                when (it.first()) {
                    'V' -> R.color.grey_700
                    'D' -> R.color.grey_700
                    'I' -> R.color.blue_500
                    'W' -> R.color.yellow_800
                    'E' -> R.color.red_400
                    'F' -> R.color.red_A700
                    else -> R.color.colorPrimary
                }
            )
            val colored = SpannableString(it).apply {
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    it.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            textView.append(colored)
            textView.append("\n")
        }.launchIn(findViewTreeLifecycleOwner()!!.lifecycleScope)
    }

    val currentLog: CharSequence
        get() = textView.text ?: ""

    fun clear() {
        textView.text = ""
    }
}
