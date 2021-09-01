package com.osfans.trime.ime.SymbolKeyboard;

public class ClipboardBean extends SimpleKeyBean {
  private long time;
  private String text;
  private final String html;
  private int type;

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

  public ClipboardBean(String text) {
    this.text = text;
    this.time = System.currentTimeMillis();
    this.type = 0;
    this.html = "";
  }

  public ClipboardBean(String text, String html) {
    this.text = text;
    this.time = System.currentTimeMillis();
    this.type = 1;
    this.html = html;
  }

  public ClipboardBean(String text, String html, int type, long time) {
    this.text = text;
    this.time = time;
    this.type = type;
    this.html = html;
  }
}
