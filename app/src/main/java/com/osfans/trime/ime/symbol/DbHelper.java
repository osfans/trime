package com.osfans.trime.ime.symbol;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import timber.log.Timber;

public class DbHelper extends SQLiteOpenHelper {

  public static final String CREATE_STUDENT =
      "create table if not exists  t_data ("
          + "id integer primary key, text TEXT, html TEXT, type integer, time integer)";

  public DbHelper(Context context, String name) {
    super(context, name, null, 3);
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    Timber.i("open db");
    super.onOpen(db);
  }

  @Override
  public void onCreate(@NonNull SQLiteDatabase db) {
    Timber.i("create db");
    db.execSQL(CREATE_STUDENT);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    if (oldVersion == 1) {
      Timber.i("onUpgrade() rename db");
      db.execSQL("ALTER TABLE t_clipboard RENAME TO t_data");
    } else {
      Timber.i("onUpgrade() create db");
      db.execSQL(CREATE_STUDENT);
    }
  }
}
