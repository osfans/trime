package com.osfans.trime.ime.symbol;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.osfans.trime.R;
import com.osfans.trime.data.db.DbBean;
import com.osfans.trime.data.db.DbDao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckableAdatper extends ArrayAdapter {
  private LayoutInflater layoutInflater = null;
  private List<SimpleKeyBean> words;
  private String dbName;

  public List<Integer> getChecked() {
    return checked;
  }

  private List<Integer> checked;

  public String getCheckedText() {
    if (checked.size() == 1) return words.get(checked.get(0)).getText();
    if (checked.size() > 1) {
      StringBuffer buffer = new StringBuffer();
      for (int i : checked) {
        buffer.append(words.get(checked.get(i)).getText());
      }
      return buffer.toString();
    }
    return "";
  }

  public CheckableAdatper(
      @NonNull Context context, int resource, @NonNull List objects, String dbName) {
    super(context, resource, objects);
    layoutInflater = LayoutInflater.from(context);
    words = objects;
    checked = new ArrayList<>();
    this.dbName = dbName;
    Log.i("UserDictAdatper", "set words.size=" + words.size());
  }

  public void clickItem(int position) {
    if (checked.contains(position)) checked.remove((Integer) position);
    else checked.add(position);

    notifyDataSetChanged();
  }

  public void updateItem(String text) {
    if (checked.isEmpty()) {
      new DbDao(dbName).add(new DbBean(text));
      SimpleKeyBean bean = new SimpleKeyBean(text);
      words.add(0, bean);
    } else {
      int position = checked.get(0);
      new DbDao(dbName).update(words.get(position).getText(), text);
      SimpleKeyBean bean = words.get(position);
      bean.setText(text);
      words.set(position, bean);
    }
    notifyDataSetChanged();
  }

  public void mergeItem() {

    StringBuffer buffer = new StringBuffer();

    for (int i : checked) {
      buffer.append("\n");
      buffer.append(words.get(i).getText());
    }
    String text = buffer.toString().trim();

    Collections.sort(checked, Collections.reverseOrder());
    List<SimpleKeyBean> result = new ArrayList<>();
    for (int i : checked) {
      if (i > words.size()) continue;
      result.add(words.get(i));
      words.remove(i);
    }
    checked.clear();
    new DbDao(dbName).delete(result);
    new DbDao(dbName).add(new DbBean(text));

    SimpleKeyBean simpleKeyBean = new SimpleKeyBean(text);
    words.add(0, simpleKeyBean);
    notifyDataSetChanged();
  }

  public List<SimpleKeyBean> remove(int checkPositon) {
    Collections.sort(checked, Collections.reverseOrder());
    List<SimpleKeyBean> result = new ArrayList<>();
    for (int i : checked) {
      if (i > words.size()) continue;
      result.add(words.get(i));
      words.remove(i);
    }
    checked.clear();

    if (checkPositon >= 0) checked.add(checkPositon);

    notifyDataSetChanged();
    return result;
  }

  public List<SimpleKeyBean> collectSelected() {
    Collections.sort(checked, Collections.reverseOrder());
    List<SimpleKeyBean> result = new ArrayList<>();
    for (int i : checked) {
      if (i > words.size()) continue;
      SimpleKeyBean bean = words.get(i);
      result.add(bean);
      new DbDao(DbDao.COLLECTION).add(new DbBean(bean.getText()));
    }
    return result;
  }

  @Override
  public int getCount() {
    return words.size();
  }

  @Override
  public Object getItem(int position) {
    return words.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  private static class ViewHolder {
    private TextView textView;
    private CheckBox checkBox;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    // 优化后
    ViewHolder holder;
    if (convertView == null) {
      convertView = layoutInflater.inflate(R.layout.checkable_item, null);
      holder = new ViewHolder();
      holder.textView = (TextView) convertView.findViewById(R.id.textView);
      holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkBox);
      convertView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }
    holder.checkBox.setText(words.get(position).getText());
    holder.checkBox.setChecked(checked.contains(position));
    return convertView;
  }
}
