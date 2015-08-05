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
import android.widget.LinearLayout;
import android.util.Log;

/**
 * Contains all candidates in pages where users could move forward (next page)
 * or move backward (previous) page to select one of these candidates. 
 */
public class CandContainer extends LinearLayout {

  private CandView candidateView;
  private TextView text;

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
          String s = text.getText().toString().substring(0, n).replaceAll("[ ‸]", "");
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
    text.setTextSize(config.getInt("text_size"));
    text.setHeight(config.getPixel("text_height"));
    //text.setBackgroundColor(config.getColor("back_color"));
    text.setTypeface(config.getFont("text_color"));
    boolean show = config.getBoolean("show_text");
    text.setVisibility(show ? View.VISIBLE : View.GONE);
    show = config.getBoolean("show_candidate");
    candidateView.setVisibility(show ? View.VISIBLE : View.GONE);
    candidateView.refresh();
  }

  public void setCandViewListener(
      CandView.CandViewListener listener) {
    candidateView.setCandViewListener(listener);
  }

  public void updatePage() {
    candidateView.update();
    text.setText(Rime.getRime().getCompositionText());
  }
}
