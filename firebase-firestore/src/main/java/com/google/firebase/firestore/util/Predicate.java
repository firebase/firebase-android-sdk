package com.google.firebase.firestore.util;

/** A port of {@link java.util.function.Predicate} */
@FunctionalInterface
public interface Predicate<T> {
  boolean test(T t);
}
