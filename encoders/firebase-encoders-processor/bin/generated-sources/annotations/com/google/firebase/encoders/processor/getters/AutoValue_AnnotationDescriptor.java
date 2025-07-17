

package com.google.firebase.encoders.processor.getters;

import java.util.List;
import javax.annotation.processing.Generated;
import javax.lang.model.element.AnnotationMirror;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_AnnotationDescriptor extends AnnotationDescriptor {

  private final AnnotationMirror type;

  private final List<AnnotationProperty> properties;

  AutoValue_AnnotationDescriptor(
      AnnotationMirror type,
      List<AnnotationProperty> properties) {
    if (type == null) {
      throw new NullPointerException("Null type");
    }
    this.type = type;
    if (properties == null) {
      throw new NullPointerException("Null properties");
    }
    this.properties = properties;
  }

  @Override
  public AnnotationMirror type() {
    return type;
  }

  @Override
  public List<AnnotationProperty> properties() {
    return properties;
  }

  @Override
  public String toString() {
    return "AnnotationDescriptor{"
         + "type=" + type + ", "
         + "properties=" + properties
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof AnnotationDescriptor) {
      AnnotationDescriptor that = (AnnotationDescriptor) o;
      return this.type.equals(that.type())
          && this.properties.equals(that.properties());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= type.hashCode();
    h$ *= 1000003;
    h$ ^= properties.hashCode();
    return h$;
  }

}
