package com.osfans.trime.data.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.symbol.SimpleKeyBean;
import java.util.ArrayList;
import java.util.List;
import timber.log.Timber;

public class CollectionDao {

  private DbHelper helper;
  private static CollectionDao self;
  private static String DB_NAME = "collection.db";

  public static CollectionDao get() {
    if (null == self) self = new CollectionDao();
    return self;
  }

  public CollectionDao() {}

  /** 插入新记录 * */
  public void insert(@NonNull DbBean clipboardBean) {
    helper = new DbHelper(Trime.getService(), DB_NAME);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.execSQL(
        "insert into t_data(text,html,type,time) values(?,?,?,?)",
        new Object[] {
          clipboardBean.getText(),
          clipboardBean.getHtml(),
          clipboardBean.getType(),
          clipboardBean.getTime()
        });
    db.close();
  }

  /** 删除文字相同的剪贴板记录，插入新记录 * */
  public void add(@NonNull DbBean clipboardBean) {
    helper = new DbHelper(Trime.getService(), DB_NAME);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.delete("t_data", "text=?", new String[] {clipboardBean.getText()});
    db.execSQL(
        "insert into t_data(text,html,type,time) values(?,?,?,?)",
        new Object[] {
          clipboardBean.getText(),
          clipboardBean.getHtml(),
          clipboardBean.getType(),
          clipboardBean.getTime()
        });
    db.close();
  }

  /** 删除记录 * */
  public void delete(@NonNull String str) {
    helper = new DbHelper(Trime.getService(), DB_NAME);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.delete("t_data", "text=?", new String[] {str});
    db.close();
  }

  public void delete(@NonNull List<SimpleKeyBean> list) {
    helper = new DbHelper(Trime.getService(), DB_NAME);
    SQLiteDatabase db = helper.getWritableDatabase();
    for (SimpleKeyBean bean : list) db.delete("t_data", "text=?", new String[] {bean.getText()});
    db.close();
  }

  public List<SimpleKeyBean> getAllSimpleBean() {

    List<SimpleKeyBean> list = new ArrayList<>();

    String sql = "select text , html , type , time from t_data ORDER BY time DESC";

    helper = new DbHelper(Trime.getService(), DB_NAME);

    SQLiteDatabase db = helper.getWritableDatabase();
    Cursor cursor = db.rawQuery(sql, null);
    if (cursor != null) {
      while (cursor.moveToNext()) {
        DbBean v = new DbBean(cursor.getString(0));
        list.add(v);
      }
      cursor.close();
    }
    db.close();
    Timber.d("getAllSimpleBean() size=%s", list.size());
    return list;
  }
}
