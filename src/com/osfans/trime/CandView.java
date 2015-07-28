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
import android.util.Log;
import android.util.TypedValue;
import android.graphics.Typeface;

import java.io.File;
import java.util.Map;

/**
 * View to show candidate words.
 */
public class CandView extends View {

  /**
   * Listens to candidate-view actions.
   */
  public static interface CandViewListener {
    void onPickCandidate(int index);
  }

  public static final int MAX_CANDIDATE_COUNT = 20;
  private static final int CANDIDATE_TOUCH_OFFSET = -12;

  private CandViewListener listener;
  private int highlightIndex;
  private Rime mRime;
  private Rime.RimeCandidate[] candidates;
  private int num_candidates;

  private Drawable candidateHighlight, candidateSeparator;
  private Paint paint, paintpy;
  private Typeface tf, tfb, tfl, tfs;
  private int candidate_text_color, hilited_candidate_text_color;
  private int comment_text_color, hilited_comment_text_color;
  private int candidate_text_size, comment_text_size;

  private Rect candidateRect[] = new Rect[MAX_CANDIDATE_COUNT];

  public Typeface getFont(String name){
    File f = new File("/sdcard/rime/fonts", name);
    if(f.exists()) return Typeface.createFromFile(f);
    return null;
  }

  public void refresh() {
    Schema schema = Schema.get();
    candidateHighlight = new ColorDrawable(schema.getColor("hilited_candidate_back_color"));
    candidateSeparator = new ColorDrawable(schema.getColor("candidate_separator_color"));
    candidate_text_color = schema.getColor("candidate_text_color");
    comment_text_color = schema.getColor("comment_text_color");
    hilited_candidate_text_color = schema.getColor("hilited_candidate_text_color");
    hilited_comment_text_color = schema.getColor("hilited_comment_text_color");

    candidate_text_size = schema.getInt("candidate_text_size");
    comment_text_size = schema.getInt("comment_text_size");

    tf = getFont(schema.getString("text_font"));
    tfb = getFont(schema.getString("text_font_b"));
    tfl = getFont(schema.getString("text_font_latin"));
  }

  public CandView(Context context, AttributeSet attrs) {
    super(context, attrs);
    refresh();
    tfs = Typeface.createFromAsset(context.getAssets(), "symbol.ttf");
    Resources r = context.getResources();
    paint = new Paint();
    paint.setAntiAlias(true);
    paint.setTextSize(candidate_text_size);
    paint.setStrokeWidth(0);
    paint.setTypeface(tf);

    paintpy = new Paint();
    paintpy.setAntiAlias(true);
    paintpy.setTextSize(comment_text_size);
    paintpy.setStrokeWidth(0);
    paintpy.setTypeface(tfl);

    setWillNotDraw(false);
    mRime = Rime.getRime();
  }
  
  public void setCandViewListener(CandViewListener listener) {
    this.listener = listener;
  }

  /**
   * Highlight the first candidate as the default candidate.
   */
  public void update() {
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
    float size = candidate_text_size;
    if (size != paint.getTextSize()) {
      paint.setTextSize(size);
    }

    if (candidates == null) return;

    final float y = candidateRect[0].centerY() - (paint.ascent() + paintpy.getTextSize() - paint.getTextSize()) / 2;
    float x = 0;
    int i = 0;

    while (i < num_candidates) {
      // Calculate a position where the text could be centered in the rectangle.
      paint.setTypeface(tf);
      paint.setColor(highlightIndex == i ? hilited_candidate_text_color : candidate_text_color);
      paintpy.setColor(highlightIndex == i ? hilited_comment_text_color : comment_text_color);
      drawText(getCandidate(i), canvas, paint, tf, candidateRect[i].centerX(), y);
      drawText(getComment(i), canvas, paintpy, tfl, candidateRect[i].centerX(), - paintpy.ascent());
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
      String candidate = getCandidate(j);
      if (candidate == null) continue;
      paint.setTypeface(tfs);
      x = candidateRect[i].centerX() - paint.measureText(candidate) / 2;
      canvas.drawText(candidate, x, y, paint);
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
    if (mRime.hasLeft()) x += getCandidateWidth(-4);
    getCandNum();
    for (i = 0; i < num_candidates; i++) candidateRect[i] = new Rect(x, top, x += getCandidateWidth(i), bottom);
    if (mRime.hasLeft()) candidateRect[i++] = new Rect(0, top, (int)getCandidateWidth(-4), bottom);
    if (mRime.hasRight()) candidateRect[i++] = new Rect(x, top, x += getCandidateWidth(-5), bottom);
    LayoutParams params = getLayoutParams();
    params.width = x;
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

    if (mRime.hasLeft()) { //Page Up
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        return -4;
      }
    }

    if (mRime.hasRight()) { //Page Down
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      if (r.contains(x, y)) {
        return -5;
      }
    }

    return -1;
  }

  private int getCandNum() {
    mRime = Rime.getRime();
    candidates = mRime.getCandidates();
    highlightIndex = mRime.getCandHighlightIndex();
    num_candidates = candidates == null ? 0 : candidates.length;
    return num_candidates;
  }

  private String getCandidate(int i) {
    String s = null;
    if (candidates != null && i >= 0) s = candidates[i].text;
    else if (i == -4 && mRime.hasLeft()) s = "◀";
    else if (i == -5 && mRime.hasRight()) s = "▶";
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
    if (i >= 0) {
      String comment = getComment(i);
      if (comment != null) {
        float x2 = paintpy.measureText(comment);
        if (x2 > x) x = x2;
      }
    }
    return x;
  }
}
