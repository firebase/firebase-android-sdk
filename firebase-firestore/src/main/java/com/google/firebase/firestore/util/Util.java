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

import static java.lang.Character.isSurrogate;

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

  /** Compare strings in UTF-8 encoded byte order */
  public static int compareUtf8Strings(String left, String right) {
    // noinspection StringEquality
    if (left == right) {
      return 0;
    }

    // Find the first differing character (a.k.a. "UTF-16 code unit") in the two strings and,
    // if found, use that character to determine the relative ordering of the two strings as a
    // whole. Comparing UTF-16 strings in UTF-8 byte order can be done simply and efficiently by
    // comparing the UTF-16 code units (chars). This serendipitously works because of the way UTF-8
    // and UTF-16 happen to represent Unicode code points.
    //
    // After finding the first pair of differing characters, there are two cases:
    //
    // Case 1: Both characters are non-surrogates (code points less than or equal to 0xFFFF) or
    // both are surrogates from a surrogate pair (that collectively represent code points greater
    // than 0xFFFF). In this case their numeric order as UTF-16 code units is the same as the
    // lexicographical order of their corresponding UTF-8 byte sequences. A direct comparison is
    // sufficient.
    //
    // Case 2: One character is a surrogate and the other is not. In this case the surrogate-
    // containing string is always ordered after the non-surrogate. This is because surrogates are
    // used to represent code points greater than 0xFFFF which have 4-byte UTF-8 representations
    // and are lexicographically greater than the 1, 2, or 3-byte representations of code points
    // less than or equal to 0xFFFF.
    //
    // An example of why Case 2 is required is comparing the following two Unicode code points:
    //
    // |-----------------------|------------|---------------------|-----------------|
    // | Name                  | Code Point | UTF-8 Encoding      | UTF-16 Encoding |
    // |-----------------------|------------|---------------------|-----------------|
    // | Replacement Character | U+FFFD     | 0xEF 0xBF 0xBD      | 0xFFFD          |
    // | Grinning Face         | U+1F600    | 0xF0 0x9F 0x98 0x80 | 0xD83D 0xDE00   |
    // |-----------------------|------------|---------------------|-----------------|
    //
    // A lexicographical comparison of the UTF-8 encodings of these code points would order
    // "Replacement Character" _before_ "Grinning Face" because 0xEF is less than 0xF0. However, a
    // direct comparison of the UTF-16 code units, as would be done in case 1, would erroneously
    // produce the _opposite_ ordering, because 0xFFFD is _greater than_ 0xD83D. As it turns out,
    // this relative ordering holds for all comparisons of UTF-16 code points requiring a surrogate
    // pair with those that do not.
    final int length = Math.min(left.length(), right.length());
    for (int i = 0; i < length; i++) {
      final char leftChar = left.charAt(i);
      final char rightChar = right.charAt(i);
      if (leftChar != rightChar) {
        return (isSurrogate(leftChar) == isSurrogate(rightChar))
            ? Character.compare(leftChar, rightChar)
            : isSurrogate(leftChar) ? 1 : -1;
      }
    }

    // Use the lengths of the strings to determine the overall comparison result since either the
    // strings were equal or one is a prefix of the other.
    return Integer.compare(left.length(), right.length());
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
    return Integer.compare(left.length, right.length);
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
    return Integer.compare(left.size(), right.size());
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
