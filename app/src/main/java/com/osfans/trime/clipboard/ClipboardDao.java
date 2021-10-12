package com.osfans.trime.clipboard;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.symbol.SimpleKeyBean;
import java.util.ArrayList;
import java.util.List;
import timber.log.Timber;

public class ClipboardDao {

  private SQLiteOpenHelper helper;
  private static ClipboardDao self;

  public static ClipboardDao get() {
    if (null == self) self = new ClipboardDao();
    return self;
  }

  public ClipboardDao() {}

  public void insert(@NonNull ClipboardBean clipboardBean) {
    helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.execSQL(
        "insert into t_clipboard(text,html,type,time) values(?,?,?,?)",
        new Object[] {
          clipboardBean.getText(),
          clipboardBean.getHtml(),
          clipboardBean.getType(),
          clipboardBean.getTime()
        });
    db.close();
  }

  /** 删除文字相同的剪贴板记录，插入新记录 * */
  public void add(@NonNull ClipboardBean clipboardBean) {
    helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.delete("t_clipboard", "text=?", new String[] {clipboardBean.getText()});
    db.execSQL(
        "insert into t_clipboard(text,html,type,time) values(?,?,?,?)",
        new Object[] {
          clipboardBean.getText(),
          clipboardBean.getHtml(),
          clipboardBean.getType(),
          clipboardBean.getTime()
        });
    db.close();
  }

  public void update(@NonNull ClipboardBean clipboardBean) {
    helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.execSQL(
        "insert into t_clipboard(text,html,type,time) values(?,?,?,?)",
        new Object[] {
          clipboardBean.getText(),
          clipboardBean.getHtml(),
          clipboardBean.getType(),
          clipboardBean.getTime()
        });
    db.close();
  }

  public List<SimpleKeyBean> getAllSimpleBean(int size) {

    List<SimpleKeyBean> list = new ArrayList<>();
    if (size == 0) return list;

    String sql = "select text , html , type , time from t_clipboard ORDER BY time DESC";
    if (size > 0) sql = sql + " limit 0," + size;

    helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);

    SQLiteDatabase db = helper.getWritableDatabase();
    Cursor cursor = db.rawQuery(sql, null);
    if (cursor != null) {
      while (cursor.moveToNext()) {
        ClipboardBean v = new ClipboardBean(cursor.getString(0));
        list.add(v);
      }
      cursor.close();
    }
    db.close();
    Timber.d("getAllSimpleBean() size=%s limit=%s", list.size(), size);
    return list;
  }
}
