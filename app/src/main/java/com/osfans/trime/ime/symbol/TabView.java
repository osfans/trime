/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.osfans.trime.ime.symbol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import androidx.annotation.NonNull;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.setup.Config;
import com.osfans.trime.util.GraphicUtils;

import timber.log.Timber;

// 这是滑动键盘顶部的view，展示了键盘布局的多个标签。
// 为了公用候选栏的皮肤参数以及外观，大部分代码从Candidate.java复制而来。
public class TabView extends View {

  private static final int MAX_CANDIDATE_COUNT = 30;
  private static final int CANDIDATE_TOUCH_OFFSET = -12;

  private int highlightIndex;
  private TabTag[] tabTags;
  private final GraphicUtils graphicUtils;

  private PaintDrawable candidateHighlight;
  private final Paint separatorPaint;
  private final Paint candidatePaint;
  private Typeface candidateFont;
  private int candidateTextColor, hilitedCandidateTextColor;
  private int candidateViewHeight, commentHeight, candidateSpacing, candidatePadding;
  private final boolean shouldShowComment = true;
  private boolean isCommentOnTop;
  private boolean shouldCandidateUseCursor;

  private final Rect[] tabGeometries = new Rect[MAX_CANDIDATE_COUNT + 2];

  public void reset(Context context) {
    Config config = Config.get(context);
    candidateHighlight = new PaintDrawable(config.getColor("hilited_candidate_back_color"));
    candidateHighlight.setCornerRadius(config.getFloat("layout/round_corner"));

    separatorPaint.setColor(config.getColor("candidate_separator_color"));

    candidateSpacing = config.getPixel("candidate_spacing");
    candidatePadding = config.getPixel("candidate_padding");

    candidateTextColor = config.getColor("candidate_text_color");
    hilitedCandidateTextColor = config.getColor("hilited_candidate_text_color");

    commentHeight = config.getPixel("comment_height");

    int candidateTextSize = config.getPixel("candidate_text_size");
    candidateViewHeight = config.getPixel("candidate_view_height");

    candidateFont = config.getFont("candidate_font");

    candidatePaint.setTextSize(candidateTextSize);
    candidatePaint.setTypeface(candidateFont);

    isCommentOnTop = config.getBoolean("comment_on_top");
    shouldCandidateUseCursor = config.getBoolean("candidate_use_cursor");
    invalidate();
  }

  public TabView(Context context, AttributeSet attrs) {
    super(context, attrs);
    candidatePaint = new Paint();
    candidatePaint.setAntiAlias(true);
    candidatePaint.setStrokeWidth(0);

    separatorPaint = new Paint();
    separatorPaint.setColor(Color.BLACK);

    graphicUtils = new GraphicUtils(context);
    reset(context);

    setWillNotDraw(false);
  }

  private boolean isHighlighted(int i) {
    return shouldCandidateUseCursor && i >= 0 && i == highlightIndex;
  }

  private void drawHighlight(Canvas canvas) {
    if (isHighlighted(highlightIndex)) {
      candidateHighlight.setBounds(tabGeometries[highlightIndex]);
      candidateHighlight.draw(canvas);
    }
  }

  public int getHightlightLeft() {
    return tabGeometries[highlightIndex].left;
  }

  public int getHightlightRight() {
    return tabGeometries[highlightIndex].right;
  }

  private void drawCandidates(Canvas canvas) {
    if (tabTags == null) return;

    float y = tabGeometries[0].centerY() - (candidatePaint.ascent() + candidatePaint.descent()) / 2;
    if (shouldShowComment && isCommentOnTop) y += (float) commentHeight / 2;

    int i = 0;
    while (i < tabTags.length) {
      // Calculate a position where the text could be centered in the rectangle.
      float x = tabGeometries[i].centerX();

      candidatePaint.setColor(
          isHighlighted(i) ? hilitedCandidateTextColor : candidateTextColor);
      graphicUtils.drawText(canvas, getTabText(i), x, y,candidatePaint, candidateFont);
      // Draw the separator at the right edge of each candidate.
      canvas.drawRect(
              tabGeometries[i].right - candidateSpacing,
              tabGeometries[i].top,
              tabGeometries[i].right + candidateSpacing,
              tabGeometries[i].bottom,
              separatorPaint
      );
      i++;
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (canvas == null) {
      return;
    }
    super.onDraw(canvas);

    drawHighlight(canvas);
    drawCandidates(canvas);
  }

  public void updateCandidateWidth() {
    tabTags = TabManager.get().getTabCandidates();
    highlightIndex = TabManager.get().getSelected();

    int x = 0;
    for (int i = 0; i < tabTags.length; i++) {
      tabGeometries[i] = new Rect(x, 0, x += getTabWidth(i), getHeight());
      x += candidateSpacing;
    }
    LayoutParams params = getLayoutParams();
    Timber.i("update, from Height=" + params.height + " width=" + params.width);
    params.width = x;
    params.height = (shouldShowComment && isCommentOnTop) ? candidateViewHeight + commentHeight : candidateViewHeight;
    Timber.i("update, to Height=" + candidateViewHeight + " width=" + x);
    setLayoutParams(params);
    params = getLayoutParams();
    Timber.i("update, reload Height=" + params.height + " width=" + params.width);
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateCandidateWidth();
    Timber.i("onSizeChanged() w=" + w + ", Height=" + oldh + "=>" + h);
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  int x0, y0;
  long time0;

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent me) {
    int x = (int) me.getX();
    int y = (int) me.getY();

    switch (me.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        x0 = x;
        y0 = y;
        time0 = System.currentTimeMillis();
        //        updateHighlight(x, y);
        break;
      case MotionEvent.ACTION_MOVE:
        if (Math.abs(x - x0) > 100) time0 = 0;
        break;
      case MotionEvent.ACTION_UP:
        int i = getTabIndex(x, y);
        if (i > -1) {
          performClick();
          TabTag tag = TabManager.getTag(i);
          if (tag.type == SymbolKeyboardType.NO_KEY) {
            switch (tag.command) {
              case EXIT:
                Trime.getService().selectLiquidKeyboard(-1);
                break;

                // TODO liquidKeyboard中除返回按钮外，其他按键均未实装
              case DEL_LEFT:
              case DEL_RIGHT:
              case REDO:
              case UNDO:
                break;
            }
          } else if (System.currentTimeMillis() - time0 < 500) {
            highlightIndex = i;
            invalidate();
            Trime.getService().selectLiquidKeyboard(i);
          }
          Timber.d("index=" + i + " length=" + tabTags.length);
        }
        break;
    }
    return true;
  }

  /**
   * 獲得觸摸處候選項序號
   *
   * @param x 觸摸點橫座標
   * @param y 觸摸點縱座標
   * @return {@code >=0}: 觸摸點 (x, y) 處候選項序號，從0開始編號； {@code -1}: 觸摸點 (x, y) 處無候選項；
   */
  private int getTabIndex(int x, int y) {
    Rect r = new Rect();

    int j = 0;
    for (int i = 0; i < tabTags.length; i++) {
      // Enlarge the rectangle to be more responsive to user clicks.
      r.set(tabGeometries[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        // Returns -1 if there is no candidate in the hitting rectangle.
        return (i < tabTags.length) ? i : -1;
      }
    }
    return -1;
  }

  private String getTabText(int i) {
    if (tabTags != null && i >= 0) return tabTags[i].text;
    return "-1";
  }

  private float getTabWidth(int i) {
    String s = getTabText(i);
    return s != null ? 2 * candidatePadding + graphicUtils.measureText(candidatePaint, s, candidateFont) : 2 * candidatePadding;
  }
}
