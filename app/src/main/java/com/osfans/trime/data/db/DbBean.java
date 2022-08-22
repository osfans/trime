package com.osfans.trime.data.db;

import android.database.Cursor;
import com.osfans.trime.ime.symbol.SimpleKeyBean;

public class DbBean extends SimpleKeyBean {
  private int id;
  private long time;
  private String text;
  private String html = ""; // 草稿箱用此字段存储App信息，剪贴板用于存储html信息（未实装）
  private int type; // 0 text, 1 html

  public long getTime() {
    return time;
  }

  public String getHtml() {
    return html;
  }

  public String getText() {
    return text;
  }

  public int getType() {
    return type;
  }

  public void setText(String text) {
    this.text = text;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public void setType(int type) {
    this.type = type;
  }

  public DbBean(String text) {
    this.text = text;
    this.time = System.currentTimeMillis();
  }

  public DbBean(String text, String html) {
    this.text = text;
    this.html = html;
    this.time = System.currentTimeMillis();
  }

  public DbBean(int id, String text, String html) {
    this.id = id;
    this.text = text;
    this.html = html;
    this.time = System.currentTimeMillis();
  }

  public DbBean(Cursor cursor) {
    //  text , html , id,  type , time
    this.text = cursor.getString(0);
    this.html = cursor.getString(1);
    this.id = cursor.getInt(2);
  }
}
