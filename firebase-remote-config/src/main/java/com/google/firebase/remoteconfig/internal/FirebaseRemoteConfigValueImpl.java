// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler.FALSE_REGEX;
import static com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler.FRC_BYTE_ARRAY_ENCODING;
import static com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler.TRUE_REGEX;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;

/**
 * Implementation of {@link FirebaseRemoteConfigValue}.
 *
 * @author Miraziz Yusupov
 */
public class FirebaseRemoteConfigValueImpl implements FirebaseRemoteConfigValue {
  private static final String ILLEGAL_ARGUMENT_STRING_FORMAT =
      "[Value: %s] cannot be converted to a %s.";

  private final String value;
  private final int source;

  FirebaseRemoteConfigValueImpl(String value, int source) {
    this.value = value;
    this.source = source;
  }

  @Override
  public long asLong() {
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
      return FirebaseRemoteConfig.DEFAULT_VALUE_FOR_LONG;
    }

    String valueAsString = asTrimmedString();
    try {
      return Long.valueOf(valueAsString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(ILLEGAL_ARGUMENT_STRING_FORMAT, valueAsString, "long"), e);
    }
  }

  @Override
  public double asDouble() {
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
      return FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE;
    }

    String valueAsString = asTrimmedString();
    try {
      return Double.valueOf(valueAsString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(ILLEGAL_ARGUMENT_STRING_FORMAT, valueAsString, "double"), e);
    }
  }

  @Override
  public String asString() {
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
      return FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING;
    }

    throwIfNullValue();
    return value;
  }

  @Override
  public byte[] asByteArray() {
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
      return FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BYTE_ARRAY;
    }
    return value.getBytes(FRC_BYTE_ARRAY_ENCODING);
  }

  @Override
  public boolean asBoolean() throws IllegalArgumentException {
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
      return FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BOOLEAN;
    }

    String valueAsString = asTrimmedString();
    if (TRUE_REGEX.matcher(valueAsString).matches()) {
      return true;
    } else if (FALSE_REGEX.matcher(valueAsString).matches()) {
      return false;
    }
    throw new IllegalArgumentException(
        String.format(ILLEGAL_ARGUMENT_STRING_FORMAT, valueAsString, "boolean"));
  }

  @Override
  public int getSource() {
    return source;
  }

  private void throwIfNullValue() {
    if (value == null) {
      throw new IllegalArgumentException(
          "Value is null, and cannot be converted to the desired type.");
    }
  }

  /** Returns a trimmed version of {@link #asString}. */
  private String asTrimmedString() {
    return asString().trim();
  }
}
