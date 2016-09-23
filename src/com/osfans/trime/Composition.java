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
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;

import java.util.Map;
import java.util.List;

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置 */
public class Composition extends TextView {
  private int text_size, candidate_text_size, comment_text_size;
  private int text_color, candidate_text_color, comment_text_color;
  private int hilited_text_color, hilited_candidate_text_color, hilited_comment_text_color;
  private int back_color, hilited_back_color, hilited_candidate_back_color;
  private int key_text_size, key_text_color, key_back_color;
  private int composition_start, composition_end;
  private int max_entries = Candidate.MAX_CANDIDATE_COUNT;
  private boolean candidate_use_cursor = true;
  private int highlightIndex;
  private List<Map<String,Object>> components;
  private SpannableStringBuilder ss;
  private int span = 0;

  private class CandidateSpan extends ClickableSpan{
      int index;
      public CandidateSpan(int i) {
          super();
          index = i;
      }
      @Override
      public void onClick(View tv) {
         Trime.getService().onPickCandidate(index);
      }
      @Override
      public void updateDrawState(TextPaint ds) {
          ds.setUnderlineText(false);
      }
  }

  private class EventSpan extends ClickableSpan{
      Event event;
      public EventSpan(Event e) {
          super();
          event = e;
      }

      @Override
      public void onClick(View tv) {
         Trime.getService().onEvent(event);
      }
      @Override
      public void updateDrawState(TextPaint ds) {
          ds.setUnderlineText(false);
      }
  }

  public Composition(Context context, AttributeSet attrs) {
    super(context, attrs);
    reset();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_UP) {
      int n = getOffsetForPosition(event.getX(),event.getY());
      if (composition_start <= n && n <= composition_end) {
        String s = getText().toString().substring(0, n).replace(" ", "");
        n = s.length();
        Rime.RimeSetCaretPos(n);
        Trime.getService().updateComposing();
        return true;
      }
    }
    return super.onTouchEvent(event);
  }


  public void reset() {
    Config config = Config.get();
    components = (List<Map<String,Object>>)config.getValue("window");
    if (config.hasKey("layout/max_entries")) max_entries = config.getInt("layout/max_entries");
    candidate_use_cursor = config.getBoolean("candidate_use_cursor");
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
    
    key_text_size = config.getPixel("key_text_size");
    key_text_color = config.getColor("key_text_color");
    key_back_color = config.getColor("key_back_color");
    
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
    boolean show = config.getBoolean("show_window");
    setVisibility(show ? View.VISIBLE : View.GONE);
  }

  private void appendComposition(Map m) {
    Rime.RimeComposition r = Rime.getComposition();
    String s = r.getText();
    String format = (String)m.get("composition");
    String sep = (String) m.get("start");
    if (!Function.isEmpty(sep)) ss.append(sep);
    int start, end;
    start = ss.length();
    ss.append(s);
    end = ss.length();
    composition_start = start;
    composition_end = end;
    ss.setSpan(new AbsoluteSizeSpan(text_size), start, end, span);
    ss.setSpan(new ForegroundColorSpan(text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(back_color), start, end, span);
    start = composition_start + r.getStart();
    end = composition_start + r.getEnd();
    ss.setSpan(new ForegroundColorSpan(hilited_text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(hilited_back_color), start, end, span);
    sep = (String) m.get("end");
    if (!Function.isEmpty(sep))ss.append(sep);
  }

  private int appendCandidates(Map m, int length) {
    int start, end;
    int i = 0;
    Rime.RimeCandidate[] candidates = Rime.getCandidates();
    if (candidates == null) return i;
    String sep = (String) m.get("start");
    highlightIndex = candidate_use_cursor ? Rime.getCandHighlightIndex() : -1;
    String candidate_format = (String) m.get("candidate");
    String comment_format = (String) m.get("comment");
    String line = (String) m.get("sep");
    for (Rime.RimeCandidate o: candidates) {
      String cand = o.text;
      if (i >= max_entries || cand.length() < length) break;
      String line_sep = i == 0 ? sep : line;
      if (!Function.isEmpty(line_sep))ss.append(line_sep);
      start = ss.length();
      ss.append(String.format(candidate_format, cand));
      end = ss.length();
      ss.setSpan(new CandidateSpan(i), start, end, span);
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
        ss.append(String.format(comment_format, comment));
        end = ss.length();
        ss.setSpan(new CandidateSpan(i), start, end, span);
        ss.setSpan(new AbsoluteSizeSpan(comment_text_size), start, end, span);
        if (i == highlightIndex) {
          ss.setSpan(new ForegroundColorSpan(hilited_comment_text_color), start, end, span);
          ss.setSpan(new BackgroundColorSpan(hilited_candidate_back_color), start, end, span);
        } else {
          ss.setSpan(new ForegroundColorSpan(comment_text_color), start, end, span);
        }
      }
      i++;
    }
    sep = (String) m.get("end");
    if (!Function.isEmpty(sep)) ss.append(sep);
    return i;
  }

  private void appendButton(Map m) {
    if (m.containsKey("when")) {
      String when = (String)m.get("when");
      if (when.contentEquals("paging") && !Rime.isPaging()) return;
      if (when.contentEquals("has_menu") && !Rime.hasMenu()) return;
    }
    String label;
    Event e = new Event(null, (String)m.get("click"));
    if (m.containsKey("label")) label = (String)m.get("label");
    else label = e.getLabel();
    String sep = (String) m.get("start");
    if (!Function.isEmpty(sep)) ss.append(sep);
    int start, end;
    start = ss.length();
    ss.append(label);
    end = ss.length();
    ss.setSpan(new EventSpan(e), start, end, span);
    ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
    ss.setSpan(new ForegroundColorSpan(key_text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(key_back_color), start, end, span);
    sep = (String) m.get("end");
    if (!Function.isEmpty(sep)) ss.append(sep);
  }

  public int setWindow(int length) {
    if (getVisibility() != View.VISIBLE) return 0;
    Rime.RimeComposition r = Rime.getComposition();
    if (r == null) return 0;
    String s = r.getText();
    if (Function.isEmpty(s)) return 0;
    ss = new SpannableStringBuilder();
    int i = 0;
    for (Map<String,Object> m: components) {
      if (m.containsKey("composition")) appendComposition(m);
      else if (m.containsKey("candidate")) i = appendCandidates(m, length);
      else if (m.containsKey("click"))appendButton(m);
    }
    setText(ss);
    setMovementMethod(LinkMovementMethod.getInstance());
    return i;
  }
}
