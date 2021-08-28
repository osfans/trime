package com.osfans.trime.ime.SymbolKeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import com.osfans.trime.setup.Config;
import java.util.List;
import timber.log.Timber;

public class SimpleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final Context myContext;
  private final List<SimpleKeyBean> list;
  private int keyWidth, keyHeight, keyMarginX, keyMarginTop, borderWidth;
  private Integer borderColor, backColor, backColor2, textColor;
  private float textSize, roundCorner;
  private Drawable background, background2;
  private GradientDrawable drawable;
  private Typeface textFont;

  public SimpleAdapter(Context context, List<SimpleKeyBean> itemlist) {
    myContext = context;
    list = itemlist;
  }

  public void configStyle(int keyWidth, int keyHegith, int keyMarginX, int keyMarginTop) {
    // 由于按键宽度、横向间距会影响键盘左右两侧到屏幕边缘的距离，而前者需要提前计算，故adapter直接接收参数，不重新读取设置
    this.keyHeight = keyHegith;
    this.keyWidth = keyWidth;
    this.keyMarginX = keyMarginX;
    this.keyMarginTop = keyMarginTop;

    Timber.d("configStyle keyHeight=%s", keyHegith);

    //  边框尺寸、圆角、字号直接读取主题通用参数。配色优先读取liquidKeyboard专用参数。
    Config config = Config.get(myContext);
    textColor = config.getLiquidColor("key_text_color");
    textSize = config.getFloat("label_text_size");
    //  点击前后必须使用相同类型的背景，或者全部为背景图，或者都为背景色
    background = config.getLiquidDrawable("key_back_color", myContext);
    if (background == null) {
      backColor = config.getLiquidColor("key_back_color");
      borderColor = config.getLiquidColor("key_border_color");
      borderWidth = config.getPixel("border_width");
      roundCorner = config.getFloat("round_corner");
      backColor2 = config.getLiquidColor("hilited_key_back_color");
    } else background2 = config.getLiquidDrawable("hilited_key_back_color", myContext);

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
      itemViewHold.mTitle.setText(searchHistoryBean.getText());

      itemViewHold.mTitle.setText(searchHistoryBean.getLabel());
      if (textSize > 0) itemViewHold.mTitle.setTextSize(textSize);

      ViewGroup.LayoutParams lp = itemViewHold.listItemLayout.getLayoutParams();
      if (lp instanceof FlexboxLayoutManager.LayoutParams) {
        FlexboxLayoutManager.LayoutParams flexboxLp =
            (FlexboxLayoutManager.LayoutParams) itemViewHold.listItemLayout.getLayoutParams();

        if (keyWidth > 0) flexboxLp.setWidth(keyWidth);
        if (keyHeight > 0) flexboxLp.setHeight(keyHeight);
        itemViewHold.mTitle.setTextColor(textColor);

        int marginTop = flexboxLp.getMarginTop();
        int marginX = flexboxLp.getMarginLeft();
        if (keyMarginTop > 0) marginTop = keyMarginTop;
        if (keyMarginX > 0) marginX = keyMarginX;

        flexboxLp.setMargins(marginX, marginTop, marginX, flexboxLp.getMarginBottom());

        if (background == null) {
          if (drawable == null) {
            drawable = (GradientDrawable) itemViewHold.listItemLayout.getBackground();
            if (borderWidth >= 0 && borderColor != null)
              drawable.setStroke(borderWidth, borderColor);
            if (backColor != null) drawable.setColor(backColor);
            if (roundCorner >= 0) drawable.setCornerRadius(roundCorner);
          }

        } else itemViewHold.listItemLayout.setBackground(background);
      }

      if (mOnItemClickLitener != null) {
        itemViewHold.listItemLayout.setOnClickListener(
            view -> {
              int position = itemViewHold.getLayoutPosition(); // 在增加数据或者减少数据时候，position和index就不一样了
              mOnItemClickLitener.onItemClick(itemViewHold.listItemLayout, position);
            });
      }

      // 按钮点击时产生背景变色效果
      itemViewHold.listItemLayout.setOnTouchListener(
          (view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
              if (drawable == null) {
                if (background2 != null) itemViewHold.listItemLayout.setBackground(background2);
              } else if (backColor2 != null) {
                drawable.setColor(backColor2);
                itemViewHold.listItemLayout.setBackground(drawable);
              }
            } else {
              if (motionEvent.getAction() == MotionEvent.ACTION_UP
                  || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                if (drawable == null) itemViewHold.listItemLayout.setBackground(background);
                else if (backColor != null) {
                  drawable.setColor(backColor);
                  itemViewHold.listItemLayout.setBackground(drawable);
                }
              }
            }
            return false;
          });
    }
  }

  /*=====================添加OnItemClickListener回调================================*/
  public interface OnItemClickLitener {
    void onItemClick(View view, int position);
  }

  private OnItemClickLitener mOnItemClickLitener;

  public void setOnItemClickLitener(OnItemClickLitener mOnItemClickLitener) {
    this.mOnItemClickLitener = mOnItemClickLitener;
  }
}
