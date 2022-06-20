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
import com.osfans.trime.core.Rime;
import com.osfans.trime.ime.core.Trime;
import timber.log.Timber;

public class ScrollView extends HorizontalScrollView {
  private View inner;
  private float x;
  private final Rect normal = new Rect();
  private boolean isCount = false;
  private boolean isMoving = false;
  private int left;

  private Runnable pageDownAction, pageUpAction, pageExAction;

  public ScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setPageStr(Runnable pageDownAction, Runnable pageUpAction, Runnable pageExAction) {
    this.pageDownAction = pageDownAction;
    this.pageUpAction = pageUpAction;
    this.pageExAction = pageExAction;
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

  private int swipeActionLimit = 200;
  private float swipeStartX = -1;

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
      case MotionEvent.ACTION_DOWN:
        swipeActionLimit = Math.min(getWidth() / 4, getHeight() * 10);
        Timber.i(
            "commOnTouchEvent limit = "
                + swipeActionLimit
                + ", "
                + getWidth() / 4
                + ", "
                + getHeight() * 4);
        if (swipeActionLimit < 50) swipeActionLimit = 100;
        break;

      case MotionEvent.ACTION_UP:
        swipeStartX = -1;
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
        if (inner.getLeft() > swipeActionLimit && Rime.hasLeft()) {
          if (pageUpAction != null) pageUpAction.run();
          if (inner.getWidth() > this.getWidth())
            scrollTo(this.getWidth() - inner.getWidth() + 400, 0);
        } else {
          //          Timber.d("commOnTouchEvent "+getWidth() + "-" + inner.getWidth() +"+" +
          // getScrollX()+", p=" +scrollEndPosition+", x="+ev.getX());
          Timber.d(
              "commOnTouchEvent dif=" + (swipeStartX - ev.getX()) + " limit=" + swipeActionLimit);

          if (swipeStartX < 0) {
            if (getWidth() - inner.getWidth() + getScrollX() >= 0) {
              swipeStartX = ev.getX();
            }
          } else if (swipeStartX - ev.getX() > swipeActionLimit) {
            if (Trime.getService().hasCandidateExPage()) {
              if (pageExAction != null) pageExAction.run();
              return;
            } else if (Rime.hasRight()) {
              if (pageDownAction != null) pageDownAction.run();
              swipeStartX = -1;
              if (inner.getWidth() > this.getWidth()) {
                scrollTo(-swipeActionLimit, 0);
                inner.layout(
                    inner.getRight(),
                    inner.getTop(),
                    inner.getRight() + inner.getWidth(),
                    inner.getBottom());
              }
              return;
            }
          }

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
    final TranslateAnimation taa = new TranslateAnimation(0, 0, left + 200, 200);
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

  public void move(int left, int right) {
    Timber.i("ScroolView move(%s %s), scroll=%s", left, right, getScrollX());
    if (right > getScrollX() + getWidth()) {
      scrollTo(right - getWidth(), 0);
    } else if (left < getScrollX()) {
      scrollTo(left, 0);
    }
  }
}
