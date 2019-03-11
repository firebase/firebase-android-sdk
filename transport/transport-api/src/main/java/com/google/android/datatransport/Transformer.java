package com.google.android.datatransport;

public interface Transformer<T, U> {
  U apply(T value);
}
