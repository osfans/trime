package com.osfans.trime.ime.symbol;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.osfans.trime.R;
import com.osfans.trime.data.Config;
import java.util.List;
import timber.log.Timber;

public class SimpleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final Context myContext;
  private final List<SimpleKeyBean> list;
  private int keyWidth, keyHeight, keyMarginX, keyMarginTop;
  private Integer textColor;
  private float textSize;
  private Drawable background, background2;
  private Typeface textFont;

  public SimpleAdapter(Context context, List<SimpleKeyBean> itemlist) {
    myContext = context;
    list = itemlist;
  }

  public void configStyle(int keyWidth, int keyHeight, int keyMarginX, int keyMarginTop) {
    // 由于按键宽度、横向间距会影响键盘左右两侧到屏幕边缘的距离，而前者需要提前计算，故adapter直接接收参数，不重新读取设置
    this.keyHeight = keyHeight;
    this.keyWidth = keyWidth;
    this.keyMarginX = keyMarginX;
    this.keyMarginTop = keyMarginTop;

    Timber.d("configStyle keyHeight = %s", keyHeight);

    //  边框尺寸、圆角、字号直接读取主题通用参数。
    Config config = Config.get(myContext);
    textColor = config.getLiquidColor("key_text_color");
    textSize = config.getFloat("label_text_size");
    //  点击前后必须使用相同类型的背景，或者全部为背景图，或者都为背景色
    background =
        config.getDrawable(
            "key_back_color", "key_border", "key_border_color", "round_corner", null);

    background2 =
        config.getDrawable(
            "hilited_key_back_color",
            "key_border",
            "hilited_key_border_color",
            "round_corner",
            null);

    textFont = config.getFont("key_font");
  }

  @Override
  public int getItemCount() {
    return list.size();
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(myContext).inflate(R.layout.simple_key_fix_item, parent, false);
    return new ItemViewHolder(view);
  }

  static class ItemViewHolder extends RecyclerView.ViewHolder {
    public ItemViewHolder(View view) {
      super(view);

      listItemLayout = view.findViewById(R.id.listitem_layout);
      mTitle = view.findViewById(R.id.simple_key);
    }

    LinearLayout listItemLayout;
    TextView mTitle;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int index) {

    if (viewHolder instanceof ItemViewHolder) {
      SimpleKeyBean searchHistoryBean = list.get(index);
      final ItemViewHolder itemViewHold = ((ItemViewHolder) viewHolder);

      if (textFont != null) itemViewHold.mTitle.setTypeface(textFont);

      itemViewHold.mTitle.setText(searchHistoryBean.getLabel());
      if (textSize > 0) itemViewHold.mTitle.setTextSize(textSize);

      ViewGroup.LayoutParams lp = itemViewHold.listItemLayout.getLayoutParams();
      if (lp instanceof FlexboxLayoutManager.LayoutParams) {
        FlexboxLayoutManager.LayoutParams flexboxLp =
            (FlexboxLayoutManager.LayoutParams) itemViewHold.listItemLayout.getLayoutParams();

        if (searchHistoryBean.getLabel().isEmpty()) {
          flexboxLp.setWrapBefore(true);
          flexboxLp.setWidth(0);
          flexboxLp.setHeight(0);
          flexboxLp.setMargins(0, 0, 0, 0);
        } else {

          if (keyWidth > 0) flexboxLp.setWidth(keyWidth);
          if (keyHeight > 0) flexboxLp.setHeight(keyHeight);
          itemViewHold.mTitle.setTextColor(textColor);

          int marginTop = flexboxLp.getMarginTop();
          int marginX = flexboxLp.getMarginLeft();
          if (keyMarginTop > 0) marginTop = keyMarginTop;
          if (keyMarginX > 0) marginX = keyMarginX;

          flexboxLp.setMargins(marginX, marginTop, marginX, flexboxLp.getMarginBottom());

          if (background != null) {
            itemViewHold.listItemLayout.setBackground(background);
          }
        }
      }

      if (mOnItemClickListener != null) {
        itemViewHold.listItemLayout.setOnClickListener(
            view -> {
              int position = itemViewHold.getLayoutPosition(); // 在增加数据或者减少数据时候，position和index就不一样了
              mOnItemClickListener.onItemClick(itemViewHold.listItemLayout, position);
            });
      }

      // 按钮点击时产生背景变色效果
      itemViewHold.listItemLayout.setOnTouchListener(
          (view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
              if (background2 != null) itemViewHold.listItemLayout.setBackground(background2);
            } else {
              if (motionEvent.getAction() == MotionEvent.ACTION_UP
                  || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                if (background != null) itemViewHold.listItemLayout.setBackground(background);
              }
            }
            return false;
          });
    }
  }

  /*=====================添加OnItemClickListener回调================================*/
  public interface OnItemClickListener {
    void onItemClick(View view, int position);
  }

  private OnItemClickListener mOnItemClickListener;

  public void setOnItemClickListener(OnItemClickListener mOnItemClickListener) {
    this.mOnItemClickListener = mOnItemClickListener;
  }
}
