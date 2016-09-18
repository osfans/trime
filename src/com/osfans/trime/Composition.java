/*
 * Copyright 2015 osfans
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
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.graphics.drawable.GradientDrawable;

import android.util.Log;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.style.*;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置 */
public class Composition extends TextView {
  private int text_size, candidate_text_size, comment_text_size;
  private int text_color, candidate_text_color, comment_text_color;
  private int hilited_text_color, hilited_candidate_text_color, hilited_comment_text_color;
  private int back_color, hilited_back_color, hilited_candidate_back_color;
  private int[] positions = new int[20];
  private int page_up, page_down;

  public Composition(Context context, AttributeSet attrs) {
    super(context, attrs);
    reset();
  }

  private int getLineForPostion(int n) {
    if (n < 0) return -1;
    int i = 0;
    while(i < 20 && positions[i] < n) {
      i++;
    }
    if (i == 20) {
      if (n < page_up) return -4;
      if (n < page_down) return -5;
    }
    return i;
  }

  public boolean onTouchEvent(MotionEvent event) {
    if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        int n = getOffsetForPosition(event.getX(),event.getY());
        int i = getLineForPostion(n);
        if (i == 0) {
          String s = getText().toString().substring(0, n).replace(" ", "");
          n = s.length();
          Rime.RimeSetCaretPos(n);
          Trime.getService().updateComposing();
        } else if (i < 0) {
          //Trime.getService().onKey(i == -4 ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN, 0);
        } else if (i > 0){
          Trime.getService().onPickCandidate(i - 1);
        }
      }
    }
    return true;
  }

  public void reset() {
    Config config = Config.get();
    text_size = config.getPixel("text_size");
    candidate_text_size = config.getPixel("candidate_text_size");
    comment_text_size = config.getPixel("comment_text_size");

    text_color = config.getColor("text_color");
    candidate_text_color = config.getColor("candidate_text_color");
    comment_text_color = config.getColor("comment_text_color");
    hilited_text_color = config.getColor("hilited_text_color");
    hilited_candidate_text_color = config.getColor("hilited_candidate_text_color");
    hilited_comment_text_color = config.getColor("hilited_comment_text_color");
    
    back_color = config.getColor("back_color");
    hilited_back_color = config.getColor("hilited_back_color");
    hilited_candidate_back_color = config.getColor("hilited_candidate_back_color");
    
    float line_spacing_multiplier = config.getFloat("layout/line_spacing_multiplier");
    if (line_spacing_multiplier == 0f) line_spacing_multiplier = 1f;
    setLineSpacing(config.getFloat("layout/line_spacing"), line_spacing_multiplier);
    setMinWidth(config.getPixel("layout/min_width"));
    setMinHeight(config.getPixel("layout/min_height"));
    setMaxWidth(config.getPixel("layout/max_width"));
    setMaxHeight(config.getPixel("layout/max_height"));
    int margin_x, margin_y;
    margin_x = config.getPixel("layout/margin_x");
    margin_y = config.getPixel("layout/margin_y");
    setPadding(margin_x, margin_y, margin_x, margin_y);
    boolean show = config.getBoolean("show_text");
    setVisibility(show ? View.VISIBLE : View.GONE);
  }

  public int setCompositionText(int length) {
    if (getVisibility() != View.VISIBLE) return 0;
    Rime.RimeComposition r = Rime.getComposition();
    if (r == null) return 0;
    String s = r.getText();
    if (Function.isEmpty(s)) return 0;
    SpannableStringBuilder ss = new SpannableStringBuilder();
    int span = 0; //SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE;
    int start, end;
    start = ss.length();
    ss.append(s);
    end = ss.length();
    ss.setSpan(new AbsoluteSizeSpan(text_size), start, end, span);
    ss.setSpan(new ForegroundColorSpan(text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(back_color), start, end, span);
    start = r.getStart();
    end = r.getEnd();
    ss.setSpan(new ForegroundColorSpan(hilited_text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(hilited_back_color), start, end, span);
    int i = 0;
    positions[i] = ss.length();
    Rime.RimeCandidate[] candidates = Rime.getCandidates();
    if (candidates != null) {
      int highlightIndex = Rime.getCandHighlightIndex();
      for (Rime.RimeCandidate o: candidates) {
        String cand = o.text;
        if (cand.length() < length) break;
        start = ss.length();
        ss.append("\n" + cand);
        end = ss.length();
        ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
        if (i == highlightIndex) {
          ss.setSpan(new ForegroundColorSpan(hilited_candidate_text_color), start, end, span);
          ss.setSpan(new BackgroundColorSpan(hilited_candidate_back_color), start, end, span);
        } else {
          ss.setSpan(new ForegroundColorSpan(candidate_text_color), start, end, span);
        }
        String comment = o.comment;
        if (!Function.isEmpty(comment)) {
          start = ss.length();
          ss.append(" " + comment);
          end = ss.length();
          ss.setSpan(new AbsoluteSizeSpan(comment_text_size), start, end, span);
          if (i == highlightIndex) {
            ss.setSpan(new ForegroundColorSpan(hilited_comment_text_color), start, end, span);
            ss.setSpan(new BackgroundColorSpan(hilited_candidate_back_color), start, end, span);
          } else {
            ss.setSpan(new ForegroundColorSpan(comment_text_color), start, end, span);
          }
        }
        positions[++i] = ss.length();
      }
      //if (Rime.hasLeft()) {ss.append("  ◀  "); page_up = ss.length();}
      //if (Rime.hasRight()) {ss.append("  ▶  "); page_down = ss.length();}
    }
    setText(ss);
    return i;
  }
}
