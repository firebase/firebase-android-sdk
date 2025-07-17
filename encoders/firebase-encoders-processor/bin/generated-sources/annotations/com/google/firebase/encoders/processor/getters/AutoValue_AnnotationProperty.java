

package com.google.firebase.encoders.processor.getters;

import javax.annotation.processing.Generated;
import javax.lang.model.element.AnnotationValue;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_AnnotationProperty extends AnnotationProperty {

  private final String name;

  private final AnnotationValue value;

  AutoValue_AnnotationProperty(
      String name,
      AnnotationValue value) {
    if (name == null) {
      throw new NullPointerException("Null name");
    }
    this.name = name;
    if (value == null) {
      throw new NullPointerException("Null value");
    }
    this.value = value;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public AnnotationValue value() {
    return value;
  }

  @Override
  public String toString() {
    return "AnnotationProperty{"
         + "name=" + name + ", "
         + "value=" + value
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof AnnotationProperty) {
      AnnotationProperty that = (AnnotationProperty) o;
      return this.name.equals(that.name())
          && this.value.equals(that.value());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= name.hashCode();
    h$ *= 1000003;
    h$ ^= value.hashCode();
    return h$;
  }

}
