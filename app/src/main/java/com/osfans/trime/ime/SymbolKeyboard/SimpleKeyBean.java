package com.osfans.trime.ime.SymbolKeyboard;

public class SimpleKeyBean {
  private String text;
  private String label;

  public SimpleKeyBean() {}

  public SimpleKeyBean(String text) {
    this.text = text;
  }

  public SimpleKeyBean(String text, String label) {
    this.text = text;
    this.label = label;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getText() {
    return text;
  }

  public String getLabel() {
    return label == null ? text : label;
  }
}
