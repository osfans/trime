package com.osfans.trime.data.db;

import com.osfans.trime.ime.symbol.SimpleKeyBean;

public class DbBean extends SimpleKeyBean {
  private long time;
  private String text;
  private final String html; // 草稿箱用此字段存储App信息，剪贴板用于存储html信息（未实装）
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
    this.type = 0;
    this.html = "";
  }

  @SuppressWarnings("unused")
  public DbBean(String text, String html) {
    this.text = text;
    this.time = System.currentTimeMillis();
    this.type = 1;
    this.html = html;
  }

  @SuppressWarnings("unused")
  public DbBean(String text, String html, int type, long time) {
    this.text = text;
    this.time = time;
    this.type = type;
    this.html = html;
  }
}
