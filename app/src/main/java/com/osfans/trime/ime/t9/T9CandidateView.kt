package com.osfans.trime.ime.t9

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.util.UnicodeVariantUtils
import com.osfans.trime.util.dp
import com.osfans.trime.util.sp

class T9CandidateView(
    context: Context,
    attrs: AttributeSet? = null,
    private val theme: Theme,
    private val keyboard: Keyboard,
) : FrameLayout(context, attrs) {

    var onItemSelected: ((T9InputController.PinYinToken) -> Unit)? = null

    private val scrollView = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = true
        clipChildren = true
        clipToOutline = true
    }

    private val itemContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private var tokens: List<T9InputController.PinYinToken> = emptyList()

    private val candidateBg by lazy {
        ColorManager.getDecorDrawable(
            "t9_candidate_back_color",
            "t9_candidate_border_color",
            borderWidthPx,
            cornerRadiusPx,
        ) ?: GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx, candidateBorderColor)
            }
            cornerRadius = cornerRadiusPx
        }
    }

    private val candidateTextColor: Int by lazy {
        resolveColor("t9_candidate_text_color", "candidate_text_color")
    }

    private val candidateBorderColor: Int by lazy {
        resolveColor("t9_candidate_border_color", "candidate_border_color")
    }

    private val candidateHilitedBg by lazy {
        ColorManager.getDecorDrawable(
            "t9_candidate_hilited_back_color",
            cornerRadius = cornerRadiusPx,
        )
    }

    private val borderLayer = View(context).apply {
        background = candidateBg
    }

    private fun resolveColor(primaryKey: String, fallbackKey: String): Int {
        runCatching { ColorManager.getColor(primaryKey) }.getOrNull()?.let { return it }
        return runCatching { ColorManager.getColor(fallbackKey) }.getOrElse { Color.TRANSPARENT }
    }

    private val borderWidthPx: Int get() = context.dp(keyboard.keyBorder)
    private val cornerRadiusPx: Float get() = context.dp(keyboard.roundCorner.toInt()).toFloat()
    private val itemHeight: Int get() = keyboard.t9CandidateHeight
    private val itemPadding: Int get() = context.dp(12)

    private val textSizeSp: Float get() = sp(keyboard.roundCorner * 0.8f)
    private val typeface by lazy { FontManager.getTypeface("t9_candidate_font") }

    private val itemViewPool = ArrayDeque<FrameLayout>(16)

    private companion object {
        private const val MODE_EMPTY = 0
        private const val MODE_TOKENS = 1
    }

    private var currentMode = MODE_EMPTY

    init {
        val vGap = keyboard.verticalGap
        val hGap = keyboard.horizontalGap
        setPadding(
            hGap / 2,
            vGap / 2,
            hGap / 2,
            vGap / 2,
        )

        scrollView.addView(
            itemContainer,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        addView(
            borderLayer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ).apply {
                gravity = Gravity.TOP
            },
        )

        addView(
            scrollView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP
            },
        )
    }

    fun updateItems(items: List<T9InputController.PinYinToken>) {
        tokens = items

        if (items.isEmpty()) {
            recycleAllViews()
            return
        }

        if (currentMode != MODE_TOKENS) {
            recycleAllViews()
            buildTokenViews(items)
            return
        }

        updateTokenViewsIncremental(items)
    }

    private fun buildTokenViews(
        items: List<T9InputController.PinYinToken>,
    ) {
        currentMode = MODE_TOKENS
        items.forEachIndexed { index, token ->
            itemContainer.addView(obtainItemView(token))
            if (index < items.size - 1) {
                itemContainer.addView(createDivider())
            }
        }
    }

    private fun updateTokenViewsIncremental(
        items: List<T9InputController.PinYinToken>,
    ) {
        val targetChildCount = items.size * 2 - 1

        while (itemContainer.childCount > targetChildCount) {
            val idx = itemContainer.childCount - 1
            val child = itemContainer.getChildAt(idx)
            if (child is FrameLayout) {
                child.setOnClickListener(null)
                itemViewPool.addLast(child)
            }
            itemContainer.removeViewAt(idx)
        }

        for (i in items.indices) {
            val itemIdx = i * 2
            val token = items[i]

            if (itemIdx < itemContainer.childCount) {
                val itemView = itemContainer.getChildAt(itemIdx) as? FrameLayout ?: continue
                itemView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    itemHeight,
                )
                (itemView.getChildAt(0) as? CandidateTextLabel)?.label = UnicodeVariantUtils.toDisplay(token.display)
                itemView.setOnClickListener { onItemSelected?.invoke(token) }
            } else {
                itemContainer.addView(obtainItemView(token))
            }

            if (i < items.size - 1 && itemIdx + 1 >= itemContainer.childCount) {
                itemContainer.addView(createDivider())
            }
        }
    }

    private fun recycleAllViews() {
        for (i in 0 until itemContainer.childCount) {
            val child = itemContainer.getChildAt(i)
            if (child is FrameLayout) {
                child.setOnClickListener(null)
                itemViewPool.addLast(child)
            }
        }
        itemContainer.removeAllViews()
        currentMode = MODE_EMPTY
    }

    private fun obtainItemView(
        token: T9InputController.PinYinToken,
    ): FrameLayout {
        val view = if (itemViewPool.isNotEmpty()) {
            itemViewPool.removeLast().also { v ->
                v.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    itemHeight,
                )
                (v.getChildAt(0) as? CandidateTextLabel)?.label = UnicodeVariantUtils.toDisplay(token.display)
            }
        } else {
            createItemView(token)
        }
        view.setOnClickListener { onItemSelected?.invoke(token) }
        return view
    }

    private fun createItemView(
        token: T9InputController.PinYinToken,
    ): FrameLayout {
        val label = CandidateTextLabel(context).apply {
            this.label = UnicodeVariantUtils.toDisplay(token.display)
        }

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                itemHeight,
            )
            setPadding(itemPadding, 0, itemPadding, 0)
            isClickable = true
            background = createPressStateDrawable()

            addView(
                label,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT,
                ).apply {
                    gravity = Gravity.CENTER
                },
            )
        }
    }

    private fun createPressStateDrawable(): StateListDrawable {
        val pressed = candidateHilitedBg ?: GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = cornerRadiusPx
        }
        val normal = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = cornerRadiusPx
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun createDivider(): View = View(context).apply {
        layoutParams =
            LinearLayout.LayoutParams(
                context.dp(1),
                itemHeight,
            )
        setBackgroundColor(candidateBorderColor)
        alpha = 0.3f
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val specH = MeasureSpec.getSize(heightMeasureSpec)
        val desiredHeight = itemHeight + paddingTop + paddingBottom + borderWidthPx * 2
        if (measuredHeight < desiredHeight) {
            setMeasuredDimension(measuredWidth, desiredHeight)
        }
    }

    private inner class CandidateTextLabel(context: Context) : View(context) {
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = candidateTextColor
                textSize = textSizeSp
                typeface = typeface
                fontFeatureSettings = FontManager.fontFeatureSettings
            }

        var label: String = ""
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            if (label.isEmpty()) return
            val bounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, bounds)
            val baseline = (height - (bounds.top + bounds.bottom)) / 2f
            val offsetX = if (label.isCjkPunctuation()) visualCenterCorrect(label, textPaint) else 0f
            canvas.drawText(label, width / 2f + offsetX, baseline, textPaint)
        }

        private fun String.isCjkPunctuation(): Boolean {
            if (length != 1) return false
            val c = this[0]
            return c in '\u3000'..'\u303F' ||
                c in '\uFF00'..'\uFF0F' ||
                c in '\uFF1A'..'\uFF1F' ||
                c == '\u00B7'
        }

        private fun visualCenterCorrect(text: String, paint: Paint): Float {
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val glyphCenter = paint.measureText(text) / 2f
            val visualCenter = bounds.width() / 2f + bounds.left.toFloat()
            return glyphCenter - visualCenter
        }
    }
}