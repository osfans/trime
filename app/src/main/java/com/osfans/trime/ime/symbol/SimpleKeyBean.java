package com.osfans.trime.ime.symbol;

import androidx.annotation.NonNull;
import java.io.Serializable;

public class SimpleKeyBean implements Serializable {
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

  @NonNull
  @Override
  public String toString() {
    return "SimpleKeyBean {" + "text='" + text + '\'' + ", label='" + label + '\'' + '}';
  }
}
