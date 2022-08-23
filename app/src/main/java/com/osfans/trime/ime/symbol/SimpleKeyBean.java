package com.osfans.trime.ime.symbol;

import androidx.annotation.NonNull;
import java.io.Serializable;

public class SimpleKeyBean implements Serializable {
  private String text;
  private String label;
  private int id;
  private boolean selected;

  public SimpleKeyBean() {}

  public SimpleKeyBean(String text) {
    this.text = text;
  }

  public SimpleKeyBean(int id, String text) {
    this.id = id;
    this.text = text;
  }

  public SimpleKeyBean(String text, String label) {
    this.text = text;
    this.label = label;
  }

  public SimpleKeyBean(int id, String text, String label) {
    this.id = id;
    this.text = text;
    this.label = label;
  }

  public int getId() {
    return id;
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

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  public boolean isSelected() {
    return selected;
  }

  @NonNull
  @Override
  public String toString() {
    return "SimpleKeyBean {" + "text='" + text + '\'' + ", label='" + label + '\'' + '}';
  }
}
