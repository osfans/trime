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
import com.osfans.trime.core.CandidateListItem;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.theme.FontManager;
import com.osfans.trime.data.theme.Theme;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.util.DimensionsKt;
import com.osfans.trime.util.GraphicUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** 顯示候選字詞 */
public class Candidate extends View {

  /** 處理候選條選字事件 */
  public interface EventListener {
    void onCandidatePressed(int index);

    void onCandidateSymbolPressed(String arrow);

    void onCandidateLongClicked(int index);
  }

  private static final int MAX_CANDIDATE_COUNT = 30;
  public static final String PAGE_UP_BUTTON = "◀";
  public static final String PAGE_DOWN_BUTTON = "▶";
  public static final String PAGE_EX_BUTTON = "▼";
  private int expectWidth = 0;

  private WeakReference<EventListener> listener = new WeakReference<>(null);
  private int highlightIndex = -1;
  private CandidateListItem[] candidates;
  private final ArrayList<ComputedCandidate> computedCandidates =
      new ArrayList<>(MAX_CANDIDATE_COUNT);
  private int numCandidates;
  private int startNum = 0;
  private long timeDown = 0, timeMove = 0;

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

  @NonNull
  private AppPrefs getAppPrefs() {
    return AppPrefs.defaultInstance();
  }

  public void reset() {
    Theme theme = Theme.get();
    candidateHighlight = new PaintDrawable(theme.colors.getColor("hilited_candidate_back_color"));
    candidateHighlight.setCornerRadius(theme.style.getFloat("layout/round_corner"));
    separatorPaint.setColor(theme.colors.getColor("candidate_separator_color"));
    candidateSpacing = (int) DimensionsKt.dp2px(theme.style.getFloat("candidate_spacing"));
    candidatePadding = (int) DimensionsKt.dp2px(theme.style.getFloat("candidate_padding"));

    candidateTextColor = theme.colors.getColor("candidate_text_color");
    commentTextColor = theme.colors.getColor("comment_text_color");
    hilitedCandidateTextColor = theme.colors.getColor("hilited_candidate_text_color");
    hilitedCommentTextColor = theme.colors.getColor("hilited_comment_text_color");

    int candidate_text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("candidate_text_size"));
    int comment_text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("comment_text_size"));
    candidateViewHeight = (int) DimensionsKt.dp2px(theme.style.getFloat("candidate_view_height"));
    commentHeight = (int) DimensionsKt.dp2px(theme.style.getFloat("comment_height"));

    candidateFont = FontManager.getTypeface(theme.style.getString("candidate_font"));
    commentFont = FontManager.getTypeface(theme.style.getString("comment_font"));
    symbolFont = FontManager.getTypeface(theme.style.getString("symbol_font"));

    candidatePaint.setTextSize(candidate_text_size);
    candidatePaint.setTypeface(candidateFont);
    symbolPaint.setTextSize(candidate_text_size);
    symbolPaint.setTypeface(symbolFont);
    commentPaint.setTextSize(comment_text_size);
    commentPaint.setTypeface(commentFont);

    isCommentOnTop = theme.style.getBoolean("comment_on_top");
    candidateUseCursor = theme.style.getBoolean("candidate_use_cursor");
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

    // reset(context);

    setWillNotDraw(false);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int h =
        (shouldShowComment && isCommentOnTop)
            ? candidateViewHeight + commentHeight
            : candidateViewHeight;
    setMeasuredDimension(
        MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
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

  public void setExpectWidth(int expectWidth) {
    this.expectWidth = expectWidth;
  }

  /**
   * 選取候選項
   *
   * @param index 候選項序號（從0開始），{@code -1}表示選擇當前高亮候選項
   */
  private void onCandidateClick(int index, boolean isLongClick) {
    ComputedCandidate candidate;
    if (index >= 0 && index < computedCandidates.size()) {
      candidate = computedCandidates.get(index);
      if (candidate != null) {
        if (candidate instanceof ComputedCandidate.Word) {
          if (listener.get() != null) {
            if (isLongClick && getAppPrefs().getKeyboard().getShouldLongClickDeleteCandidate()) {
              listener.get().onCandidateLongClicked(index + startNum);
            } else {
              listener.get().onCandidatePressed(index + startNum);
            }
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

    boolean moveAllCandidatesDown = false;
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
          moveAllCandidatesDown |= comment != null && !comment.isEmpty();
          if (moveAllCandidatesDown) wordY += commentHeight / 2.0f;
          if (comment != null && !comment.isEmpty()) {
            float commentX = computedCandidate.getGeometry().centerX();
            float commentY =
                commentHeight / 2.0f - (commentPaint.ascent() + commentPaint.descent()) / 2;
            if (!isCommentOnTop) {
              float commentWidth = GraphicUtils.measureText(commentPaint, comment, commentFont);
              commentX = computedCandidate.getGeometry().right - commentWidth / 2;
              commentY += computedCandidates.get(0).getGeometry().bottom - commentHeight;
              wordX -= commentWidth / 2.0f;
              wordY -= commentHeight / 2.0f;
            }
            commentPaint.setColor(isHighlighted(i) ? hilitedCommentTextColor : commentTextColor);
            GraphicUtils.drawText(canvas, comment, commentX, commentY, commentPaint, commentFont);
          }
        }
        String word = ((ComputedCandidate.Word) computedCandidate).getWord();
        candidatePaint.setColor(isHighlighted(i) ? hilitedCandidateTextColor : candidateTextColor);
        GraphicUtils.drawText(canvas, word, wordX, wordY, candidatePaint, candidateFont);
      } else if (computedCandidate instanceof ComputedCandidate.Symbol) {
        // Draw page up / down buttons
        String arrow = ((ComputedCandidate.Symbol) computedCandidate).getArrow();
        float arrowX =
            computedCandidate.getGeometry().centerX()
                - GraphicUtils.measureText(symbolPaint, arrow, symbolFont) / 2;
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
    boolean hasExButton = false;
    int pageEx =
        Integer.parseInt(AppPrefs.defaultInstance().getKeyboard().getCandidatePageSize()) - 10000;
    int pageBottonWidth =
        (int)
            (candidateSpacing
                + GraphicUtils.measureText(symbolPaint, PAGE_DOWN_BUTTON, symbolFont)
                + 2 * candidatePadding);
    int minWidth;
    if (pageEx > 2) minWidth = (int) (expectWidth * (pageEx / 10f + 1) - pageBottonWidth);
    else if (pageEx == 2) minWidth = (expectWidth - pageBottonWidth * 2);
    else minWidth = expectWidth - pageBottonWidth;

    computedCandidates.clear();
    updateCandidates();
    int x = (!Rime.hasLeft()) ? 0 : pageBottonWidth;
    for (int i = 0; i < numCandidates; i++) {
      int n = i + startNum;

      if (pageEx >= 0) {
        if (x >= minWidth) {
          computedCandidates.add(
              new ComputedCandidate.Symbol(
                  PAGE_EX_BUTTON, new Rect(x, 0, (x + pageBottonWidth), getMeasuredHeight())));
          x += pageBottonWidth;
          hasExButton = true;
          break;
        }
      }
      String comment = null, text = candidates[n].getText();
      float candidateWidth =
          GraphicUtils.measureText(candidatePaint, text, candidateFont) + 2 * candidatePadding;

      if (shouldShowComment) {
        comment = candidates[n].getComment();
        if (!TextUtils.isEmpty(comment)) {
          float commentWidth = GraphicUtils.measureText(commentPaint, comment, commentFont);
          candidateWidth =
              isCommentOnTop
                  ? Math.max(candidateWidth, commentWidth)
                  : candidateWidth + commentWidth;
        }
      }

      // 自动填满候选栏，并保障展开候选按钮显示出来
      if (pageEx == 0 && x + candidateWidth + candidateSpacing > minWidth) {
        computedCandidates.add(
            new ComputedCandidate.Symbol(
                PAGE_EX_BUTTON, new Rect(x, 0, (x + pageBottonWidth), getMeasuredHeight())));
        x += pageBottonWidth;
        hasExButton = true;
        break;
      }

      computedCandidates.add(
          new ComputedCandidate.Word(
              text, comment, new Rect(x, 0, (int) (x + candidateWidth), getMeasuredHeight())));
      x += candidateWidth + candidateSpacing;
    }
    if (Rime.hasLeft()) {
      computedCandidates.add(
          new ComputedCandidate.Symbol(
              PAGE_UP_BUTTON, new Rect(0, 0, pageBottonWidth, getMeasuredHeight())));
    }
    if (Rime.hasRight()) {
      computedCandidates.add(
          new ComputedCandidate.Symbol(
              PAGE_DOWN_BUTTON, new Rect(x, 0, (x + pageBottonWidth), getMeasuredHeight())));
      x += pageBottonWidth;
    }

    LayoutParams params = getLayoutParams();
    params.width = x;
    params.height =
        (shouldShowComment && isCommentOnTop)
            ? candidateViewHeight + commentHeight
            : candidateViewHeight;
    setLayoutParams(params);

    Trime.getService().setCandidateExPage(hasExButton);
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
        timeDown = System.currentTimeMillis();
      case MotionEvent.ACTION_MOVE:
        setPressed(true);
        highlightIndex = getCandidateIndex(x, y);
        invalidate();
        break;
        // updateHighlight(x, y);
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        timeMove = System.currentTimeMillis();
        long durationMs = timeMove - timeDown;
        setPressed(false);
        if (me.getActionMasked() == MotionEvent.ACTION_UP) {
          onCandidateClick(
              highlightIndex,
              durationMs >= getAppPrefs().getKeyboard().getDeleteCandidateTimeout());
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
    candidates = Rime.getCandidatesOrStatusSwitches();
    highlightIndex = Rime.getCandHighlightIndex() - startNum;
    numCandidates = candidates == null ? 0 : candidates.length - startNum;
    return numCandidates;
  }
}
