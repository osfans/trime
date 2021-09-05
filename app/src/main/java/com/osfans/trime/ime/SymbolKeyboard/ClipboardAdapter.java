package com.osfans.trime.ime.SymbolKeyboard;

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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.osfans.trime.R;
import com.osfans.trime.setup.Config;
import java.util.List;

public class ClipboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private final Context myContext;
  private final List<SimpleKeyBean> list;
  private int keyMarginX, keyMarginTop;
  private Integer textColor;
  private float textSize;
  private Typeface textFont;
  private Drawable background;

  public ClipboardAdapter(Context context, List<SimpleKeyBean> itemlist) {
    myContext = context;
    list = itemlist;
  }

  @Override
  public int getItemCount() {
    return list.size();
  }

  public void configStyle(int keyMarginX, int keyMarginTop) {
    this.keyMarginX = keyMarginX;
    this.keyMarginTop = keyMarginTop;

    //  边框尺寸、圆角、字号直接读取主题通用参数。配色优先读取liquidKeyboard专用参数。
    Config config = Config.get(myContext);
    textColor = config.getLiquidColor("long_text_color");
    if (textColor == null) textColor = config.getLiquidColor("key_text_color");

    textSize = config.getFloat("key_long_text_size");
    if (textSize <= 0) textSize = config.getFloat("label_text_size");

    background =
        config.getDrawable(
            "long_text_back_color", "key_border", "key_long_text_border", "round_corner", null);

    textFont = config.getFont("long_text_font");
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(myContext).inflate(R.layout.simple_key_item, parent, false);
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

    if (viewHolder instanceof ClipboardAdapter.ItemViewHolder) {
      SimpleKeyBean searchHistoryBean = list.get(index);
      final ClipboardAdapter.ItemViewHolder itemViewHold =
          ((ClipboardAdapter.ItemViewHolder) viewHolder);

      if (textFont != null) itemViewHold.mTitle.setTypeface(textFont);
      itemViewHold.mTitle.setText(searchHistoryBean.getText());

      if (textSize > 0) itemViewHold.mTitle.setTextSize(textSize);

      ViewGroup.LayoutParams lp = itemViewHold.listItemLayout.getLayoutParams();
      if (lp instanceof FlexboxLayoutManager.LayoutParams) {
        FlexboxLayoutManager.LayoutParams flexboxLp =
            (FlexboxLayoutManager.LayoutParams) itemViewHold.listItemLayout.getLayoutParams();

        itemViewHold.mTitle.setTextColor(textColor);

        int marginTop = flexboxLp.getMarginTop();
        int marginX = flexboxLp.getMarginLeft();
        if (keyMarginTop > 0) marginTop = keyMarginTop;
        if (keyMarginX > 0) marginX = keyMarginX;

        flexboxLp.setMargins(marginX, marginTop, marginX, flexboxLp.getMarginBottom());

        // TODO 设置剪贴板列表样式
        // copy SimpleAdapter会造成高度始终为3行无法自适应的效果。

      }

      if (background != null) itemViewHold.listItemLayout.setBackground(background);

      // 如果设置了回调，则设置点击事件
      if (mOnItemClickLitener != null) {
        itemViewHold.listItemLayout.setOnClickListener(
            view -> {
              int position = itemViewHold.getLayoutPosition();
              mOnItemClickLitener.onItemClick(itemViewHold.listItemLayout, position);
            });
      }

      itemViewHold.listItemLayout.setOnLongClickListener(
          view -> {
            int position = itemViewHold.getLayoutPosition();
            //  TODO 长按删除、编辑剪贴板
            //  当文本较长时，目前样式只缩略显示为3行，长按时tosat消息可以预览全文，略有用处。
            Toast.makeText(myContext, list.get(position).getText(), Toast.LENGTH_SHORT).show();
            return true;
          });

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

  /*=====================添加OnItemClickListener回调================================*/
  public interface OnItemClickLitener {
    void onItemClick(View view, int position);
  }

  private ClipboardAdapter.OnItemClickLitener mOnItemClickLitener;

  public void setOnItemClickLitener(ClipboardAdapter.OnItemClickLitener mOnItemClickLitener) {
    this.mOnItemClickLitener = mOnItemClickLitener;
  }
}
