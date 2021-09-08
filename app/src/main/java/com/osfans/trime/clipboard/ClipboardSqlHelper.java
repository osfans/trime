package com.osfans.trime.clipboard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import timber.log.Timber;

public class ClipboardSqlHelper extends SQLiteOpenHelper {

  public static final String CREATE_STUDENT =
      "create table t_clipboard ("
          + "id integer primary key, text TEXT, html TEXT, type integer, time integer)";

  public ClipboardSqlHelper(
      Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
    super(context, name, factory, version);
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    Timber.i("open db");
    super.onOpen(db);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    Timber.i("create db");
    db.execSQL(CREATE_STUDENT);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
