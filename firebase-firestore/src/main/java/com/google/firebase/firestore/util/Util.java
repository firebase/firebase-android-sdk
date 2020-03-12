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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

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

  @SuppressWarnings("unchecked")
  private static final Comparator COMPARABLE_COMPARATOR =
      new Comparator<Comparable<?>>() {
        @Override
        public int compare(Comparable left, Comparable right) {
          return left.compareTo(right);
        }
      };

  @SuppressWarnings("unchecked")
  public static <T extends Comparable<T>> Comparator<T> comparator() {
    return COMPARABLE_COMPARATOR;
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
  public static void crashMainThread(RuntimeException exception) {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              throw exception;
            });
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
}
