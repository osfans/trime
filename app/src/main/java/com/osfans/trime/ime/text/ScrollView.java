package com.osfans.trime.ime.text;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.HorizontalScrollView;
import androidx.annotation.NonNull;
import com.osfans.trime.Rime;

public class ScrollView extends HorizontalScrollView {
  private View inner;
  private float x;
  private final Rect normal = new Rect();
  private boolean isCount = false;
  private boolean isMoving = false;
  private int left;

  private Runnable pageDownAction, pageUpAction;

  public ScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setPageStr(Runnable pageDownAction, Runnable pageUpAction) {
    this.pageDownAction = pageDownAction;
    this.pageUpAction = pageUpAction;
  }

  /**
   * Based on the XML generated view work done. The function in the creation of the view of the last
   * call. after all sub-view has been added. Even if the sub-class covered the onFinishInflate
   * method. it should also call the parent class method to make the method to be implemented.
   */
  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    if (getChildCount() > 0) {
      inner = getChildAt(0);
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(@NonNull MotionEvent ev) {
    if (inner != null) {
      commOnTouchEvent(ev);
    }
    return super.onTouchEvent(ev);
  }

  @Override
  public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
    /*
    if (ev.getAction() == MotionEvent.ACTION_UP) {
        if (views != null) { }
    } **/
    return super.onInterceptTouchEvent(ev);
  }

  /** Slide event (let the speed of sliding into the original 1/2) */
  @Override
  public void fling(int velocityY) {
    super.fling(velocityY / 2);
  }

  public void commOnTouchEvent(@NonNull MotionEvent ev) {
    int action = ev.getAction();
    switch (action) {
        /* case MotionEvent.ACTION_DOWN:
        break; **/
      case MotionEvent.ACTION_UP:
        isMoving = false;
        // Fingers loose
        if (isNeedAnimation()) {
          animation();
        }

        break;
      case MotionEvent.ACTION_MOVE:
        final float preX = x; // When the y coordinate is pressed
        final float nowX = ev.getX(); // Always y-coordinate
        int deltaX = (int) (nowX - preX); // Slide distance
        if (!isCount) {
          deltaX = 0; // Here to 0.
        }

        // TODO: 翻页后、手指抬起前，降低滑动速度增加阻尼感
        if (inner.getLeft() > 100 && Rime.hasLeft()) {
          if (pageUpAction != null)
            pageUpAction.run();
          if (inner.getWidth() > this.getWidth())
            inner.layout(
                    this.getWidth() - inner.getWidth() - 400,
                    inner.getTop(),
                    this.getWidth() - 400,
                    inner.getBottom());
        } else if (inner.getWidth() - inner.getRight() > 100 && Rime.hasRight()) {
          if (pageDownAction != null)
            pageDownAction.run();
          if (inner.getWidth() > this.getWidth()) {
            inner.layout(400, inner.getTop(), this.getWidth() + 400, inner.getBottom());
          }
        } else {
          // When the scroll to the top or the most when it will not scroll, then move the layout.
          isNeedMove();
          // Log.d("MotionEvent "+isMoveing," d="+deltaX+" left="+left+" LR="+inner.getLeft()+",
          // "+inner.getRight()+" scrollX="+this.getScrollX());
          if (isMoving) {
            // Initialize the head rectangle
            if (normal.isEmpty()) {
              // Save the normal layout position
              normal.set(inner.getLeft(), inner.getTop(), inner.getRight(), inner.getBottom());
            }
            // Move the layout
            inner.layout(
                inner.getLeft() + deltaX / 3,
                inner.getTop(),
                inner.getRight() + deltaX / 3,
                inner.getBottom());

            left += (deltaX / 6);
          }

          isCount = true;
          x = nowX;
        }
        break;
      default:
        break;
    }
  }

  /** Retract animation */
  public void animation() {
    final TranslateAnimation taa = new TranslateAnimation(0, 0, left + 200,  200);
    taa.setDuration(200);
    // Turn on moving animation
    final TranslateAnimation ta = new TranslateAnimation(inner.getLeft(), normal.left, 0, 0);
    ta.setDuration(200);
    inner.startAnimation(ta);
    // Set back to the normal layout position
    inner.layout(normal.left, normal.top, normal.right, normal.bottom);
    normal.setEmpty();

    isCount = false;
    x = 0; // Fingers loose to 0..
  }

  // Whether you need to turn on animation
  public boolean isNeedAnimation() {
    return !normal.isEmpty();
  }

  public void isNeedMove() {
    if (getScrollY() == 0) {
      isMoving = true;
    }
  }
}
