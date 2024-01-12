package com.osfans.trime.ime.symbol;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.blankj.utilcode.util.SizeUtils;
import com.osfans.trime.data.theme.FontManager;
import com.osfans.trime.data.theme.Theme;
import com.osfans.trime.databinding.SimpleItemOneBinding;
import com.osfans.trime.databinding.SimpleItemRowBinding;
import java.util.ArrayList;
import java.util.List;

public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.ViewHolder> {
  private final @NonNull List<SimpleKeyBean> mBeans = new ArrayList<>();
  private final @NonNull List<List<SimpleKeyBean>> mBeansByRows = new ArrayList<>();

  @NonNull
  public final List<SimpleKeyBean> getBeans() {
    return mBeans;
  }

  private final Theme theme;
  private final int columnSize;

  public SimpleAdapter(@NonNull Theme theme, int columnSize) {
    this.theme = theme;
    this.columnSize = columnSize;
  }

  public void updateBeans(List<SimpleKeyBean> beans) {
    mBeans.clear();
    mBeans.addAll(beans);
    mBeansByRows.clear();

    ArrayList<SimpleKeyBean> t = new ArrayList<>();
    for (int i = 0; i < mBeans.size(); i++) {
      t.add(mBeans.get(i));
      if ((i + 1) % columnSize == 0) {
        mBeansByRows.add(t);
        t = new ArrayList<>();
      }
    }
    if (!t.isEmpty()) {
      mBeansByRows.add(t);
    }

    notifyDataSetChanged();
  }

  @Override
  public int getItemCount() {
    return mBeansByRows.size();
  }

  @Override
  public long getItemId(int position) {
    return position * 1000L;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final SimpleItemRowBinding binding =
        SimpleItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
    int size = SizeUtils.dp2px(theme.liquid.getFloat("single_width"));
    ViewGroup.LayoutParams p = new ViewGroup.LayoutParams(size, size);

    List<SimpleItemOneBinding> bindings = new ArrayList<>();
    for (int i = 0; i < columnSize; i++) {
      SimpleItemOneBinding view =
          SimpleItemOneBinding.inflate(LayoutInflater.from(parent.getContext()), null, false);
      bindings.add(view);
      binding.wrapper.addView(view.getRoot(), p);
    }
    ViewHolder holder = new ViewHolder(binding, bindings);

    for (int i = 0; i < holder.simpleKeyTexts.size(); i++) {
      holder.wrappers.get(i).setTag((Integer) i);

      TextView textView = holder.simpleKeyTexts.get(i);

      textView.setTextColor(theme.colors.getColor("key_text_color"));
      textView.setTypeface(FontManager.getTypeface(theme.style.getString("key_font")));
      textView.setGravity(Gravity.CENTER);
      textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);

      final float labelTextSize = theme.style.getFloat("label_text_size");
      if (labelTextSize > 0) textView.setTextSize(labelTextSize);

      textView.setBackground(
          theme.colors.getDrawable(
              "key_back_color", "key_border", "key_border_color", "round_corner", null));
    }
    return holder;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    List<TextView> simpleKeyTexts;
    List<ViewGroup> wrappers;

    public ViewHolder(@NonNull SimpleItemRowBinding binding, List<SimpleItemOneBinding> views) {
      super(binding.getRoot());

      simpleKeyTexts = new ArrayList<>();
      wrappers = new ArrayList<>();
      for (int i = 0; i < views.size(); i++) {
        simpleKeyTexts.add((TextView) (views.get(i).getRoot().getChildAt(0)));
        ((ViewGroup) views.get(i).getRoot()).getChildAt(1).setVisibility(View.GONE);
        wrappers.add(views.get(i).getRoot());
      }
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    final List<SimpleKeyBean> bean = mBeansByRows.get(position);

    for (int i = 0; i < holder.simpleKeyTexts.size(); i++) {
      holder.simpleKeyTexts.get(i).setText("");
      if (i < bean.size()) {
        holder.wrappers.get(i).setVisibility(View.VISIBLE);
        holder.simpleKeyTexts.get(i).setText(bean.get(i).getLabel());
      } else {
        holder.wrappers.get(i).setVisibility(View.INVISIBLE);
      }

      if (listener != null) {
        holder
            .wrappers
            .get(i)
            .setOnClickListener(
                view -> {
                  if (view.getTag() != null) {
                    listener.onSimpleKeyClick(position * columnSize + (int) view.getTag());
                  }
                });
      }
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
