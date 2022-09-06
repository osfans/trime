package com.osfans.trime.ime.symbol;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.osfans.trime.data.db.CollectionDao;
import com.osfans.trime.data.db.DbBean;
import com.osfans.trime.databinding.CheckableItemBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import timber.log.Timber;

public class CheckableAdapter extends ArrayAdapter<SimpleKeyBean> {
  private final List<SimpleKeyBean> items;
  private final List<Integer> checked;

  public List<Integer> getChecked() {
    return checked;
  }

  public CheckableAdapter(@NonNull Context context, @NonNull List<SimpleKeyBean> items) {
    super(context, 0, items);
    this.items = items;
    this.checked = new ArrayList<>();
    Timber.tag("UserDictAdapter").i("set words.size=%s", items.size());
  }

  public void clickItem(int position) {
    if (checked.contains(position)) checked.remove((Integer) position);
    else checked.add(position);

    notifyDataSetChanged();
  }

  public List<SimpleKeyBean> remove(int index) {
    Collections.sort(checked, Collections.reverseOrder());
    List<SimpleKeyBean> result = new ArrayList<>();
    for (int i : checked) {
      if (i > items.size()) continue;
      result.add(items.get(i));
      items.remove(i);
    }
    checked.clear();

    if (index >= 0) checked.add(index);

    notifyDataSetChanged();
    return result;
  }

  public List<SimpleKeyBean> collectSelected() {
    Collections.sort(checked, Collections.reverseOrder());
    List<SimpleKeyBean> result = new ArrayList<>();
    for (int i : checked) {
      if (i > items.size()) continue;
      SimpleKeyBean bean = items.get(i);
      result.add(bean);
      CollectionDao.get().insert(new DbBean(bean.getText()));
    }
    return result;
  }

  @Override
  public int getCount() {
    return items.size();
  }

  @NonNull
  @Override
  public SimpleKeyBean getItem(int position) {
    return items.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  private static class ViewHolder {
    private final TextView textView;
    private final CheckBox checkBox;
    private final View root;

    public ViewHolder(@NonNull LayoutInflater layoutInflater) {
      CheckableItemBinding binding = CheckableItemBinding.inflate(layoutInflater);
      this.textView = binding.textView;
      this.checkBox = binding.checkBox;
      this.root = binding.getRoot();
    }
  }

  @Override
  public View getView(int position, View convertView, @NonNull ViewGroup parent) {
    // 优化后
    final ViewHolder holder = new ViewHolder(LayoutInflater.from(parent.getContext()));

    holder.checkBox.setText(items.get(position).getText());
    holder.checkBox.setChecked(checked.contains(position));

    return holder.root;
  }
}
