

package com.google.firebase.encoders.processor.getters;

import java.util.Set;
import javax.annotation.processing.Generated;
import javax.lang.model.type.TypeMirror;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Getter extends Getter {

  private final String name;

  private final Set<AnnotationDescriptor> annotationDescriptors;

  private final String expression;

  private final TypeMirror returnType;

  private final boolean inline;

  AutoValue_Getter(
      String name,
      Set<AnnotationDescriptor> annotationDescriptors,
      String expression,
      TypeMirror returnType,
      boolean inline) {
    if (name == null) {
      throw new NullPointerException("Null name");
    }
    this.name = name;
    if (annotationDescriptors == null) {
      throw new NullPointerException("Null annotationDescriptors");
    }
    this.annotationDescriptors = annotationDescriptors;
    if (expression == null) {
      throw new NullPointerException("Null expression");
    }
    this.expression = expression;
    if (returnType == null) {
      throw new NullPointerException("Null returnType");
    }
    this.returnType = returnType;
    this.inline = inline;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Set<AnnotationDescriptor> annotationDescriptors() {
    return annotationDescriptors;
  }

  @Override
  public String expression() {
    return expression;
  }

  @Override
  TypeMirror returnType() {
    return returnType;
  }

  @Override
  public boolean inline() {
    return inline;
  }

  @Override
  public String toString() {
    return "Getter{"
         + "name=" + name + ", "
         + "annotationDescriptors=" + annotationDescriptors + ", "
         + "expression=" + expression + ", "
         + "returnType=" + returnType + ", "
         + "inline=" + inline
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Getter) {
      Getter that = (Getter) o;
      return this.name.equals(that.name())
          && this.annotationDescriptors.equals(that.annotationDescriptors())
          && this.expression.equals(that.expression())
          && this.returnType.equals(that.returnType())
          && this.inline == that.inline();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= name.hashCode();
    h$ *= 1000003;
    h$ ^= annotationDescriptors.hashCode();
    h$ *= 1000003;
    h$ ^= expression.hashCode();
    h$ *= 1000003;
    h$ ^= returnType.hashCode();
    h$ *= 1000003;
    h$ ^= inline ? 1231 : 1237;
    return h$;
  }

}
