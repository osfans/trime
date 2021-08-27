package com.osfans.trime.ime.SymbolKeyboard;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.osfans.trime.ime.core.Trime;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class ClipboardDao {

    private SQLiteOpenHelper helper;
    private static ClipboardDao self;

    public static ClipboardDao get() {
        if (null == self)
            self = new ClipboardDao();
        return self;
    }

    public ClipboardDao() {
    }

    public void insert(ClipboardBean clipboardBean) {

        helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("insert into t_clipboard(text,html,type,time) values(?,?,?,?)"
                , new Object[]{clipboardBean.getText(), clipboardBean.getHtml(), clipboardBean.getType()
                        , clipboardBean.getTime()});
        db.close();
    }

    //  删除文字相同的剪贴板记录，插入新记录
    public void add(ClipboardBean clipboardBean) {
        helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("t_clipboard", "text=?", new String[]{clipboardBean.getText()});
        db.execSQL("insert into t_clipboard(text,html,type,time) values(?,?,?,?)"
                , new Object[]{clipboardBean.getText(), clipboardBean.getHtml(), clipboardBean.getType()
                        , clipboardBean.getTime()});
        db.close();
    }


    public void update(ClipboardBean clipboardBean) {

        helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("insert into t_clipboard(text,html,type,time) values(?,?,?,?)"
                , new Object[]{clipboardBean.getText(), clipboardBean.getHtml(), clipboardBean.getType(), clipboardBean.getTime()});
        db.close();
    }


    public List<SimpleKeyBean> getAllSimpleBean() {

        List<SimpleKeyBean> list = new ArrayList<>();
        helper = new ClipboardSqlHelper(Trime.getService(), "clipboard.db", null, 1);

        SQLiteDatabase db = helper.getWritableDatabase();

        Cursor cursor = db.rawQuery("select text , html , type , time from t_clipboard"
//             +   " where del = false"
                        + "  ORDER BY time DESC"
                , null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ClipboardBean v = new ClipboardBean(cursor.getString(0));
                list.add(v);
            }
            cursor.close();
        }
        db.close();
        Timber.d("getAllSimpleBean() size=%s", list.size());
        return list;
    }
}