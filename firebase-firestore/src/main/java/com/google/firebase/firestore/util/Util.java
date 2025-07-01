// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.util;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Continuation;
import com.google.cloud.datastore.core.number.NumberComparisonHelper;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;

/** A utility class for Firestore */
public class Util {
  private static final int AUTO_ID_LENGTH = 20;

  private static final String AUTO_ID_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private static final Random rand = new SecureRandom();

  public static String autoId() {
    StringBuilder builder = new StringBuilder();
    int maxRandom = AUTO_ID_ALPHABET.length();
    for (int i = 0; i < AUTO_ID_LENGTH; i++) {
      builder.append(AUTO_ID_ALPHABET.charAt(rand.nextInt(maxRandom)));
    }
    return builder.toString();
  }

  /**
   * Utility function to compare booleans. Note that we can't use Boolean.compare because it's only
   * available after Android 19.
   */
  public static int compareBooleans(boolean b1, boolean b2) {
    if (b1 == b2) {
      return 0;
    } else if (b1) {
      return 1;
    } else {
      return -1;
    }
  }

  /**
   * Utility function to compare integers. Note that we can't use Integer.compare because it's only
   * available after Android 19.
   */
  public static int compareIntegers(int i1, int i2) {
    if (i1 < i2) {
      return -1;
    } else if (i1 > i2) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * Utility function to compare longs. Note that we can't use Long.compare because it's only
   * available after Android 19.
   */
  public static int compareLongs(long i1, long i2) {
    return NumberComparisonHelper.compareLongs(i1, i2);
  }

  /** Utility function to compare doubles (using Firestore semantics for NaN). */
  public static int compareDoubles(double i1, double i2) {
    return NumberComparisonHelper.firestoreCompareDoubles(i1, i2);
  }

  /** Compares a double and a long (using Firestore semantics for NaN). */
  public static int compareMixed(double doubleValue, long longValue) {
    return NumberComparisonHelper.firestoreCompareDoubleWithLong(doubleValue, longValue);
  }

  public static <T extends Comparable<T>> Comparator<T> comparator() {
    return Comparable::compareTo;
  }

  public static FirebaseFirestoreException exceptionFromStatus(Status error) {
    StatusException statusException = error.asException();
    return new FirebaseFirestoreException(
        statusException.getMessage(), Code.fromValue(error.getCode().value()), statusException);
  }

  /**
   * If an exception is a StatusException, convert it to a FirebaseFirestoreException. Otherwise,
   * leave it untouched.
   */
  private static Exception convertStatusException(Exception e) {
    if (e instanceof StatusException) {
      StatusException statusException = (StatusException) e;
      return exceptionFromStatus(statusException.getStatus());
    } else if (e instanceof StatusRuntimeException) {
      StatusRuntimeException statusRuntimeException = (StatusRuntimeException) e;
      return exceptionFromStatus(statusRuntimeException.getStatus());
    } else {
      return e;
    }
  }

  /** Turns a Throwable into an exception, converting it from a StatusException if necessary. */
  public static Exception convertThrowableToException(Throwable t) {
    if (t instanceof Exception) {
      return Util.convertStatusException((Exception) t);
    } else {
      return new Exception(t);
    }
  }

  private static final Continuation<Void, Void> VOID_ERROR_TRANSFORMER =
      task -> {
        if (task.isSuccessful()) {
          return task.getResult();
        }
        Exception e = Util.convertStatusException(task.getException());
        if (e instanceof FirebaseFirestoreException) {
          throw e;
        } else {
          throw new FirebaseFirestoreException(e.getMessage(), Code.UNKNOWN, e);
        }
      };

  public static Continuation<Void, Void> voidErrorTransformer() {
    return VOID_ERROR_TRANSFORMER;
  }

  /**
   * Converts varargs from an update call to a list of objects, ensuring that the arguments
   * alternate between String/FieldPath and Objects.
   *
   * @param fieldPathOffset The offset of the first field path in the original update API (used as
   *     the index in error messages)
   */
  public static List<Object> collectUpdateArguments(
      int fieldPathOffset, Object field, Object val, Object... fieldsAndValues) {
    if (fieldsAndValues.length % 2 == 1) {
      throw new IllegalArgumentException(
          "Missing value in call to update().  There must be an even number of "
              + "arguments that alternate between field names and values");
    }
    List<Object> argumentList = new ArrayList<>();
    argumentList.add(field);
    argumentList.add(val);
    Collections.addAll(argumentList, fieldsAndValues);

    for (int i = 0; i < argumentList.size(); i += 2) {
      Object fieldPath = argumentList.get(i);
      if (!(fieldPath instanceof String) && !(fieldPath instanceof FieldPath)) {
        throw new IllegalArgumentException(
            "Excepted field name at argument position "
                + (i + fieldPathOffset + 1)
                + " but got "
                + fieldPath
                + " in call to update.  The arguments to update "
                + "should alternate between field names and values");
      }
    }

    return argumentList;
  }

  public static String toDebugString(ByteString bytes) {
    int size = bytes.size();
    StringBuilder result = new StringBuilder(2 * size);
    for (int i = 0; i < size; i++) {
      int value = bytes.byteAt(i) & 0xFF;
      result.append(Character.forDigit(value >>> 4, 16));
      result.append(Character.forDigit(value & 0xF, 16));
    }
    return result.toString();
  }

  /** Describes the type of an object, handling null objects gracefully. */
  public static String typeName(@Nullable Object obj) {
    return obj == null ? "null" : obj.getClass().getName();
  }

  /** Raises an exception on Android's UI Thread and crashes the end user's app. */
  // TODO(b/258277574): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  public static void crashMainThread(RuntimeException exception) {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              throw exception;
            });
  }

  public static int compareByteArrays(byte[] left, byte[] right) {
    int size = Math.min(left.length, right.length);
    for (int i = 0; i < size; i++) {
      // Make sure the bytes are unsigned
      int thisByte = left[i] & 0xff;
      int otherByte = right[i] & 0xff;
      if (thisByte < otherByte) {
        return -1;
      } else if (thisByte > otherByte) {
        return 1;
      }
      // Byte values are equal, continue with comparison
    }
    return Util.compareIntegers(left.length, right.length);
  }

  public static int compareByteStrings(ByteString left, ByteString right) {
    int size = Math.min(left.size(), right.size());
    for (int i = 0; i < size; i++) {
      // Make sure the bytes are unsigned
      int thisByte = left.byteAt(i) & 0xff;
      int otherByte = right.byteAt(i) & 0xff;
      if (thisByte < otherByte) {
        return -1;
      } else if (thisByte > otherByte) {
        return 1;
      }
      // Byte values are equal, continue with comparison
    }
    return Util.compareIntegers(left.size(), right.size());
  }

  public static StringBuilder repeatSequence(
      CharSequence sequence, int count, CharSequence delimiter) {
    final StringBuilder sb = new StringBuilder();
    if (count != 0) {
      sb.append(sequence);
      for (int i = 1; i < count; i++) {
        sb.append(delimiter);
        sb.append(sequence);
      }
    }
    return sb;
  }

  /**
   * Compares two collections for equality using the provided comparator. The method computes the
   * intersection and invokes `onAdd` for every element that is in `after` but not `before`.
   * `onRemove` is invoked for every element in `before` but missing from `after`.
   *
   * <p>The method creates a copy of both `before` and `after` and runs in O(n log n), where n is
   * the size of the two lists.
   *
   * @param before The elements that exist in the original set.
   * @param after The elements to diff against the original set.
   * @param comparator The comparator to use to define equality between elements.
   * @param onAdd A function to invoke for every element that is part of `after` but not `before`.
   * @param onRemove A function to invoke for every element that is part of `before` but not
   *     `after`.
   */
  public static <T> void diffCollections(
      Collection<T> before,
      Collection<T> after,
      Comparator<T> comparator,
      Consumer<T> onAdd,
      Consumer<T> onRemove) {
    List<T> beforeEntries = new ArrayList<>(before);
    Collections.sort(beforeEntries, comparator);
    List<T> afterEntries = new ArrayList<>(after);
    Collections.sort(afterEntries, comparator);

    diffCollections(beforeEntries.iterator(), afterEntries.iterator(), comparator, onAdd, onRemove);
  }

  /**
   * Compares two sorted sets for equality using their natural ordering. The method computes the
   * intersection and invokes `onAdd` for every element that is in `after` but not `before`.
   * `onRemove` is invoked for every element in `before` but missing from `after`.
   *
   * <p>The method creates a copy of both `before` and `after` and runs in O(n log n), where n is
   * the size of the two lists.
   *
   * @param before The elements that exist in the original set.
   * @param after The elements to diff against the original set.
   * @param onAdd A function to invoke for every element that is part of `after` but not `before`.
   * @param onRemove A function to invoke for every element that is part of `before` but not
   *     `after`.
   */
  public static <T extends Comparable<T>> void diffCollections(
      SortedSet<T> before, SortedSet<T> after, Consumer<T> onAdd, Consumer<T> onRemove) {
    diffCollections(
        before.iterator(),
        after.iterator(),
        before.comparator() != null ? before.comparator() : (l, r) -> l.compareTo(r),
        onAdd,
        onRemove);
  }

  private static <T> void diffCollections(
      Iterator<T> beforeSorted,
      Iterator<T> afterSorted,
      Comparator<? super T> comparator,
      Consumer<T> onAdd,
      Consumer<T> onRemove) {
    @Nullable T beforeValue = advanceIterator(beforeSorted);
    @Nullable T afterValue = advanceIterator(afterSorted);

    // Walk through the two sets at the same time, using the ordering defined by `comparator`.
    while (beforeValue != null || afterValue != null) {
      boolean added = false;
      boolean removed = false;

      if (beforeValue != null && afterValue != null) {
        int cmp = comparator.compare(beforeValue, afterValue);
        if (cmp < 0) {
          // The element was removed if the next element in our ordered walkthrough is only in
          // `beforeSorted`.
          removed = true;
        } else if (cmp > 0) {
          // The element was added if the next element in our ordered walkthrough is only in
          // `afterSorted`.
          added = true;
        }
      } else if (beforeValue != null) {
        removed = true;
      } else {
        added = true;
      }

      if (added) {
        onAdd.accept(afterValue);
        afterValue = advanceIterator(afterSorted);
      } else if (removed) {
        onRemove.accept(beforeValue);
        beforeValue = advanceIterator(beforeSorted);
      } else {
        beforeValue = advanceIterator(beforeSorted);
        afterValue = advanceIterator(afterSorted);
      }
    }
  }

  /** Returns the next element from the iterator or `null` if none available. */
  @Nullable
  private static <T> T advanceIterator(Iterator<T> it) {
    return it.hasNext() ? it.next() : null;
  }

  /** Returns an iterable that iterates over the values in a map. */
  public static <K, V> Iterable<V> values(Iterable<Map.Entry<K, V>> map) {
    return () -> {
      Iterator<Map.Entry<K, V>> iterator = map.iterator();
      return new Iterator<V>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public V next() {
          return iterator.next().getValue();
        }
      };
    };
  }

  /** Returns a map with the first {#code n} elements of {#code data} when sorted by comp. */
  public static <K, V> Map<K, V> firstNEntries(Map<K, V> data, int n, Comparator<V> comp) {
    if (data.size() <= n) {
      return data;
    } else {
      List<Map.Entry<K, V>> sortedValues = new ArrayList<>(data.entrySet());
      Collections.sort(sortedValues, (l, r) -> comp.compare(l.getValue(), r.getValue()));
      Map<K, V> result = new HashMap<>();
      for (int i = 0; i < n; ++i) {
        result.put(sortedValues.get(i).getKey(), sortedValues.get(i).getValue());
      }
      return result;
    }
  }
}
