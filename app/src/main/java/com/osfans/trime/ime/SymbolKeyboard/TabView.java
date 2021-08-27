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

package com.osfans.trime.ime.SymbolKeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import com.osfans.trime.Rime;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.setup.Config;

import timber.log.Timber;


//这是滑动键盘顶部的view，展示了键盘布局的多个标签。
//为了公用候选栏的皮肤参数以及外观，大部分代码从Candidate.java复制而来。
public class TabView extends View {

    private static final int MAX_CANDIDATE_COUNT = 30;
    private static final int CANDIDATE_TOUCH_OFFSET = -12;

    private int highlightIndex;
    private TabTag[] candidates;
    private int num_candidates;

    private Drawable candidateHighlight, candidateSeparator;
    private final Paint paintCandidate;
    private final Paint paintSymbol;
    private final Paint paintComment;
    private Typeface tfCandidate;
    private Typeface tfHanB;
    private Typeface tfLatin;
    private int candidate_text_color, hilited_candidate_text_color;
    private int candidate_view_height, comment_height, candidate_spacing, candidate_padding;
    private final boolean show_comment = true;
    private boolean comment_on_top;
    private boolean candidate_use_cursor;

    private final Rect[] candidateRect = new Rect[MAX_CANDIDATE_COUNT + 2];

    public void reset(Context context) {
        Config config = Config.get(context);
        candidateHighlight = new PaintDrawable(config.getColor("hilited_candidate_back_color"));
        ((PaintDrawable) candidateHighlight).setCornerRadius(config.getFloat("layout/round_corner"));
        candidateSeparator = new PaintDrawable(config.getColor("candidate_separator_color"));
        candidate_spacing = config.getPixel("candidate_spacing");
        candidate_padding = config.getPixel("candidate_padding");

        candidate_text_color = config.getColor("candidate_text_color");
        hilited_candidate_text_color = config.getColor("hilited_candidate_text_color");

        comment_height = config.getPixel("comment_height");

        int candidate_text_size = config.getPixel("candidate_text_size");
        int comment_text_size = config.getPixel("comment_text_size");
        candidate_view_height = config.getPixel("candidate_view_height");

        tfCandidate = config.getFont("candidate_font");
        tfLatin = config.getFont("latin_font");
        tfHanB = config.getFont("hanb_font");
        Typeface tfComment = config.getFont("comment_font");
        Typeface tfSymbol = config.getFont("symbol_font");

        paintCandidate.setTextSize(candidate_text_size);
        paintCandidate.setTypeface(tfCandidate);
        paintSymbol.setTextSize(candidate_text_size);
        paintSymbol.setTypeface(tfSymbol);
        paintComment.setTextSize(comment_text_size);
        paintComment.setTypeface(tfComment);

        comment_on_top = config.getBoolean("comment_on_top");
        candidate_use_cursor = config.getBoolean("candidate_use_cursor");
        invalidate();
    }

    public TabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paintCandidate = new Paint();
        paintCandidate.setAntiAlias(true);
        paintCandidate.setStrokeWidth(0);
        paintSymbol = new Paint();
        paintSymbol.setAntiAlias(true);
        paintSymbol.setStrokeWidth(0);
        paintComment = new Paint();
        paintComment.setAntiAlias(true);
        paintComment.setStrokeWidth(0);

        reset(context);

        setWillNotDraw(false);
    }

    private boolean isHighlighted(int i) {
        return candidate_use_cursor && i >= 0 && i == highlightIndex;
    }

    private void drawHighlight(Canvas canvas) {
        if (isHighlighted(highlightIndex)) {
            candidateHighlight.setBounds(candidateRect[highlightIndex]);
            candidateHighlight.draw(canvas);
        }
    }

    private Typeface getFont(int codepoint, Typeface font) {
        if (tfHanB != Typeface.DEFAULT && Character.isSupplementaryCodePoint(codepoint))
            return tfHanB;
        if (tfLatin != Typeface.DEFAULT && codepoint < 0x2e80) return tfLatin;
        return font;
    }

    private void drawText(
            String s, Canvas canvas, Paint paint, Typeface font, float center, float y) {
        if (s == null) return;
        int length = s.length();
        if (length == 0) return;
        int points = s.codePointCount(0, length);
        float x = center - measureText(s, paint, font) / 2;
        if (tfLatin != Typeface.DEFAULT || (tfHanB != Typeface.DEFAULT && length > points)) {
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

        float x, y;
        int i = 0;

        y = candidateRect[0].centerY() - (paintCandidate.ascent() + paintCandidate.descent()) / 2;
        if (show_comment && comment_on_top) y += (float) comment_height / 2;


        while (i < num_candidates) {
            // Calculate a position where the text could be centered in the rectangle.
            x = candidateRect[i].centerX();

            paintCandidate.setColor(
                    isHighlighted(i) ? hilited_candidate_text_color : candidate_text_color);
            drawText(getCandidate(i), canvas, paintCandidate, tfCandidate, x, y);
            // Draw the separator at the right edge of each candidate.
            candidateSeparator.setBounds(
                    candidateRect[i].right - candidateSeparator.getIntrinsicWidth(),
                    candidateRect[i].top,
                    candidateRect[i].right + candidate_spacing,
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

    public void updateCandidateWidth() {
        final int top = 0;
        final int bottom = getHeight();
        int i;
        int x = 0;

        candidates = TabManager.get().getTabCanditates();
        highlightIndex = TabManager.get().getSelected();
        num_candidates = candidates.length;

        for (i = 0; i < num_candidates; i++) {
            candidateRect[i] = new Rect(x, top, x += getCandidateWidth(i), bottom);
            x += candidate_spacing;
        }
        LayoutParams params = getLayoutParams();
        Timber.i("update, from Height=" + params.height + " width=" + params.width);
        params.width = x;
        params.height = candidate_view_height;
        if (show_comment && comment_on_top) params.height += comment_height;
        Timber.i("update, to Height=" + candidate_view_height + " width=" + x);
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
    public boolean onTouchEvent(MotionEvent me) {
        int action = me.getAction();
        int x = (int) me.getX();
        int y = (int) me.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                x0 = x;
                y0 = y;
                time0 = System.currentTimeMillis();
//        updateHighlight(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (x - x0 > 100 || x0 - x > 100)
                    time0 = 0;
                break;
            case MotionEvent.ACTION_UP:
                int i = getCandidateIndex(x, y);
                if (i > -1) {
                    performClick();
                    TabTag tag = TabManager.getTag(i);
                    if (tag.type == SymbolKeyboardType.NO_KEY) {
                        switch (tag.command) {
                            case EXIT:
                                Rime.toggleOption("_liquid_keyboard");
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
                        Rime.toggleOption("_liquid_keyboard_" + i);
                    }
                    Timber.d("index=" + i + " length=" + candidates.length);
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
        return -1;
    }

    private String getCandidate(int i) {
        if (candidates != null && i >= 0)
            return candidates[i].text;
        return "-1";
    }


    private float measureText(String s, Paint paint, Typeface font) {
        float x = 0;
        if (s == null) return x;
        int length = s.length();
        if (length == 0) return x;
        int points = s.codePointCount(0, length);
        if (tfLatin != Typeface.DEFAULT || (tfHanB != Typeface.DEFAULT && length > points)) {
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
        float x = 2 * candidate_padding;
        if (s != null) x += measureText(s, paintCandidate, tfCandidate);
        return x;
    }
}
