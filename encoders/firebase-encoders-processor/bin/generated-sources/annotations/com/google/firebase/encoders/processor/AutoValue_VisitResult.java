

package com.google.firebase.encoders.processor;

import androidx.annotation.Nullable;
import java.util.Set;
import javax.annotation.processing.Generated;
import javax.lang.model.type.TypeMirror;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_VisitResult<T> extends VisitResult<T> {

  private final Set<TypeMirror> pendingToVisit;

  private final T result;

  AutoValue_VisitResult(
      Set<TypeMirror> pendingToVisit,
      @Nullable T result) {
    if (pendingToVisit == null) {
      throw new NullPointerException("Null pendingToVisit");
    }
    this.pendingToVisit = pendingToVisit;
    this.result = result;
  }

  @Override
  Set<TypeMirror> pendingToVisit() {
    return pendingToVisit;
  }

  @Nullable
  @Override
  T result() {
    return result;
  }

  @Override
  public String toString() {
    return "VisitResult{"
         + "pendingToVisit=" + pendingToVisit + ", "
         + "result=" + result
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof VisitResult) {
      VisitResult<?> that = (VisitResult<?>) o;
      return this.pendingToVisit.equals(that.pendingToVisit())
          && (this.result == null ? that.result() == null : this.result.equals(that.result()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= pendingToVisit.hashCode();
    h$ *= 1000003;
    h$ ^= (result == null) ? 0 : result.hashCode();
    return h$;
  }

}
