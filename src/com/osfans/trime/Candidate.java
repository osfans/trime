/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;

import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.util.Log;
import android.util.TypedValue;
import android.graphics.Typeface;

import java.io.File;
import java.util.Map;

/**
 * View to show candidate words.
 */
public class Candidate extends View {

  /**
   * Listens to candidate-view actions.
   */
  public static interface CandidateListener {
    void onPickCandidate(int index);
  }

  public static final int MAX_CANDIDATE_COUNT = 20;
  private static final int CANDIDATE_TOUCH_OFFSET = -12;

  private CandidateListener listener;
  private int highlightIndex;
  private Rime.RimeCandidate[] candidates;
  private int num_candidates;

  private Drawable candidateHighlight, candidateSeparator;
  private Paint paintCandidate, paintComment;
  private Typeface tfCandidate, tfb, tfComment, tfs;
  private int candidate_text_color, hilited_candidate_text_color;
  private int comment_text_color, hilited_comment_text_color;
  private int candidate_text_size, comment_text_size;
  private int candidate_view_height, comment_height;
  private boolean show_comment, comment_on_top;

  private Rect candidateRect[] = new Rect[MAX_CANDIDATE_COUNT];

  public void reset() {
    Config config = Config.get();
    candidateHighlight = new ColorDrawable(config.getColor("hilited_candidate_back_color"));
    candidateSeparator = new ColorDrawable(config.getColor("candidate_separator_color"));
    candidate_text_color = config.getColor("candidate_text_color");
    comment_text_color = config.getColor("comment_text_color");
    hilited_candidate_text_color = config.getColor("hilited_candidate_text_color");
    hilited_comment_text_color = config.getColor("hilited_comment_text_color");

    candidate_text_size = config.getPixel("candidate_text_size");
    comment_text_size = config.getPixel("comment_text_size");
    candidate_view_height = config.getPixel("candidate_view_height");
    comment_height = config.getPixel("comment_height");

    tfCandidate = config.getFont("candidate_font");
    tfb = config.getFont("hanb_font");
    tfComment = config.getFont("comment_font");
    tfs = config.getFont("symbol_font");

    paintCandidate.setTextSize(candidate_text_size);
    paintCandidate.setTypeface(tfCandidate);
    paintComment.setTextSize(comment_text_size);
    paintComment.setTypeface(tfComment);

    show_comment = config.getBoolean("show_comment");
    boolean show = config.getBoolean("show_candidate");
    setVisibility(show ? View.VISIBLE : View.GONE);
    comment_on_top = config.getBoolean("comment_on_top");
    invalidate();
  }

  public Candidate(Context context, AttributeSet attrs) {
    super(context, attrs);
    paintCandidate = new Paint();
    paintCandidate.setAntiAlias(true);
    paintCandidate.setStrokeWidth(0);
    paintComment = new Paint();
    paintComment.setAntiAlias(true);
    paintComment.setStrokeWidth(0);

    reset();

    setWillNotDraw(false);
  }
  
  public void setCandidateListener(CandidateListener listener) {
    this.listener = listener;
  }

  /**
   * Highlight the first candidate as the default candidate.
   */
  public void setText() {
    removeHighlight();
    updateCandidateWidth();
    if (getCandNum() > 0) {
      invalidate();
    }
  }

  /**
   * Picks the highlighted candidate.
   *
   * @return {@code false} if no candidate is highlighted and picked.
   */
  public boolean pickHighlighted(int index) {
    if ((highlightIndex != -1) && (listener != null)) {
      listener.onPickCandidate(index == -1 ? highlightIndex : index);
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

  private void drawHighlight(Canvas canvas) {
    if (highlightIndex >= 0) {
      candidateHighlight.setBounds(candidateRect[highlightIndex]);
      candidateHighlight.draw(canvas);
    }
  }

  private void drawText(String s, Canvas canvas, Paint paint, Typeface font, float center, float y) {
    if (s == null) return;
    int length = s.length();
    if (length == 0) return;
    int points = s.codePointCount(0, length);
    float x = center - paint.measureText(s) / 2;
    if (tfb != null && length > points) {
      for (int offset = 0; offset < length; ) {
        int codepoint = s.codePointAt(offset);
        int charCount = Character.charCount(codepoint);
        int end = offset + charCount;
        paint.setTypeface(Character.isSupplementaryCodePoint(codepoint) ? tfb : font);
        canvas.drawText(s, offset, end, x, y, paint);
        x += paint.measureText(s, offset, end);
        offset += charCount;
      }
    } else {
      paint.setTypeface(font);
      canvas.drawText(s, x, y, paint);
    }
  }

  private void drawCandidates(Canvas canvas) {
    if (candidates == null) return;

    float x = 0;
    float y = 0;
    int i = 0;
    float comment_x, comment_y;
    float comment_width;
    String candidate, comment;

    y = candidateRect[0].centerY() - (paintCandidate.ascent() + paintCandidate.descent()) / 2;
    if (show_comment && comment_on_top) y += comment_height / 2;
    comment_y = comment_height / 2 - (paintComment.ascent() + paintComment.descent()) / 2;
    if (show_comment && !comment_on_top) comment_y += candidateRect[0].bottom - comment_height;

    while (i < num_candidates) {
      // Calculate a position where the text could be centered in the rectangle.
      x = candidateRect[i].centerX();
      if (show_comment) {
        comment = getComment(i);
        if (comment != null && !comment.isEmpty()) {
          comment_width = paintComment.measureText(comment);
          if (comment_on_top) {
            comment_x = candidateRect[i].centerX();
          } else {
            x -= comment_width / 2;
            comment_x = candidateRect[i].right -  comment_width / 2;
          }
          paintComment.setTypeface(tfComment);
          paintComment.setColor(highlightIndex == i ? hilited_comment_text_color : comment_text_color);
          drawText(comment, canvas, paintComment, tfComment, comment_x, comment_y);
        }
      }
      paintCandidate.setTypeface(tfCandidate);
      paintCandidate.setColor(highlightIndex == i ? hilited_candidate_text_color : candidate_text_color);
      drawText(getCandidate(i), canvas, paintCandidate, tfCandidate, x, y);
      // Draw the separator at the right edge of each candidate.
      candidateSeparator.setBounds(
        candidateRect[i].right - candidateSeparator.getIntrinsicWidth(),
        candidateRect[i].top,
        candidateRect[i].right,
        candidateRect[i].bottom);
      candidateSeparator.draw(canvas);
      i++;
    }
    for (int j = -4; j >= -5; j--) { // -4: left, -5: right
      candidate = getCandidate(j);
      if (candidate == null) continue;
      paintCandidate.setTypeface(tfs);
      x = candidateRect[i].centerX() - paintCandidate.measureText(candidate) / 2;
      canvas.drawText(candidate, x, y, paintCandidate);
      candidateSeparator.setBounds(
        candidateRect[i].right - candidateSeparator.getIntrinsicWidth(),
        candidateRect[i].top,
        candidateRect[i].right,
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
    int i = 0;
    int x = 0;
    if (Rime.hasLeft()) x += getCandidateWidth(-4);
    getCandNum();
    for (i = 0; i < num_candidates; i++) candidateRect[i] = new Rect(x, top, x += getCandidateWidth(i), bottom);
    if (Rime.hasLeft()) candidateRect[i++] = new Rect(0, top, (int)getCandidateWidth(-4), bottom);
    if (Rime.hasRight()) candidateRect[i++] = new Rect(x, top, x += getCandidateWidth(-5), bottom);
    LayoutParams params = getLayoutParams();
    params.width = x;
    params.height = candidate_view_height;
    if (show_comment && comment_on_top) params.height += comment_height;
    setLayoutParams(params);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateCandidateWidth();
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
          pickHighlighted(-1);
        }
        break;
    }
    return true;
  }

  /**
   * Returns the index of the candidate which the given coordinate points to.
   * 
   * @return -1 if no candidate is mapped to the given (x, y) coordinate.
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

    if (Rime.hasLeft()) { //Page Up
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        return -4;
      }
    }

    if (Rime.hasRight()) { //Page Down
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
    highlightIndex = Rime.getCandHighlightIndex();
    num_candidates = candidates == null ? 0 : candidates.length;
    return num_candidates;
  }

  private String getCandidate(int i) {
    String s = null;
    if (candidates != null && i >= 0) s = candidates[i].text;
    else if (i == -4 && Rime.hasLeft()) s = "◀";
    else if (i == -5 && Rime.hasRight()) s = "▶";
    return s;
  }

  private String getComment(int i) {
    String s = null;
    if (candidates != null && i >= 0) s = candidates[i].comment;
    return s;
  }

  private float getCandidateWidth(int i) {
    String s = getCandidate(i);
    float n = (s == null ? 0 : s.codePointCount(0, s.length()));
    n += n < 2 ? 0.8f : 0.4f;
    float x = n * candidate_text_size;
    if (i >= 0 && show_comment) {
      String comment = getComment(i);
      if (comment != null) {
        float x2 = paintComment.measureText(comment);
        if (comment_on_top) { if (x2 > x) x = x2; } //提示在上方
        else x += x2;  //提示在右方
      }
    }
    return x;
  }
}
