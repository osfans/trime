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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.osfans.trime.Rime;
import com.osfans.trime.setup.Config;
import com.osfans.trime.util.GraphicUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** 顯示候選字詞 */
public class Candidate extends View {

  /** 處理候選條選字事件 */
  public interface EventListener {
    void onCandidatePressed(int index);

    void onCandidateSymbolPressed(String arrow);
  }

  private static final int MAX_CANDIDATE_COUNT = 30;
  public static final String PAGE_UP_BUTTON = "◀";
  public static final String PAGE_DOWN_BUTTON = "▶";
  // private static final int CANDIDATE_TOUCH_OFFSET = -12;

  private WeakReference<EventListener> listener = new WeakReference<>(null);
  private final GraphicUtils graphicUtils;
  private int highlightIndex = -1;
  private Rime.RimeCandidate[] candidates;
  private final ArrayList<ComputedCandidate> computedCandidates =
      new ArrayList<>(MAX_CANDIDATE_COUNT);
  private int numCandidates;
  private int startNum = 0;

  private PaintDrawable candidateHighlight;
  private final Paint separatorPaint;
  private final Paint candidatePaint;
  private final Paint symbolPaint;
  private final Paint commentPaint;
  private Typeface candidateFont, symbolFont, commentFont;
  private int candidateTextColor, hilitedCandidateTextColor;
  private int commentTextColor, hilitedCommentTextColor;
  private int candidateViewHeight, commentHeight, candidateSpacing, candidatePadding;
  private boolean shouldShowComment = true, isCommentOnTop, candidateUseCursor;

  public void reset(Context context) {
    Config config = Config.get(context);
    candidateHighlight = new PaintDrawable(config.getColor("hilited_candidate_back_color"));
    candidateHighlight.setCornerRadius(config.getFloat("layout/round_corner"));
    separatorPaint.setColor(config.getColor("candidate_separator_color"));
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

    candidateFont = config.getFont("candidate_font");
    commentFont = config.getFont("comment_font");
    symbolFont = config.getFont("symbol_font");

    candidatePaint.setTextSize(candidate_text_size);
    candidatePaint.setTypeface(candidateFont);
    symbolPaint.setTextSize(candidate_text_size);
    symbolPaint.setTypeface(symbolFont);
    commentPaint.setTextSize(comment_text_size);
    commentPaint.setTypeface(commentFont);

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

    separatorPaint = new Paint();
    separatorPaint.setColor(Color.BLACK);

    graphicUtils = new GraphicUtils(context);

    // reset(context);

    setWillNotDraw(false);
  }

  public static int getMaxCandidateCount() {
    return MAX_CANDIDATE_COUNT;
  }

  public void setCandidateListener(@Nullable EventListener listener) {
    this.listener = new WeakReference<>(listener);
  }

  /**
   * 刷新候選列表
   *
   * @param start 候選的起始編號
   */
  public void setText(int start) {
    startNum = start;
    removeHighlight();
    updateCandidateWidth();
    if (updateCandidates() > 0) {
      invalidate();
    }
  }

  /**
   * 選取候選項
   *
   * @param index 候選項序號（從0開始），{@code -1}表示選擇當前高亮候選項
   * @return 是否成功選字
   */
  private void onCandidateClick(int index) {
    ComputedCandidate candidate = null;
    if (index >= 0 && index < computedCandidates.size()) {
      candidate = computedCandidates.get(index);
      if (candidate != null) {
        if (candidate instanceof ComputedCandidate.Word) {
          if (listener.get() != null) {
            listener.get().onCandidatePressed(index + startNum);
          }
        }
        if (candidate instanceof ComputedCandidate.Symbol) {
          String arrow = ((ComputedCandidate.Symbol) candidate).getArrow();
          if (listener.get() != null) {
            listener.get().onCandidateSymbolPressed(arrow);
          }
        }
      }
    }
  }

  private void removeHighlight() {
    highlightIndex = -1;
    invalidate();
    requestLayout();
  }

  private boolean isHighlighted(int i) {
    return candidateUseCursor && i == highlightIndex;
  }

  public int getHighlightLeft() {
    if (highlightIndex < computedCandidates.size() && highlightIndex >= 0)
      return computedCandidates.get(highlightIndex).getGeometry().left;
    return 0;
  }

  public int getHighlightRight() {
    if (highlightIndex < computedCandidates.size() && highlightIndex >= 0)
      return computedCandidates.get(highlightIndex).getGeometry().right;
    return 0;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (canvas == null) return;
    if (candidates == null) return;
    super.onDraw(canvas);

    for (ComputedCandidate computedCandidate : computedCandidates) {
      int i = computedCandidates.indexOf(computedCandidate);
      // Draw highlight
      if (candidateUseCursor && i == highlightIndex) {
        candidateHighlight.setBounds(computedCandidates.get(i).getGeometry());
        candidateHighlight.draw(canvas);
      }
      // Draw candidates
      if (computedCandidate instanceof ComputedCandidate.Word) {
        float wordX = computedCandidate.getGeometry().centerX();
        float wordY =
            computedCandidates.get(0).getGeometry().centerY()
                - (candidatePaint.ascent() + candidatePaint.descent()) / 2;
        if (shouldShowComment) {
          String comment = ((ComputedCandidate.Word) computedCandidate).getComment();
          if (comment != null && !comment.isEmpty()) {
            float commentX = computedCandidate.getGeometry().centerX();
            float commentY =
                commentHeight / 2.0f - (commentPaint.ascent() + commentPaint.descent()) / 2;
            wordY += commentHeight / 2.0f;
            if (!isCommentOnTop) {
              float commentWidth = graphicUtils.measureText(commentPaint, comment, commentFont);
              commentX = computedCandidate.getGeometry().right - commentWidth / 2;
              commentY += computedCandidates.get(0).getGeometry().bottom - commentHeight;
              wordX -= commentWidth / 2.0f;
              wordY -= commentHeight / 2.0f;
            }
            commentPaint.setColor(isHighlighted(i) ? hilitedCommentTextColor : commentTextColor);
            graphicUtils.drawText(canvas, comment, commentX, commentY, commentPaint, commentFont);
          }
        }
        String word = ((ComputedCandidate.Word) computedCandidate).getWord();
        candidatePaint.setColor(isHighlighted(i) ? hilitedCandidateTextColor : candidateTextColor);
        graphicUtils.drawText(canvas, word, wordX, wordY, candidatePaint, candidateFont);
      } else if (computedCandidate instanceof ComputedCandidate.Symbol) {
        // Draw page up / down buttons
        String arrow = ((ComputedCandidate.Symbol) computedCandidate).getArrow();
        float arrowX =
            computedCandidate.getGeometry().centerX()
                - graphicUtils.measureText(symbolPaint, arrow, symbolFont) / 2;
        float arrowY =
            computedCandidates.get(0).getGeometry().centerY()
                - (candidatePaint.ascent() + candidatePaint.descent()) / 2;
        symbolPaint.setColor(isHighlighted(i) ? hilitedCommentTextColor : commentTextColor);
        canvas.drawText(arrow, arrowX, arrowY, symbolPaint);
      }
      // Draw separators
      if (i + 1 < computedCandidates.size()) {
        canvas.drawRect(
            computedCandidate.getGeometry().right - candidateSpacing,
            computedCandidate.getGeometry().height() * 0.2f,
            computedCandidate.getGeometry().right + candidateSpacing,
            computedCandidate.getGeometry().height() * 0.8f,
            separatorPaint);
      }
    }
  }

  private void updateCandidateWidth() {
    computedCandidates.clear();
    updateCandidates();
    int x =
        (!Rime.hasLeft())
            ? 0
            : (int)
                (2 * candidatePadding
                    + graphicUtils.measureText(symbolPaint, PAGE_UP_BUTTON, symbolFont)
                    + candidateSpacing);
    for (int i = 0; i < numCandidates; i++) {
      int n = i + startNum;
      float candidateWidth =
          graphicUtils.measureText(candidatePaint, candidates[n].text, candidateFont)
              + 2 * candidatePadding;
      if (shouldShowComment) {
        String comment = candidates[n].comment;
        if (!TextUtils.isEmpty(comment)) {
          float commentWidth = graphicUtils.measureText(commentPaint, comment, commentFont);
          candidateWidth =
              isCommentOnTop
                  ? Math.max(candidateWidth, commentWidth)
                  : candidateWidth + commentWidth;
        }
      }
      computedCandidates.add(
          new ComputedCandidate.Word(
              candidates[n].text,
              candidates[n].comment,
              new Rect(x, 0, (int) (x + candidateWidth), getMeasuredHeight())));
      x += candidateWidth + candidateSpacing;
    }
    if (Rime.hasLeft()) {
      float right =
          candidateSpacing
              + graphicUtils.measureText(symbolPaint, PAGE_UP_BUTTON, symbolFont)
              + 2 * candidatePadding;
      computedCandidates.add(
          new ComputedCandidate.Symbol(
              PAGE_UP_BUTTON, new Rect(0, 0, (int) right, getMeasuredHeight())));
    }
    if (Rime.hasRight()) {
      float right =
          candidateSpacing
              + graphicUtils.measureText(symbolPaint, PAGE_DOWN_BUTTON, symbolFont)
              + 2 * candidatePadding;
      computedCandidates.add(
          new ComputedCandidate.Symbol(
              PAGE_DOWN_BUTTON, new Rect(x, 0, (int) ((int) x + right), getMeasuredHeight())));
      x += (int) right;
    }
    LayoutParams params = getLayoutParams();
    params.width = x;
    params.height =
        (shouldShowComment && isCommentOnTop)
            ? candidateViewHeight + commentHeight
            : candidateViewHeight;
    setLayoutParams(params);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateCandidateWidth();
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(@NonNull MotionEvent me) {
    int x = (int) me.getX();
    int y = (int) me.getY();

    switch (me.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_MOVE:
        setPressed(true);
        highlightIndex = getCandidateIndex(x, y);
        invalidate();
        break;
        // updateHighlight(x, y);
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        setPressed(false);
        if (me.getActionMasked() == MotionEvent.ACTION_UP) {
          onCandidateClick(highlightIndex);
        }
        highlightIndex = -1;
        invalidate();
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
    // Rect r = new Rect();
    int retIndex = -1;
    for (ComputedCandidate computedCandidate : computedCandidates) {
      /*
       Enlarge the rectangle to be more responsive to user clicks.
      r.set(candidateRect[j++]);
      r.inset(0, CANDIDATE_TOUCH_OFFSET);
      */
      if (computedCandidate.getGeometry().contains(x, y)) {
        retIndex = computedCandidates.indexOf(computedCandidate);
        break;
      }
    }
    return retIndex;
  }

  private int updateCandidates() {
    candidates = Rime.getCandidates();
    highlightIndex = Rime.getCandHighlightIndex() - startNum;
    numCandidates = candidates == null ? 0 : candidates.length - startNum;
    return numCandidates;
  }
}
