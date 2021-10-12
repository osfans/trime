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

package com.osfans.trime.ime.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import com.osfans.trime.Rime;
import com.osfans.trime.setup.Config;

/** 顯示候選字詞 */
public class Candidate extends View {

  /** 處理候選條選字事件 */
  public interface EventListener {
    void onCandidatePressed(int index);
  }

  private static final int MAX_CANDIDATE_COUNT = 30;
  private static final int CANDIDATE_TOUCH_OFFSET = -12;

  private EventListener listener;
  private int highlightIndex;
  private Rime.RimeCandidate[] candidates;
  private int num_candidates;
  private int start_num = 0;

  private Drawable candidateHighlight, candidateSeparator;
  private final Paint candidatePaint;
  private final Paint symbolPaint;
  private final Paint commentPaint;
  private Typeface candidateTypeface, symbolTypeface, commentTypeface, hanBTypeface, latinTypeface;
  private int candidateTextColor, hilitedCandidateTextColor;
  private int commentTextColor, hilitedCommentTextColor;
  private int candidateViewHeight, commentHeight, candidateSpacing, candidatePadding;
  private boolean shouldShowComment = true, isCommentOnTop, candidateUseCursor;

  private final Rect[] candidateRect = new Rect[MAX_CANDIDATE_COUNT + 2];

  public void reset(Context context) {
    Config config = Config.get(context);
    candidateHighlight = new PaintDrawable(config.getColor("hilited_candidate_back_color"));
    ((PaintDrawable) candidateHighlight).setCornerRadius(config.getFloat("layout/round_corner"));
    candidateSeparator = new PaintDrawable(config.getColor("candidate_separator_color"));
    candidateSpacing = config.getPixel("candidate_spacing");
    candidatePadding = config.getPixel("candidate_padding");

    candidateTextColor = config.getColor("candidate_text_color");
    commentTextColor = config.getColor("comment_text_color");
    hilitedCandidateTextColor = config.getColor("hilited_candidate_text_color");
    hilitedCommentTextColor = config.getColor("hilited_comment_text_color");

    int candidate_text_size = config.getPixel("candidate_text_size");
    int comment_text_size = config.getPixel("comment_text_size");
    candidateViewHeight = config.getPixel("candidate_view_height");
    commentHeight = config.getPixel("comment_height");

    candidateTypeface = config.getFont("candidate_font");
    latinTypeface = config.getFont("latin_font");
    hanBTypeface = config.getFont("hanb_font");
    commentTypeface = config.getFont("comment_font");
    symbolTypeface = config.getFont("symbol_font");

    candidatePaint.setTextSize(candidate_text_size);
    candidatePaint.setTypeface(candidateTypeface);
    symbolPaint.setTextSize(candidate_text_size);
    symbolPaint.setTypeface(symbolTypeface);
    commentPaint.setTextSize(comment_text_size);
    commentPaint.setTypeface(commentTypeface);

    isCommentOnTop = config.getBoolean("comment_on_top");
    candidateUseCursor = config.getBoolean("candidate_use_cursor");
    invalidate();
  }

  public void setShowComment(boolean value) {
    shouldShowComment = value;
  }

  public Candidate(Context context, AttributeSet attrs) {
    super(context, attrs);
    candidatePaint = new Paint();
    candidatePaint.setAntiAlias(true);
    candidatePaint.setStrokeWidth(0);
    symbolPaint = new Paint();
    symbolPaint.setAntiAlias(true);
    symbolPaint.setStrokeWidth(0);
    commentPaint = new Paint();
    commentPaint.setAntiAlias(true);
    commentPaint.setStrokeWidth(0);

    reset(context);

    setWillNotDraw(false);
  }

  public static int getMaxCandidateCount() {
    return MAX_CANDIDATE_COUNT;
  }

  public void setCandidateListener(EventListener listener) {
    this.listener = listener;
  }

  /**
   * 刷新候選列表
   *
   * @param start 候選的起始編號
   */
  public void setText(int start) {
    start_num = start;
    removeHighlight();
    updateCandidateWidth();
    if (getCandNum() > 0) {
      invalidate();
    }
  }

  /**
   * 選取候選項
   *
   * @param index 候選項序號（從0開始），{@code -1}表示選擇當前高亮候選項
   * @return 是否成功選字
   */
  @SuppressWarnings("UnusedReturnValue")
  private boolean pickHighlighted(int index) {
    if ((highlightIndex != -1) && (listener != null)) {
      if (index == -1) index = highlightIndex;
      if (index >= 0) index += start_num;
      listener.onCandidatePressed(index);
      return true;
    }
    return false;
  }

  private boolean updateHighlight(int x, int y) {
    int index = getCandidateIndex(x, y);
    if (index != -1) {
      highlightIndex = index;
      invalidate();
      return true;
    }
    return false;
  }

  private void removeHighlight() {
    highlightIndex = -1;
    invalidate();
    requestLayout();
  }

  private boolean isHighlighted(int i) {
    return candidateUseCursor && i >= 0 && i == highlightIndex;
  }

  private void drawHighlight(Canvas canvas) {
    if (isHighlighted(highlightIndex)) {
      candidateHighlight.setBounds(candidateRect[highlightIndex]);
      candidateHighlight.draw(canvas);
    }
  }

  private Typeface getFont(int codepoint, Typeface font) {
    if (hanBTypeface != Typeface.DEFAULT && Character.isSupplementaryCodePoint(codepoint))
      return hanBTypeface;
    if (latinTypeface != Typeface.DEFAULT && codepoint < 0x2e80) return latinTypeface;
    return font;
  }

  private void drawText(
      String s, Canvas canvas, Paint paint, Typeface font, float center, float y) {
    if (s == null) return;
    int length = s.length();
    if (length == 0) return;
    int points = s.codePointCount(0, length);
    float x = center - measureText(s, paint, font) / 2;
    if (latinTypeface != Typeface.DEFAULT
        || (hanBTypeface != Typeface.DEFAULT && length > points)) {
      int offset = 0;
      while (offset < length) {
        int codepoint = s.codePointAt(offset);
        int charCount = Character.charCount(codepoint);
        int end = offset + charCount;
        paint.setTypeface(getFont(codepoint, font));
        canvas.drawText(s, offset, end, x, y, paint);
        x += paint.measureText(s, offset, end);
        offset = end;
      }
    } else {
      paint.setTypeface(font);
      canvas.drawText(s, x, y, paint);
    }
  }

  private void drawCandidates(Canvas canvas) {
    if (candidates == null) return;

    float candidateX;

    float commentX, commentY;
    float commentWidth;
    String candidate;

    float candidateY =
        candidateRect[0].centerY() - (candidatePaint.ascent() + candidatePaint.descent()) / 2;
    if (shouldShowComment && isCommentOnTop) candidateY += commentHeight / 2f;
    commentY = commentHeight / 2f - (commentPaint.ascent() + commentPaint.descent()) / 2;
    if (shouldShowComment && !isCommentOnTop) commentY += candidateRect[0].bottom - commentHeight;

    int i = 0;
    while (i < num_candidates) {
      // Calculate a position where the text could be centered in the rectangle.
      candidateX = candidateRect[i].centerX();
      if (shouldShowComment) {
        final String comment = getComment(i);
        if (!TextUtils.isEmpty(comment)) {
          commentWidth = measureText(comment, commentPaint, commentTypeface);
          if (isCommentOnTop) {
            commentX = candidateRect[i].centerX();
          } else {
            candidateX -= commentWidth / 2;
            commentX = candidateRect[i].right - commentWidth / 2;
          }
          commentPaint.setColor(isHighlighted(i) ? hilitedCommentTextColor : commentTextColor);
          drawText(comment, canvas, commentPaint, commentTypeface, commentX, commentY);
        }
      }
      candidatePaint.setColor(isHighlighted(i) ? hilitedCandidateTextColor : candidateTextColor);
      drawText(getCandidate(i), canvas, candidatePaint, candidateTypeface, candidateX, candidateY);
      // Draw the separator at the right edge of each candidate.
      candidateSeparator.setBounds(
          candidateRect[i].right - candidateSeparator.getIntrinsicWidth(),
          candidateRect[i].top,
          candidateRect[i].right + candidateSpacing,
          candidateRect[i].bottom);
      candidateSeparator.draw(canvas);
      i++;
    }
    for (int j = -4; j >= -5; j--) { // -4: left, -5: right
      candidate = getCandidate(j);
      if (candidate == null) continue;
      symbolPaint.setColor(isHighlighted(i) ? hilitedCommentTextColor : commentTextColor);
      candidateX =
          candidateRect[i].centerX() - measureText(candidate, symbolPaint, symbolTypeface) / 2;
      canvas.drawText(candidate, candidateX, candidateY, symbolPaint);
      candidateSeparator.setBounds(
          candidateRect[i].right - candidateSeparator.getIntrinsicWidth(),
          candidateRect[i].top,
          candidateRect[i].right + candidateSpacing,
          candidateRect[i].bottom);
      candidateSeparator.draw(canvas);
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

  private void updateCandidateWidth() {
    final int top = 0;
    final int bottom = getHeight();
    int i;
    int x = 0;
    if (Rime.hasLeft()) x += getCandidateWidth(-4) + candidateSpacing;
    getCandNum();
    for (i = 0; i < num_candidates; i++) {
      candidateRect[i] = new Rect(x, top, x += getCandidateWidth(i), bottom);
      x += candidateSpacing;
    }
    if (Rime.hasLeft()) candidateRect[i++] = new Rect(0, top, (int) getCandidateWidth(-4), bottom);
    if (Rime.hasRight()) candidateRect[i++] = new Rect(x, top, x += getCandidateWidth(-5), bottom);
    LayoutParams params = getLayoutParams();
    params.width = x;
    params.height = candidateViewHeight;
    if (shouldShowComment && isCommentOnTop) params.height += commentHeight;
    setLayoutParams(params);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateCandidateWidth();
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(MotionEvent me) {
    int action = me.getAction();
    int x = (int) me.getX();
    int y = (int) me.getY();

    switch (action) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_MOVE:
        updateHighlight(x, y);
        break;
      case MotionEvent.ACTION_UP:
        if (updateHighlight(x, y)) {
          performClick();
          pickHighlighted(-1);
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
   * @return {@code >=0}: 觸摸點 (x, y) 處候選項序號，從0開始編號； {@code -1}: 觸摸點 (x, y) 處無候選項； {@code -4}: 觸摸點
   *     (x, y) 處爲{@code Page_Up}； {@code -5}: 觸摸點 (x, y) 處爲{@code Page_Down}
   */
  private int getCandidateIndex(int x, int y) {
    Rect r = new Rect();

    int j = 0;
    for (int i = 0; i < num_candidates; i++) {
      // Enlarge the rectangle to be more responsive to user clicks.
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        // Returns -1 if there is no candidate in the hitting rectangle.
        return (i < num_candidates) ? i : -1;
      }
    }

    if (Rime.hasLeft()) { // Page Up
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        return -4;
      }
    }

    if (Rime.hasRight()) { // Page Down
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        return -5;
      }
    }

    return -1;
  }

  private int getCandNum() {
    candidates = Rime.getCandidates();
    highlightIndex = Rime.getCandHighlightIndex() - start_num;
    num_candidates = candidates == null ? 0 : candidates.length - start_num;
    return num_candidates;
  }

  private String getCandidate(int i) {
    String s = null;
    if (candidates != null && i >= 0) s = candidates[i + start_num].text;
    else if (i == -4 && Rime.hasLeft()) s = "◀";
    else if (i == -5 && Rime.hasRight()) s = "▶";
    return s;
  }

  private String getComment(int i) {
    String s = null;
    if (candidates != null && i >= 0) s = candidates[i + start_num].comment;
    return s;
  }

  private float measureText(String s, Paint paint, Typeface font) {
    float x = 0;
    if (s == null) return x;
    int length = s.length();
    if (length == 0) return x;
    int points = s.codePointCount(0, length);
    if (latinTypeface != Typeface.DEFAULT
        || (hanBTypeface != Typeface.DEFAULT && length > points)) {
      int offset = 0;
      while (offset < length) {
        int codepoint = s.codePointAt(offset);
        int charCount = Character.charCount(codepoint);
        int end = offset + charCount;
        paint.setTypeface(getFont(codepoint, font));
        x += paint.measureText(s, offset, end);
        offset = end;
      }
      paint.setTypeface(font);
    } else {
      paint.setTypeface(font);
      x += paint.measureText(s);
    }
    return x;
  }

  private float getCandidateWidth(int i) {
    String s = getCandidate(i);
    // float n = (s == null ? 0 : s.codePointCount(0, s.length()));
    float x = 2 * candidatePadding;
    if (s != null) x += measureText(s, candidatePaint, candidateTypeface);
    if (i >= 0 && shouldShowComment) {
      String comment = getComment(i);
      if (comment != null) {
        float x2 = measureText(comment, commentPaint, commentTypeface);
        if (isCommentOnTop) {
          if (x2 > x) x = x2;
        } // 提示在上方
        else x += x2; // 提示在右方
      }
    }
    return x;
  }
}
