package com.osfans.trime.data.db.draft;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import com.osfans.trime.data.db.DbBean;
import com.osfans.trime.data.db.DbHelper;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.symbol.SimpleKeyBean;
import java.util.ArrayList;
import java.util.List;
import timber.log.Timber;

public class DraftDao {

  private SQLiteOpenHelper helper;
  private static DraftDao self;
  private static String DB_NAME = "draft.db";

  public static DraftDao get() {
    if (null == self) self = new DraftDao();
    return self;
  }

  public DraftDao() {}

  /** 插入新记录 * */
  public void insert(@NonNull DbBean bean) {
    helper = new DbHelper(Trime.getService(), DB_NAME);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.execSQL(
        "insert into t_data(text,html,type,time) values(?,?,?,?)",
        new Object[] {bean.getText(), bean.getHtml(), bean.getType(), bean.getTime()});
    db.close();
  }

  /** 删除文字相同的记录，插入新记录 * */
  public void add(@NonNull DbBean bean) {
    helper = new DbHelper(Trime.getService(), DB_NAME);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.delete("t_data", "text=?", new String[] {bean.getText()});
    db.execSQL(
        "insert into t_data(text,html,type,time) values(?,?,?,?)",
        new Object[] {bean.getText(), bean.getHtml(), bean.getType(), bean.getTime()});
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

  public List<SimpleKeyBean> getAllSimpleBean(int size) {

    List<SimpleKeyBean> list = new ArrayList<>();
    if (size == 0) return list;

    String sql = "select text , html , type , time from t_data ORDER BY time DESC";
    if (size > 0) sql = sql + " limit 0," + size;

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
    Timber.d("DraftDao.getAllSimpleBean() size=%s limit=%s", list.size(), size);
    return list;
  }
}
