package com.osfans.trime.ime.symbol;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.osfans.trime.data.Config;
import com.osfans.trime.databinding.SimpleKeyItemBinding;
import java.util.ArrayList;
import java.util.List;

public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.ViewHolder> {
  private final @NonNull List<SimpleKeyBean> mBeans = new ArrayList<>();

  @NonNull
  public final List<SimpleKeyBean> getBeans() {
    return mBeans;
  }

  private final Config theme;

  public SimpleAdapter(@NonNull Config theme) {
    this.theme = theme;
  }

  public void updateBeans(List<SimpleKeyBean> beans) {
    mBeans.clear();
    mBeans.addAll(beans);
  }

  @Override
  public int getItemCount() {
    return mBeans.size();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final SimpleKeyItemBinding binding =
        SimpleKeyItemBinding.inflate(LayoutInflater.from(parent.getContext()));
    binding.simpleKeyPin.setVisibility(View.INVISIBLE);
    return new ViewHolder(binding);
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    final TextView simpleKeyText;

    public ViewHolder(@NonNull SimpleKeyItemBinding binding) {
      super(binding.getRoot());

      simpleKeyText = binding.simpleKey;
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    final SimpleKeyBean bean = mBeans.get(position);

    holder.simpleKeyText.setText(bean.getLabel());
    holder.simpleKeyText.setTextColor(theme.getLiquidColor("key_text_color"));
    holder.simpleKeyText.setTypeface(theme.getFont("key_font"));
    holder.simpleKeyText.setGravity(Gravity.CENTER);
    holder.simpleKeyText.setEllipsize(TextUtils.TruncateAt.MARQUEE);

    final float labelTextSize = theme.getFloat("label_text_size");
    if (labelTextSize > 0) holder.simpleKeyText.setTextSize(labelTextSize);

    holder.itemView.setBackground(
        theme.getDrawable(
            "key_back_color", "key_border", "key_border_color", "round_corner", null));

    if (listener != null) {
      holder.itemView.setOnClickListener(
          view -> {
            listener.onSimpleKeyClick(position);
          });
    }
  }

  /*=====================添加OnItemClickListener回调================================*/
  public interface Listener {
    void onSimpleKeyClick(int position);
  }

  private Listener listener;

  public void setListener(@NonNull Listener listener) {
    this.listener = listener;
  }
}
