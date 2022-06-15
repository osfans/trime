package com.osfans.trime.ime.symbol;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.osfans.trime.R;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.Config;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.enums.PositionType;
import com.osfans.trime.ime.text.TextInputManager;
import timber.log.Timber;

public class CandidateAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final Context myContext;
  private final TextInputManager textInputManager;

  // 候选词
  private Rime.RimeCandidate[] candidates;
  private Trime trime;
  private AppPrefs prefs;

  private int keyMarginX, keyMarginTop;
  private Integer textColor;
  private float textSize;
  private Typeface textFont;
  private Drawable background;

  private PositionType textPosition, commentPosition;

  public CandidateAdapter(Context context) {
    myContext = context;
    trime = Trime.getService();
    prefs = AppPrefs.defaultInstance();
    candidates = new Rime.RimeCandidate[0];
    textInputManager = TextInputManager.Companion.getInstance();
  }

  public int updateCandidates() {

    candidates = Rime.getCandidatesWithoutSwitch();
    //    highlightIndex = Rime.getCandHighlightIndex() - startNum;
    if (candidates == null) {
      candidates = new Rime.RimeCandidate[0];
    }
    synchronized (candidates) {
      candidates.notify();
    }
    return candidates.length;
  }

  @Override
  public int getItemCount() {
    return candidates.length;
  }

  public void configStyle(int keyMarginX, int keyMarginTop) {
    this.keyMarginX = keyMarginX;
    this.keyMarginTop = keyMarginTop;

    //  边框尺寸、圆角、字号直接读取主题通用参数。配色优先读取 liquidKeyboard 专用参数。
    Config config = Config.get(myContext);
    textColor = config.getLiquidColor("long_text_color");
    if (textColor == null) textColor = config.getLiquidColor("key_text_color");

    textSize = config.getFloat("candidate_text_size");

    background =
        config.getDrawable(
            "long_text_back_color", "key_border", "key_long_text_border", "round_corner", null);

    textFont = config.getFont("long_text_font");
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(myContext).inflate(R.layout.liquid_key_item, parent, false);
    return new ItemViewHolder(view);
  }

  private static class ItemViewHolder extends RecyclerView.ViewHolder {
    public ItemViewHolder(View view) {
      super(view);

      listItemLayout = view.findViewById(R.id.listitem_layout);
      textView1 = view.findViewById(R.id.text1);
      textView2 = view.findViewById(R.id.text2);
    }

    ConstraintLayout listItemLayout;
    TextView textView1, textView2;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int index) {

    if (viewHolder instanceof ItemViewHolder) {
      final ItemViewHolder itemViewHold = ((ItemViewHolder) viewHolder);

      if (textFont != null) itemViewHold.textView1.setTypeface(textFont);

      String text = candidates[index].text;
      String comment = candidates[index].comment;
      if (comment == null) comment = "";

      if (text.length() > 300) itemViewHold.textView1.setText(text.substring(0, 300));
      else itemViewHold.textView1.setText(text);

      if (comment.length() > 300) itemViewHold.textView2.setText(comment.substring(0, 300));
      else itemViewHold.textView2.setText(comment);

      if (textSize > 0) itemViewHold.textView1.setTextSize(textSize);

      ViewGroup.LayoutParams lp = itemViewHold.listItemLayout.getLayoutParams();
      if (lp instanceof FlexboxLayoutManager.LayoutParams) {
        FlexboxLayoutManager.LayoutParams flexboxLp =
            (FlexboxLayoutManager.LayoutParams) itemViewHold.listItemLayout.getLayoutParams();

        itemViewHold.textView1.setTextColor(textColor);

        int marginTop = flexboxLp.getMarginTop();
        int marginX = flexboxLp.getMarginLeft();
        if (keyMarginTop > 0) marginTop = keyMarginTop;
        if (keyMarginX > 0) marginX = keyMarginX;

        flexboxLp.setMargins(marginX, marginTop, marginX, flexboxLp.getMarginBottom());

        // TODO 设置剪贴板列表样式
        // copy SimpleAdapter 会造成高度始终为 3 行无法自适应的效果。

      }

      if (background != null)
        itemViewHold.listItemLayout.setBackground(
            Config.get(myContext)
                .getDrawable(
                    "long_text_back_color",
                    "key_border",
                    "key_long_text_border",
                    "round_corner",
                    null));

      // 如果设置了回调，则设置点击事件
      {
        itemViewHold.listItemLayout.setOnClickListener(
            view -> {
              int position = itemViewHold.getLayoutPosition();
              Timber.d("Click " + position + "/" + getItemCount());
              textInputManager.onCandidatePressed(index);
              updateCandidates();
              if (candidates.length < 1) Trime.getService().selectLiquidKeyboard(-1);
              else notifyDataSetChanged();
            });
      }

      // TODO 剪贴板列表点击时产生背景变色效果
      itemViewHold.listItemLayout.setOnTouchListener(
          (view, motionEvent) -> {
            int action = motionEvent.getAction();
            switch (action) {
              case MotionEvent.ACTION_DOWN:

              case MotionEvent.ACTION_UP:
              case MotionEvent.ACTION_CANCEL:
                break;
            }
            return false;
          });
    }
  }

  /** 添加 OnItemClickListener 回调 * */
  public interface OnItemClickListener {
    void onItemClick(View view, int position);
  }

  private OnItemClickListener mOnItemClickListener;
}
