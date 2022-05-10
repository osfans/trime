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
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.Config;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.keyboard.Event;
import com.osfans.trime.util.ConfigGetter;
import java.util.List;
import java.util.Map;
import timber.log.Timber;

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置 */
public class Composition extends AppCompatTextView {
  private int key_text_size, text_size, label_text_size, candidate_text_size, comment_text_size;
  private int key_text_color, text_color, label_color, candidate_text_color, comment_text_color;
  private int hilited_text_color, hilited_candidate_text_color, hilited_comment_text_color;
  private int back_color, hilited_back_color, hilited_candidate_back_color;
  private Integer key_back_color;
  private Typeface tfText, tfLabel, tfCandidate, tfComment;
  private final int[] composition_pos = new int[2];
  private int max_length, sticky_lines, sticky_lines_land;
  private int max_entries = Candidate.getMaxCandidateCount();
  private boolean candidate_use_cursor, show_comment;
  private int highlightIndex;
  private List<Map<String, Object>> components;
  private SpannableStringBuilder ss;
  private final int span = 0;
  private String movable;
  private final int[] move_pos = new int[2];
  private boolean first_move = true;
  private float mDx, mDy;
  private int mCurrentX, mCurrentY;
  private int candidate_num;
  private boolean all_phrases;
  // private View mInputRoot;
  // 候选高亮序号颜色
  private Integer hilited_label_color;
  private TextInputManager textInputManager;

  private class CompositionSpan extends UnderlineSpan {
    public CompositionSpan() {
      super();
    }

    @Override
    public void updateDrawState(TextPaint ds) {
      ds.setTypeface(tfText);
      ds.setColor(text_color);
      ds.bgColor = back_color;
    }
  }

  private class CandidateSpan extends ClickableSpan {
    int index;
    Typeface tf;
    int hi_text, hi_back, text;

    public CandidateSpan(int i, Typeface _tf, int _hi_text, int _hi_back, int _text) {
      super();
      index = i;
      tf = _tf;
      hi_text = _hi_text;
      hi_back = _hi_back;
      text = _text;
    }

    @Override
    public void onClick(View tv) {
      textInputManager.onCandidatePressed(index);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
      ds.setUnderlineText(false);
      ds.setTypeface(tf);
      if (index == highlightIndex) {
        ds.setColor(hi_text);
        ds.bgColor = hi_back;
      } else {
        ds.setColor(text);
      }
    }
  }

  private class EventSpan extends ClickableSpan {
    Event event;

    public EventSpan(Event e) {
      super();
      event = e;
    }

    @Override
    public void onClick(View tv) {
      textInputManager.onPress(event.getCode());
      textInputManager.onEvent(event);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
      ds.setUnderlineText(false);
      ds.setColor(key_text_color);
      if (key_back_color != null) ds.bgColor = key_back_color;
    }
  }

  @TargetApi(21)
  public static class LetterSpacingSpan extends UnderlineSpan {
    private final float letterSpacing;

    /**
     * @param letterSpacing 字符間距
     */
    public LetterSpacingSpan(float letterSpacing) {
      this.letterSpacing = letterSpacing;
    }

    @Override
    public void updateDrawState(TextPaint ds) {
      ds.setLetterSpacing(letterSpacing);
    }
  }

  public Composition(Context context, AttributeSet attrs) {
    super(context, attrs);
    textInputManager = TextInputManager.Companion.getInstance();
    setShowComment(!Rime.getOption("_hide_comment"));
    reset(context);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    int action = event.getAction();
    if (action == MotionEvent.ACTION_UP) {
      int n = getOffsetForPosition(event.getX(), event.getY());
      if (composition_pos[0] <= n && n <= composition_pos[1]) {
        String s =
            getText().toString().substring(n, composition_pos[1]).replace(" ", "").replace("‸", "");
        n = Rime.RimeGetInput().length() - s.length(); // 從右側定位
        Rime.RimeSetCaretPos(n);
        Trime.getService().updateComposing();
        return true;
      }
    } else if (!movable.contentEquals("false")
        && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN)) {
      int n = getOffsetForPosition(event.getX(), event.getY());
      if (move_pos[0] <= n && n <= move_pos[1]) {
        if (action == MotionEvent.ACTION_DOWN) {
          if (first_move || movable.contentEquals("once")) {
            first_move = false;
            this.getLocationOnScreen(new int[] {mCurrentX, mCurrentY});
          }
          mDx = mCurrentX - event.getRawX();
          mDy = mCurrentY - event.getRawY();
        } else { // MotionEvent.ACTION_MOVE
          mCurrentX = (int) (event.getRawX() + mDx);
          mCurrentY = (int) (event.getRawY() + mDy);
          Trime.getService().updatePopupWindow(mCurrentX, mCurrentY);
        }
        return true;
      }
    }
    return super.onTouchEvent(event);
  }

  public void setShowComment(boolean value) {
    show_comment = value;
  }

  public void reset(Context context) {
    final Config config = Config.get(context);
    components = (List<Map<String, Object>>) config.getValue("window");
    if (config.hasKey("layout/max_entries")) max_entries = config.getInt("layout/max_entries");
    candidate_use_cursor = config.getBoolean("candidate_use_cursor");
    text_size = config.getPixel("text_size");
    candidate_text_size = config.getPixel("candidate_text_size");
    comment_text_size = config.getPixel("comment_text_size");
    label_text_size = config.getPixel("label_text_size");

    text_color = config.getColor("text_color");
    candidate_text_color = config.getColor("candidate_text_color");
    comment_text_color = config.getColor("comment_text_color");
    hilited_text_color = config.getColor("hilited_text_color");
    hilited_candidate_text_color = config.getColor("hilited_candidate_text_color");
    hilited_comment_text_color = config.getColor("hilited_comment_text_color");
    label_color = config.getColor("label_color");
    hilited_label_color = config.getColor("hilited_label_color");
    if (hilited_label_color == null) {
      hilited_label_color = hilited_candidate_text_color;
    }

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

    int max_width = config.getPixel("layout/max_width");
    int real_margin = config.getPixel("layout/real_margin");
    int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
    Timber.d("max_width = %s, displayWidth = %s ", max_width, displayWidth);
    if (max_width > displayWidth) max_width = displayWidth;
    setMaxWidth(max_width - real_margin * 2);

    setMaxHeight(config.getPixel("layout/max_height"));
    int margin_x, margin_y, margin_bottom;
    margin_x = config.getPixel("layout/margin_x");
    margin_y = config.getPixel("layout/margin_y");
    margin_bottom = config.getPixel("layout/margin_bottom", margin_y);
    setPadding(margin_x, margin_y, margin_x, margin_bottom);
    max_length = config.getInt("layout/max_length");
    sticky_lines = config.getInt("layout/sticky_lines");
    sticky_lines_land = config.getInt("layout/sticky_lines_land");
    movable = config.getString("layout/movable");
    all_phrases = config.getBoolean("layout/all_phrases");
    tfLabel = config.getFont("label_font");
    tfText = config.getFont("text_font");
    tfCandidate = config.getFont("candidate_font");
    tfComment = config.getFont("comment_font");
  }

  private Object getAlign(Map<?, ?> m) {
    Layout.Alignment i = Layout.Alignment.ALIGN_NORMAL;
    if (m.containsKey("align")) {
      String align = Config.getString(m, "align");
      switch (align) {
        case "left":
        case "normal":
          i = Layout.Alignment.ALIGN_NORMAL;
          break;
        case "right":
        case "opposite":
          i = Layout.Alignment.ALIGN_OPPOSITE;
          break;
        case "center":
          i = Layout.Alignment.ALIGN_CENTER;
          break;
      }
    }
    return new AlignmentSpan.Standard(i);
  }

  private void appendComposition(Map<String, ?> m) {
    final Rime.RimeComposition r = Rime.getComposition();
    assert r != null;
    final String s = r.getText();
    int start, end;
    String sep = Config.getString(m, "start");
    if (!TextUtils.isEmpty(sep)) {
      start = ss.length();
      ss.append(sep);
      end = ss.length();
      ss.setSpan(getAlign(m), start, end, span);
    }
    start = ss.length();
    ss.append(s);
    end = ss.length();
    ss.setSpan(getAlign(m), start, end, span);
    composition_pos[0] = start;
    composition_pos[1] = end;
    ss.setSpan(new CompositionSpan(), start, end, span);
    ss.setSpan(new AbsoluteSizeSpan(text_size), start, end, span);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && m.containsKey("letter_spacing")) {
      final float size = ConfigGetter.getFloat(m, "letter_spacing", 0);
      if (size != 0f) ss.setSpan(new LetterSpacingSpan(size), start, end, span);
    }
    start = composition_pos[0] + r.getStart();
    end = composition_pos[0] + r.getEnd();
    ss.setSpan(new ForegroundColorSpan(hilited_text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(hilited_back_color), start, end, span);
    sep = Config.getString(m, "end");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  /**
   * 计算悬浮窗显示候选词后，候选栏从第几个候选词开始展示 注意当 all_phrases==true 时，悬浮窗显示的候选词数量和候选栏从第几个开始，是不一致的
   *
   * @param min_length 候选词长度大于设定，才会显示到悬浮窗中
   * @param min_check 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
   * @return j
   */
  private int calcStartNum(int min_length, int min_check) {
    final Rime.RimeCandidate[] candidates = Rime.getCandidates();
    if (candidates == null) return 0;

    int j = min_check > max_entries ? (max_entries - 1) : (min_check - 1);
    if (j >= candidates.length) j = candidates.length - 1;
    for (; j >= 0; j--) {
      final String cand = candidates[j].text;
      if (cand.length() >= min_length) break;
    }

    if (j < 0) j = 0;

    for (; j < max_entries && j < candidates.length; j++) {
      final String cand = candidates[j].text;
      if (cand.length() < min_length) {
        return j;
      }
    }
    return j;
  }

  /** 生成悬浮窗内的文本 */
  private void appendCandidates(Map<?, ?> m, int length, int end_num) {
    Timber.d("appendCandidates(): length = %s", length);
    int start, end;

    final Rime.RimeCandidate[] candidates = Rime.getCandidates();
    if (candidates == null) return;
    String sep = Config.getString(m, "start");
    highlightIndex = candidate_use_cursor ? Rime.getCandHighlightIndex() : -1;
    String label_format = Config.getString(m, "label");
    String candidate_format = Config.getString(m, "candidate");
    String comment_format = Config.getString(m, "comment");
    String line = Config.getString(m, "sep");

    int line_length = 0;
    int sticky_lines_now = sticky_lines;
    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      sticky_lines_now = sticky_lines_land;
    }
    //    Timber.d("sticky_lines_now = %d", sticky_lines_now);

    String[] labels = Rime.getSelectLabels();
    int i = -1;
    candidate_num = 0;
    for (Rime.RimeCandidate o : candidates) {
      String cand = o.text;
      if (TextUtils.isEmpty(cand)) cand = "";
      i++;
      if (candidate_num >= max_entries) break;

      if (!all_phrases && candidate_num >= end_num) break;

      if (all_phrases && cand.length() < length) {
        continue;
      }
      cand = String.format(candidate_format, cand);
      final String line_sep;
      if (candidate_num == 0) {
        line_sep = sep;
      } else if ((sticky_lines_now > 0 && sticky_lines_now >= i)
          || (max_length > 0 && line_length + cand.length() > max_length)) {
        line_sep = "\n";
        line_length = 0;
      } else {
        line_sep = line;
      }
      if (!TextUtils.isEmpty(line_sep)) {
        start = ss.length();
        ss.append(line_sep);
        end = ss.length();
        ss.setSpan(getAlign(m), start, end, span);
      }
      if (!TextUtils.isEmpty(label_format) && labels != null) {
        final String label = String.format(label_format, labels[i]);
        start = ss.length();
        ss.append(label);
        end = ss.length();
        ss.setSpan(
            new CandidateSpan(
                i, tfLabel, hilited_label_color, hilited_candidate_back_color, label_color),
            start,
            end,
            span);
        ss.setSpan(new AbsoluteSizeSpan(label_text_size), start, end, span);
      }
      start = ss.length();
      ss.append(cand);
      end = ss.length();
      line_length += cand.length();
      ss.setSpan(getAlign(m), start, end, span);
      ss.setSpan(
          new CandidateSpan(
              i,
              tfCandidate,
              hilited_candidate_text_color,
              hilited_candidate_back_color,
              candidate_text_color),
          start,
          end,
          span);
      ss.setSpan(new AbsoluteSizeSpan(candidate_text_size), start, end, span);
      String comment = o.comment;
      if (show_comment && !TextUtils.isEmpty(comment_format) && !TextUtils.isEmpty(comment)) {
        comment = String.format(comment_format, comment);
        start = ss.length();
        ss.append(comment);
        end = ss.length();
        ss.setSpan(getAlign(m), start, end, span);
        ss.setSpan(
            new CandidateSpan(
                i,
                tfComment,
                hilited_comment_text_color,
                hilited_candidate_back_color,
                comment_text_color),
            start,
            end,
            span);
        ss.setSpan(new AbsoluteSizeSpan(comment_text_size), start, end, span);
        line_length += comment.length();
      }
      candidate_num++;
    }
    sep = Config.getString(m, "end");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  private void appendButton(@NonNull Map<?, ?> m) {
    if (m.containsKey("when")) {
      final String when = Config.getString(m, "when");
      if (when.contentEquals("paging") && !Rime.isPaging()) return;
      if (when.contentEquals("has_menu") && !Rime.hasMenu()) return;
    }
    final String label;
    final Event e = new Event(Config.getString(m, "click"));
    if (m.containsKey("label")) label = Config.getString(m, "label");
    else label = e.getLabel();
    int start, end;
    String sep = null;
    if (m.containsKey("start")) sep = Config.getString(m, "start");
    if (!TextUtils.isEmpty(sep)) {
      start = ss.length();
      ss.append(sep);
      end = ss.length();
      ss.setSpan(getAlign(m), start, end, span);
    }
    start = ss.length();
    ss.append(label);
    end = ss.length();
    ss.setSpan(getAlign(m), start, end, span);
    ss.setSpan(new EventSpan(e), start, end, span);
    ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
    sep = Config.getString(m, "end");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  private void appendMove(Map<?, ?> m) {
    String s = Config.getString(m, "move");
    int start, end;
    String sep = Config.getString(m, "start");
    if (!TextUtils.isEmpty(sep)) {
      start = ss.length();
      ss.append(sep);
      end = ss.length();
      ss.setSpan(getAlign(m), start, end, span);
    }
    start = ss.length();
    ss.append(s);
    end = ss.length();
    ss.setSpan(getAlign(m), start, end, span);
    move_pos[0] = start;
    move_pos[1] = end;
    ss.setSpan(new AbsoluteSizeSpan(key_text_size), start, end, span);
    ss.setSpan(new ForegroundColorSpan(key_text_color), start, end, span);
    sep = Config.getString(m, "end");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  public int setWindow(int length, int min_check) {
    if (getVisibility() != View.VISIBLE) return 0;
    Rime.RimeComposition r = Rime.getComposition();
    if (r == null) return 0;
    String s = r.getText();
    if (TextUtils.isEmpty(s)) return 0;
    setSingleLine(true); // 設置單行
    ss = new SpannableStringBuilder();
    int start_num = 0;
    for (Map<String, ?> m : components) {
      if (m.containsKey("composition")) appendComposition(m);
      else if (m.containsKey("candidate")) {
        start_num = calcStartNum(length, min_check);
        Timber.d("start_num = %s, min_length = %s, min_check = %s", start_num, length, min_check);
        appendCandidates(m, length, start_num);
      } else if (m.containsKey("click")) appendButton(m);
      else if (m.containsKey("move")) appendMove(m);
    }
    if (candidate_num > 0 || ss.toString().contains("\n")) setSingleLine(false); // 設置單行
    setText(ss);
    setMovementMethod(LinkMovementMethod.getInstance());
    return start_num;
  }
}
