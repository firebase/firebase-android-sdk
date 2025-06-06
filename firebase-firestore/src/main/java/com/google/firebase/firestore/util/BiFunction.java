package com.google.firebase.firestore.util;

/** A port of {@link java.util.function.BiFunction} */
@FunctionalInterface
public interface BiFunction<T, U, R> {
  R apply(T t, U u);
}
