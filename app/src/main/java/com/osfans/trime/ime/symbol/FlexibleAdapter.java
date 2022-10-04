package com.osfans.trime.ime.symbol;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.blankj.utilcode.util.ToastUtils;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.osfans.trime.data.Config;
import com.osfans.trime.databinding.SimpleKeyItemBinding;
import java.util.List;

public class FlexibleAdapter extends RecyclerView.Adapter<FlexibleAdapter.ViewHolder> {
  private final List<SimpleKeyBean> beans;
  private int keyMarginLeft, keyMarginTop;
  private Integer textColor;
  private float textSize;
  private Typeface textFont;
  private Config config;

  public FlexibleAdapter(@NonNull List<SimpleKeyBean> beans) {
    this.beans = beans;
  }

  @Override
  public int getItemCount() {
    return beans.size();
  }

  public void configStyle(int keyMarginLeft, int keyMarginTop) {
    this.keyMarginLeft = keyMarginLeft;
    this.keyMarginTop = keyMarginTop;

    //  边框尺寸、圆角、字号直接读取主题通用参数。配色优先读取 liquidKeyboard 专用参数。
    config = Config.get();
    textColor = config.getLiquidColor("long_text_color");
    if (textColor == null) textColor = config.getLiquidColor("key_text_color");

    textSize = config.getFloat("key_long_text_size");
    if (textSize <= 0) textSize = config.getFloat("label_text_size");

    textFont = config.getFont("long_text_font");
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final SimpleKeyItemBinding binding =
        SimpleKeyItemBinding.inflate(LayoutInflater.from(parent.getContext()));
    return new ViewHolder(binding);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final TextView simpleKeyText;

    public ViewHolder(@NonNull SimpleKeyItemBinding binding) {
      super(binding.getRoot());

      simpleKeyText = binding.simpleKey;
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder viewHolder, int index) {
    final SimpleKeyBean bean = beans.get(index);

    if (textFont != null) viewHolder.simpleKeyText.setTypeface(textFont);

    final String beanText = bean.getText();
    if (beanText.length() > 300) viewHolder.simpleKeyText.setText(beanText.substring(0, 300));
    else viewHolder.simpleKeyText.setText(beanText);

    if (textSize > 0) viewHolder.simpleKeyText.setTextSize(textSize);

    final ViewGroup.LayoutParams lp = viewHolder.itemView.getLayoutParams();
    if (lp instanceof FlexboxLayoutManager.LayoutParams) {
      final FlexboxLayoutManager.LayoutParams flexboxLp = (FlexboxLayoutManager.LayoutParams) lp;

      viewHolder.simpleKeyText.setTextColor(textColor);

      final int marginLeft = keyMarginLeft > 0 ? keyMarginLeft : flexboxLp.leftMargin;
      final int marginTop = keyMarginTop > 0 ? keyMarginTop : flexboxLp.topMargin;

      flexboxLp.setMargins(marginLeft, marginTop, marginLeft, flexboxLp.bottomMargin);

      // TODO 设置剪贴板列表样式
      // copy SimpleAdapter 会造成高度始终为 3 行无法自适应的效果。

    }

    final int backColor = config.getColor("long_text_back_color");
    // if (background != null) viewHolder.itemView.setBackground(background);
    ((CardView) viewHolder.itemView).setCardBackgroundColor(backColor);

    // 如果设置了回调，则设置点击事件
    if (mOnItemClickListener != null) {
      viewHolder.itemView.setOnClickListener(
          view -> {
            int position = viewHolder.getLayoutPosition();
            mOnItemClickListener.onItemClick(viewHolder.itemView, position);
          });
    }

    viewHolder.itemView.setOnLongClickListener(
        view -> {
          int position = viewHolder.getLayoutPosition();
          //  TODO 长按删除、编辑剪贴板
          //  当文本较长时，目前样式只缩略显示为 3 行，长按时 toast 消息可以预览全文，略有用处。
          ToastUtils.showShort(beans.get(position).getText());
          return true;
        });

    viewHolder.itemView.setClickable(true);
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
