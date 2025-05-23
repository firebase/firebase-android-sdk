package com.google.firebase.firestore.util;

/** A port of {@link java.util.function.IntFunction} */
@FunctionalInterface
public interface IntFunction<R> {
  R apply(int value);
}
