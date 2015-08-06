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
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.util.Log;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;

/**
 * Contains all candidates in pages where users could move forward (next page)
 * or move backward (previous) page to select one of these candidates. 
 */
public class CandContainer extends RelativeLayout {

  private CandView candidateView;
  private TextView text;
  private boolean soft_cursor;
  private String soft_cursor_text;
  private static String caret = "‸";
  private ForegroundColorSpan hilited_text_color_span;
  private BackgroundColorSpan hilited_back_color_span;
  private UnderlineSpan underline_span = new UnderlineSpan();

  public CandContainer(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  
  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    candidateView = (CandView) findViewById(R.id.candidate_view);
    text = (TextView) findViewById(R.id.text);
    text.setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
        int n = text.getOffsetForPosition(event.getX(),event.getY());
        if (event.getAction() == MotionEvent.ACTION_UP && n >= 0) {
          String s = text.getText().toString().substring(0, n);
          if (soft_cursor) s = s.replace(soft_cursor_text, "").replace(" ", "");
          n = s.length();
          Rime.getRime().RimeSetCaretPos(n);
          Trime.getService().updateComposing();
        }
        return true;
      }
    });
    refresh();
  }

  public void refresh() {
    Config config = Config.get();
    setBackgroundColor(config.getColor("back_color"));
    text.setTextColor(config.getColor("text_color"));
    hilited_text_color_span = new ForegroundColorSpan(config.getColor("hilited_text_color"));
    hilited_back_color_span = new BackgroundColorSpan(config.getColor("hilited_back_color"));
    text.setTextSize(config.getInt("text_size"));
    text.setHeight(config.getPixel("text_height"));
    //text.setBackgroundColor(config.getColor("back_color"));
    text.setTypeface(config.getFont("text_color"));
    boolean show = config.getBoolean("show_text");
    text.setVisibility(show ? View.VISIBLE : View.GONE);
    show = config.getBoolean("show_candidate");
    soft_cursor = config.getBoolean("soft_cursor");
    soft_cursor_text = config.getString("soft_cursor_text");
    if (soft_cursor_text == null || soft_cursor_text.isEmpty()) soft_cursor_text = caret;
    candidateView.setVisibility(show ? View.VISIBLE : View.GONE);
    candidateView.refresh();
  }

  public void setCandViewListener(
      CandView.CandViewListener listener) {
    candidateView.setCandViewListener(listener);
  }

  public void updatePage() {
    candidateView.update();
    Rime.RimeComposition r = Rime.getRime().getComposition();
    String s = "";
    if (r != null) s = r.getText();
    if (soft_cursor) s = s.replace(caret, soft_cursor_text);
    text.setText(s);
    int n = s.length();
    if (n > 0 && r != null) {
      SpannableString ss = (SpannableString)text.getText();
      int start = r.getStart();
      int end = r.getEnd();
      ss.setSpan(hilited_text_color_span, start, end, SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
      ss.setSpan(hilited_back_color_span, start, end, SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
      ss.setSpan(underline_span, start, end, SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
    }
  }
}
