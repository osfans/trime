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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
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
import com.osfans.trime.core.CandidateListItem;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeComposition;
import com.osfans.trime.data.theme.FontManager;
import com.osfans.trime.data.theme.Theme;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.keyboard.Event;
import com.osfans.trime.ime.util.UiUtil;
import com.osfans.trime.util.CollectionUtils;
import com.osfans.trime.util.DimensionsKt;
import java.util.ArrayList;
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
  private int max_entries;
  private boolean candidate_use_cursor, show_comment;
  private int highlightIndex;
  private List<Map<String, Object>> windows_comps, liquid_keyboard_window_comp;
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
    textInputManager = TextInputManager.Companion.getInstance(UiUtil.INSTANCE.isDarkMode(context));
    setShowComment(!Rime.getOption("_hide_comment"));
    reset();
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
        n = Rime.getRimeRawInput().length() - s.length(); // 從右側定位
        Rime.setCaretPos(n);
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

  public void reset() {
    final Theme theme = Theme.get();

    if ((windows_comps = (List<Map<String, Object>>) theme.style.getObject("window")) == null) {
      windows_comps = new ArrayList<>();
    }
    if ((liquid_keyboard_window_comp =
            (List<Map<String, Object>>) theme.style.getObject("liquid_keyboard_window"))
        == null) {
      liquid_keyboard_window_comp = new ArrayList<>();
    }

    if ((max_entries = theme.style.getInt("layout/max_entries")) == 0) {
      max_entries = Candidate.getMaxCandidateCount();
    }
    candidate_use_cursor = theme.style.getBoolean("candidate_use_cursor");
    text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("text_size"));
    candidate_text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("candidate_text_size"));
    comment_text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("comment_text_size"));
    label_text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("label_text_size"));

    text_color = theme.colors.getColor("text_color");
    candidate_text_color = theme.colors.getColor("candidate_text_color");
    comment_text_color = theme.colors.getColor("comment_text_color");
    hilited_text_color = theme.colors.getColor("hilited_text_color");
    hilited_candidate_text_color = theme.colors.getColor("hilited_candidate_text_color");
    hilited_comment_text_color = theme.colors.getColor("hilited_comment_text_color");
    label_color = theme.colors.getColor("label_color");
    hilited_label_color = theme.colors.getColor("hilited_label_color");
    if (hilited_label_color == null) {
      hilited_label_color = hilited_candidate_text_color;
    }

    back_color = theme.colors.getColor("back_color");
    hilited_back_color = theme.colors.getColor("hilited_back_color");
    hilited_candidate_back_color = theme.colors.getColor("hilited_candidate_back_color");

    key_text_size = (int) DimensionsKt.sp2px(theme.style.getFloat("key_text_size"));
    key_text_color = theme.colors.getColor("key_text_color");
    key_back_color = theme.colors.getColor("key_back_color");

    float line_spacing_multiplier = theme.style.getFloat("layout/line_spacing_multiplier");
    if (line_spacing_multiplier == 0f) line_spacing_multiplier = 1f;
    setLineSpacing(theme.style.getFloat("layout/line_spacing"), line_spacing_multiplier);
    setMinWidth((int) DimensionsKt.dp2px(theme.style.getFloat("layout/min_width")));
    setMinHeight((int) DimensionsKt.dp2px(theme.style.getFloat("layout/min_height")));

    int max_width = (int) DimensionsKt.dp2px(theme.style.getFloat("layout/max_width"));
    int real_margin = (int) DimensionsKt.dp2px(theme.style.getFloat("layout/real_margin"));
    int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
    Timber.d("max_width = %s, displayWidth = %s ", max_width, displayWidth);
    if (max_width > displayWidth) max_width = displayWidth;
    setMaxWidth(max_width - real_margin * 2);

    setMaxHeight((int) DimensionsKt.dp2px(theme.style.getFloat("layout/max_height")));
    int margin_x, margin_y, margin_bottom;
    margin_x = (int) DimensionsKt.dp2px(theme.style.getFloat("layout/margin_x"));
    margin_y = (int) DimensionsKt.dp2px(theme.style.getFloat("layout/margin_y"));
    margin_bottom = (int) DimensionsKt.dp2px(theme.style.getFloat("layout/margin_bottom"));
    setPadding(margin_x, margin_y, margin_x, margin_bottom);
    max_length = theme.style.getInt("layout/max_length");
    sticky_lines = theme.style.getInt("layout/sticky_lines");
    sticky_lines_land = theme.style.getInt("layout/sticky_lines_land");
    movable = theme.style.getString("layout/movable");
    all_phrases = theme.style.getBoolean("layout/all_phrases");
    tfLabel = FontManager.getTypeface(theme.style.getString("label_font"));
    tfText = FontManager.getTypeface(theme.style.getString("text_font"));
    tfCandidate = FontManager.getTypeface(theme.style.getString("candidate_font"));
    tfComment = FontManager.getTypeface(theme.style.getString("comment_font"));
  }

  private Object getAlign(Map<String, Object> m) {
    Layout.Alignment i = Layout.Alignment.ALIGN_NORMAL;
    if (m.containsKey("align")) {
      String align = CollectionUtils.obtainString(m, "align", "");
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

  private void appendComposition(Map<String, Object> m) {
    final RimeComposition r = Rime.getComposition();
    assert r != null;
    final String s = r.getPreedit();
    int start, end;
    String sep = CollectionUtils.obtainString(m, "start", "");
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
    if (m.containsKey("letter_spacing")) {
      final float size = CollectionUtils.obtainFloat(m, "letter_spacing", 0);
      if (size != 0f) ss.setSpan(new LetterSpacingSpan(size), start, end, span);
    }
    start = composition_pos[0] + r.getSelStartPos();
    end = composition_pos[0] + r.getSelEndPos();
    ss.setSpan(new ForegroundColorSpan(hilited_text_color), start, end, span);
    ss.setSpan(new BackgroundColorSpan(hilited_back_color), start, end, span);
    sep = CollectionUtils.obtainString(m, "end", "");
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
    Timber.d("setWindow calcStartNum() getCandidates");
    final CandidateListItem[] candidates = Rime.getCandidatesOrStatusSwitches();
    if (candidates.length == 0) return 0;

    Timber.d("setWindow calcStartNum() getCandidates finish, size=%s", candidates.length);
    int j = min_check > max_entries ? (max_entries - 1) : (min_check - 1);
    if (j >= candidates.length) j = candidates.length - 1;
    for (; j >= 0; j--) {
      final String cand = candidates[j].getText();
      if (cand.length() >= min_length) break;
    }

    if (j < 0) j = 0;

    for (; j < max_entries && j < candidates.length; j++) {
      final String cand = candidates[j].getText();
      if (cand.length() < min_length) {
        return j;
      }
    }
    return j;
  }

  /** 生成悬浮窗内的文本 */
  private void appendCandidates(Map<String, Object> m, int length, int end_num) {
    Timber.d("appendCandidates(): length = %s", length);
    int start, end;

    final CandidateListItem[] candidates = Rime.getCandidatesOrStatusSwitches();
    if (candidates.length == 0) return;
    String sep = CollectionUtils.obtainString(m, "start", "");
    highlightIndex = candidate_use_cursor ? Rime.getCandHighlightIndex() : -1;
    String label_format = CollectionUtils.obtainString(m, "label", "");
    String candidate_format = CollectionUtils.obtainString(m, "candidate", "");
    String comment_format = CollectionUtils.obtainString(m, "comment", "");
    String line = CollectionUtils.obtainString(m, "sep", "");

    int line_length = 0;
    int sticky_lines_now = sticky_lines;
    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      sticky_lines_now = sticky_lines_land;
    }
    //    Timber.d("sticky_lines_now = %d", sticky_lines_now);

    String[] labels = Rime.getSelectLabels();
    int i = -1;
    candidate_num = 0;
    for (CandidateListItem o : candidates) {
      String cand = o.getText();
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
      String comment = o.getComment();
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
    sep = CollectionUtils.obtainString(m, "end", "");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  private void appendButton(@NonNull Map<String, Object> m) {
    if (m.containsKey("when")) {
      final String when = CollectionUtils.obtainString(m, "when", "");
      if (when.contentEquals("paging") && !Rime.hasLeft()) return;
      if (when.contentEquals("has_menu") && !Rime.hasMenu()) return;
    }
    final String label;
    final Event e = new Event(CollectionUtils.obtainString(m, "click", ""));
    if (m.containsKey("label")) label = CollectionUtils.obtainString(m, "label", "");
    else label = e.getLabel();
    int start, end;
    String sep = null;
    if (m.containsKey("start")) sep = CollectionUtils.obtainString(m, "start", "");
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
    sep = CollectionUtils.obtainString(m, "end", "");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  private void appendMove(Map<String, Object> m) {
    String s = CollectionUtils.obtainString(m, "move", "");
    int start, end;
    String sep = CollectionUtils.obtainString(m, "start", "");
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
    sep = CollectionUtils.obtainString(m, "end", "");
    if (!TextUtils.isEmpty(sep)) ss.append(sep);
  }

  /**
   * 设置悬浮窗文本
   *
   * @param charLength 候选词长度大于设定，才会显示到悬浮窗中
   * @param minCheck 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
   * @param maxPopup 最多在悬浮窗显示多少个候选词
   * @return 悬浮窗显示的候选词数量
   */
  public int setWindow(int charLength, int minCheck, int maxPopup) {
    return setWindow(charLength, minCheck);
  }

  /**
   * 设置悬浮窗文本
   *
   * @param stringMinLength 候选词长度大于设定，才会显示到悬浮窗中
   * @param candidateMinCheck 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
   * @return 悬浮窗显示的候选词数量
   */
  public int setWindow(int stringMinLength, int candidateMinCheck) {
    if (getVisibility() != View.VISIBLE) return 0;
    StackTraceElement[] stacks = new Throwable().getStackTrace();
    Timber.d(
        "setWindow Rime.getComposition()"
            + ", [1]"
            + stacks[1].toString()
            + ", [2]"
            + stacks[2].toString()
            + ", [3]"
            + stacks[3].toString());
    RimeComposition r = Rime.getComposition();
    if (r == null) return 0;
    String s = r.getPreedit();
    if (TextUtils.isEmpty(s)) return 0;
    setSingleLine(true); // 設置單行
    ss = new SpannableStringBuilder();
    int start_num = 0;

    for (Map<String, Object> m : windows_comps) {
      if (m.containsKey("composition")) appendComposition(m);
      else if (m.containsKey("candidate")) {
        start_num = calcStartNum(stringMinLength, candidateMinCheck);
        Timber.d(
            "start_num = %s, min_length = %s, min_check = %s",
            start_num, stringMinLength, candidateMinCheck);
        appendCandidates(m, stringMinLength, start_num);
      } else if (m.containsKey("click")) appendButton(m);
      else if (m.containsKey("move")) appendMove(m);
    }
    if (candidate_num > 0 || ss.toString().contains("\n")) setSingleLine(false); // 設置單行
    setText(ss);
    setMovementMethod(LinkMovementMethod.getInstance());
    return start_num;
  }

  /** 设置悬浮窗, 用于liquidKeyboard的悬浮窗工具栏 */
  public void setWindow() {
    if (getVisibility() != View.VISIBLE) return;
    if (liquid_keyboard_window_comp.isEmpty()) {
      this.setVisibility(GONE);
      return;
    }

    ss = new SpannableStringBuilder();

    for (Map<String, Object> m : liquid_keyboard_window_comp) {
      if (m.containsKey("composition")) appendComposition(m);
      else if (m.containsKey("click")) appendButton(m);
    }
    setSingleLine(!ss.toString().contains("\n"));

    setText(ss);
    setMovementMethod(LinkMovementMethod.getInstance());
  }
}
