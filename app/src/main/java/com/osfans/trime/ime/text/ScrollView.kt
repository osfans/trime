package com.osfans.trime.ime.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.HorizontalScrollView
import com.osfans.trime.core.Rime
import com.osfans.trime.ime.core.Trime
import timber.log.Timber
import kotlin.math.min

class ScrollView(context: Context?, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {
    private var inner: View? = null
    private var x = 0f
    private val normal = Rect()
    private var isCount = false
    private var isMoving = false
    private var left = 0
    private var pageDownAction: Runnable? = null
    private var pageUpAction: Runnable? = null
    private var pageExAction: Runnable? = null

    fun setPageStr(
        pageDownAction: Runnable?,
        pageUpAction: Runnable?,
        pageExAction: Runnable?,
    ) {
        this.pageDownAction = pageDownAction
        this.pageUpAction = pageUpAction
        this.pageExAction = pageExAction
    }

    /**
     * Based on the XML generated view work done. The function in the creation of the view of the last
     * call. after all sub-view has been added. Even if the sub-class covered the onFinishInflate
     * method. it should also call the parent class method to make the method to be implemented.
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (childCount > 0) {
            inner = getChildAt(0)
        }
    }

    private var swipeActionLimit = 200
    private var swipeStartX = -1f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (inner != null) {
            commOnTouchEvent(ev)
        }
        return super.onTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        /*if (ev.getAction() == MotionEvent.ACTION_UP) {
            if (views != null) { }
        }*/
        return super.onInterceptTouchEvent(ev)
    }

    /** Slide event (let the speed of sliding into the original 1/2)  */
    override fun fling(velocityY: Int) {
        super.fling(velocityY / 2)
    }

    private fun commOnTouchEvent(ev: MotionEvent) {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeActionLimit = min(width / 4, height * 10)
                Timber.i(
                    "commOnTouchEvent limit = " + swipeActionLimit + ", " + (width / 4) + ", " + (height * 4),
                )
                if (swipeActionLimit < 50) swipeActionLimit = 100
            }

            MotionEvent.ACTION_UP -> {
                swipeStartX = -1f
                isMoving = false
                // Fingers loose
                if (isNeedAnimation) {
                    animation()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val preX = x // When the y coordinate is pressed
                val nowX = ev.x // Always y-coordinate
                var deltaX = (nowX - preX).toInt() // Slide distance
                if (!isCount) {
                    deltaX = 0 // Here to 0.
                }

                // TODO: 翻页后、手指抬起前，降低滑动速度增加阻尼感
                if (inner!!.left > swipeActionLimit && Rime.hasLeft()) {
                    if (pageUpAction != null) pageUpAction!!.run()
                    if (inner!!.width > this.width) scrollTo(this.width - inner!!.width + 400, 0)
                } else {
                    // Timber.d("commOnTouchEvent "+getWidth() + "-" + inner.getWidth() +"+" +
                    // getScrollX()+", p=" +scrollEndPosition+", x="+ev.getX());
                    Timber.d(
                        "commOnTouchEvent dif=" + (swipeStartX - ev.x) + " limit=" + swipeActionLimit,
                    )
                    if (swipeStartX < 0) {
                        if (width - inner!!.width + scrollX >= 0) {
                            swipeStartX = ev.x
                        }
                    } else if (swipeStartX - ev.x > swipeActionLimit) {
                        if (Trime.getService().candidateExPage) {
                            if (pageExAction != null) pageExAction!!.run()
                            return
                        } else if (Rime.hasRight()) {
                            if (pageDownAction != null) pageDownAction!!.run()
                            swipeStartX = -1f
                            if (inner!!.width > this.width) {
                                scrollTo(-swipeActionLimit, 0)
                                inner!!.layout(
                                    inner!!.right,
                                    inner!!.top,
                                    inner!!.right + inner!!.width,
                                    inner!!.bottom,
                                )
                            }
                            return
                        }
                    }

                    // When the scroll to the top or the most when it will not scroll, then move the layout.
                    isNeedMove
                    // Log.d("MotionEvent "+isMoveing," d="+deltaX+" left="+left+" LR="+inner.getLeft()+",
                    // "+inner.getRight()+" scrollX="+this.getScrollX());
                    if (isMoving) {
                        // Initialize the head rectangle
                        if (normal.isEmpty) {
                            // Save the normal layout position
                            normal[inner!!.left, inner!!.top, inner!!.right] = inner!!.bottom
                        }
                        // Move the layout
                        inner!!.layout(
                            inner!!.left + deltaX / 3,
                            inner!!.top,
                            inner!!.right + deltaX / 3,
                            inner!!.bottom,
                        )
                        left += (deltaX / 6)
                    }
                    isCount = true
                    x = nowX
                }
            }

            else -> {}
        }
    }

    /** Retract animation  */
    fun animation() {
        val taa = TranslateAnimation(0f, 0f, (left + 200).toFloat(), 200f)
        taa.duration = 200
        // Turn on moving animation
        val ta = TranslateAnimation(inner!!.left.toFloat(), normal.left.toFloat(), 0f, 0f)
        ta.duration = 200
        inner!!.startAnimation(ta)
        // Set back to the normal layout position
        inner!!.layout(normal.left, normal.top, normal.right, normal.bottom)
        normal.setEmpty()
        isCount = false
        x = 0f // Fingers loose to 0..
    }

    private val isNeedAnimation: Boolean
        // Whether you need to turn on animation
        get() = !normal.isEmpty
    private val isNeedMove: Unit
        get() {
            if (scrollY == 0) {
                isMoving = true
            }
        }

    fun move(
        left: Int,
        right: Int,
    ) {
        Timber.i("ScroolView move(%s %s), scroll=%s", left, right, scrollX)
        if (right > scrollX + width) {
            scrollTo(right - width, 0)
        } else if (left < scrollX) {
            scrollTo(left, 0)
        }
    }
}
