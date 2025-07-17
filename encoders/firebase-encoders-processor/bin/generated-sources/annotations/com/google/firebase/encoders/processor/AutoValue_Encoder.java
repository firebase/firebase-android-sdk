

package com.google.firebase.encoders.processor;

import com.squareup.javapoet.TypeSpec;
import javax.annotation.processing.Generated;
import javax.lang.model.type.TypeMirror;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Encoder extends Encoder {

  private final TypeMirror type;

  private final TypeSpec code;

  AutoValue_Encoder(
      TypeMirror type,
      TypeSpec code) {
    if (type == null) {
      throw new NullPointerException("Null type");
    }
    this.type = type;
    if (code == null) {
      throw new NullPointerException("Null code");
    }
    this.code = code;
  }

  @Override
  TypeMirror type() {
    return type;
  }

  @Override
  TypeSpec code() {
    return code;
  }

  @Override
  public String toString() {
    return "Encoder{"
         + "type=" + type + ", "
         + "code=" + code
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Encoder) {
      Encoder that = (Encoder) o;
      return this.type.equals(that.type())
          && this.code.equals(that.code());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= type.hashCode();
    h$ *= 1000003;
    h$ ^= code.hashCode();
    return h$;
  }

}
