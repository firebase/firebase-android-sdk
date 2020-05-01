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

package com.google.firebase.database;

// Server values

import androidx.annotation.NonNull;
import com.google.firebase.database.core.ServerValues;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Contains placeholder values to use when writing data to the Firebase Database. */
public class ServerValue {

  /**
   * A placeholder value for auto-populating the current timestamp (time since the Unix epoch, in
   * milliseconds) by the Firebase Database servers.
   */
  @NonNull
  public static final Map<String, String> TIMESTAMP =
      createScalarServerValuePlaceholder(ServerValues.NAME_OP_TIMESTAMP);

  /**
   * Returns a placeholder value that can be used to atomically increment the current database value
   * by the provided delta.
   *
   * <p>The delta must be an long or a double value. If the current value is not a number, or if the
   * database value does not yet exist, the transformation will set the database value to the delta
   * value. If either the delta value or the existing value are doubles, both values will be
   * interpreted as doubles. Double arithmetic and representation of double values follow IEEE 754
   * semantics. If there is positive/negative integer overflow, the sum is calculated as a double.
   *
   * @param delta the amount to modify the current value atomically.
   * @return a placeholder value for modifying data atomically server-side.
   */
  @NonNull
  public static final Object increment(long delta) {
    return createParameterizedServerValuePlaceholder(ServerValues.NAME_OP_INCREMENT, delta);
  }

  /**
   * Returns a placeholder value that can be used to atomically increment the current database value
   * by the provided delta.
   *
   * <p>The delta must be an long or a double value. If the current value is not an integer or
   * double, or if the data does not yet exist, the transformation will set the data to the delta
   * value. If either of the delta value or the existing data are doubles, both values will be
   * interpreted as doubles. Double arithmetic and representation of double values follow IEEE 754
   * semantics. If there is positive/negative integer overflow, the sum is calculated as a a double.
   *
   * @param delta the amount to modify the current value atomically.
   * @return a placeholder value for modifying data atomically server-side.
   */
  @NonNull
  public static final Object increment(double delta) {
    return createParameterizedServerValuePlaceholder(ServerValues.NAME_OP_INCREMENT, delta);
  }

  private static Map<String, String> createScalarServerValuePlaceholder(String key) {
    Map<String, String> result = new HashMap<String, String>();
    result.put(ServerValues.NAME_SUBKEY_SERVERVALUE, key);
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, Map<String, Object>> createParameterizedServerValuePlaceholder(
      String name, Object value) {
    Map<String, Object> op = new HashMap<>();
    op.put(name, value);
    Map<String, Map<String, Object>> result = new HashMap<>();
    result.put(ServerValues.NAME_SUBKEY_SERVERVALUE, Collections.unmodifiableMap(op));
    return Collections.unmodifiableMap(result);
  }
}
