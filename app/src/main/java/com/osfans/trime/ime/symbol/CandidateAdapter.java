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
import com.osfans.trime.data.Config;
import com.osfans.trime.ime.enums.PositionType;
import com.osfans.trime.ime.text.TextInputManager;
import java.util.List;

// 显示长度不固定，字体大小正常的内容。用于类型 CANDIDATE, VAR_LENGTH
public class CandidateAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final Context myContext;
  private final TextInputManager textInputManager;

  // 候选词
  private Rime.RimeCandidate[] candidates;

  private int keyMarginX, keyMarginTop;
  private Integer textColor, textColor2, commentColor;
  private float textSize, commentSize;
  private Typeface textFont, commentFont;
  private Drawable background;
  private PositionType textPosition, commentPosition;
  private static int COMMENT_UNKNOW = 0, COMMENT_TOP = 1, COMMENT_DOWN = 2, COMMENT_RIGHT = 3;
  private static int comment_position;
  private static boolean hide_comment;

  public CandidateAdapter(Context context) {
    myContext = context;
    candidates = new Rime.RimeCandidate[0];
    textInputManager = TextInputManager.Companion.getInstance();
    comment_position = 0;
  }

  public int updateCandidates() {
    candidates = Rime.getCandidatesWithoutSwitch();
    if (candidates == null) {
      candidates = new Rime.RimeCandidate[0];
    }
    return candidates.length;
  }

  public void setCandidates(List<SimpleKeyBean> list) {
    candidates = new Rime.RimeCandidate[list.size()];
    for (int i = 0; i < list.size(); i++) {
      candidates[i] = new Rime.RimeCandidate(list.get(i).getText(), "");
    }
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

    textColor = config.getColor("candidate_text_color");
    textColor2 = config.getColor("hilited_candidate_text_color");
    commentColor = config.getColor("comment_text_color");

    hide_comment = Rime.getOption("_hide_comment");
    if (hide_comment) {
      comment_position = COMMENT_RIGHT;
    } else {
      comment_position = config.getInt("comment_position");
      if (comment_position == COMMENT_UNKNOW) {
        comment_position = config.getBoolean("comment_on_top") ? COMMENT_TOP : COMMENT_RIGHT;
      }
    }
    textSize = config.getFloat("candidate_text_size");
    commentSize = config.getFloat("comment_text_size");

    //  点击前后必须使用相同类型的背景，或者全部为背景图，或者都为背景色
    background =
        config.getDrawable(
            "key_back_color", "key_border", "key_border_color", "round_corner", null);

    textFont = config.getFont("candidate_font");
    commentFont = config.getFont("comment_font");
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view;
    if (comment_position == COMMENT_DOWN) {
      view = LayoutInflater.from(myContext).inflate(R.layout.liquid_key_item, parent, false);
    } else if (comment_position == COMMENT_TOP) {
      view =
          LayoutInflater.from(myContext)
              .inflate(R.layout.liquid_key_item_comment_top, parent, false);
    } else {

      view =
          LayoutInflater.from(myContext)
              .inflate(R.layout.liquid_key_item_comment_right, parent, false);
    }
    return new ItemViewHolder(view);
  }

  private static class ItemViewHolder extends RecyclerView.ViewHolder {
    public ItemViewHolder(View view) {
      super(view);
      listItemLayout = view.findViewById(R.id.listitem_layout);
      textView = view.findViewById(R.id.text);
      commentView = view.findViewById(R.id.comment);
    }

    ConstraintLayout listItemLayout;
    TextView textView, commentView;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int index) {

    if (viewHolder instanceof ItemViewHolder) {
      final ItemViewHolder itemViewHold = ((ItemViewHolder) viewHolder);

      if (textFont != null) itemViewHold.textView.setTypeface(textFont);
      if (commentFont != null) itemViewHold.commentView.setTypeface(commentFont);

      String text = candidates[index].text;
      String comment = "";
      if (!hide_comment && candidates[index].comment != null) comment = candidates[index].comment;

      if (text.length() > 300) itemViewHold.textView.setText(text.substring(0, 300));
      else itemViewHold.textView.setText(text);

      if (comment.length() > 300) itemViewHold.commentView.setText(comment.substring(0, 300));
      else itemViewHold.commentView.setText(comment);

      if (textSize > 0) itemViewHold.textView.setTextSize(textSize);
      if (commentSize > 0) itemViewHold.commentView.setTextSize(commentSize);

      ViewGroup.LayoutParams lp = itemViewHold.listItemLayout.getLayoutParams();
      if (lp instanceof FlexboxLayoutManager.LayoutParams) {
        FlexboxLayoutManager.LayoutParams flexboxLp =
            (FlexboxLayoutManager.LayoutParams) itemViewHold.listItemLayout.getLayoutParams();

        itemViewHold.textView.setTextColor(textColor);
        itemViewHold.commentView.setTextColor(commentColor);

        int marginTop = flexboxLp.getMarginTop();
        int marginX = flexboxLp.getMarginLeft();
        if (keyMarginTop > 0) marginTop = keyMarginTop;
        if (keyMarginX > 0) marginX = keyMarginX;

        flexboxLp.setMargins(marginX, marginTop, marginX, flexboxLp.getMarginBottom());
        flexboxLp.setFlexGrow(1);
      }

      // 如果直接使用background，会造成滚动时部分内容的背景填充错误的问题
      if (background != null) itemViewHold.listItemLayout.setBackground(background);

      // 如果设置了回调，则设置点击事件
      if (mOnItemClickListener != null) {
        itemViewHold.listItemLayout.setOnClickListener(
            view -> {
              int position = itemViewHold.getLayoutPosition();
              mOnItemClickListener.onItemClick(itemViewHold.listItemLayout, position);
            });
      }

      // 点击时产生背景变色效果
      itemViewHold.listItemLayout.setOnTouchListener(
          (view, motionEvent) -> {
            int action = motionEvent.getAction();
            switch (action) {
              case MotionEvent.ACTION_DOWN:
                itemViewHold.textView.setTextColor(textColor2);
                break;
              case MotionEvent.ACTION_UP:
              case MotionEvent.ACTION_CANCEL:
                itemViewHold.textView.setTextColor(textColor);
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

  public void setOnItemClickListener(OnItemClickListener mOnItemClickListener) {
    this.mOnItemClickListener = mOnItemClickListener;
  }
}
