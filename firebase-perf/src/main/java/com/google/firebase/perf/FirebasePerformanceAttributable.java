// Copyright 2020 Google LLC
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

package com.google.firebase.perf;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.util.Constants;
import java.util.Map;

/**
 * Attribute functions needed for Traces and HttpMetrics
 *
 * @hide
 */
public interface FirebasePerformanceAttributable {

  // Redefining some constants for javadoc as Constants class is hidden
  /** Maximum allowed number of attributes allowed in a trace. */
  int MAX_TRACE_CUSTOM_ATTRIBUTES = Constants.MAX_TRACE_CUSTOM_ATTRIBUTES;
  /** Maximum allowed length of the Key of the {@link Trace} attribute */
  int MAX_ATTRIBUTE_KEY_LENGTH = Constants.MAX_ATTRIBUTE_KEY_LENGTH;
  /** Maximum allowed length of the Value of the {@link Trace} attribute */
  int MAX_ATTRIBUTE_VALUE_LENGTH = Constants.MAX_ATTRIBUTE_VALUE_LENGTH;
  /** Maximum allowed length of the name of the {@link Trace} */
  int MAX_TRACE_NAME_LENGTH = Constants.MAX_TRACE_ID_LENGTH;

  /**
   * Sets a String value for the specified attribute in the object's list of attributes. The maximum
   * number of attributes that can be added are {@value #MAX_TRACE_CUSTOM_ATTRIBUTES}.
   *
   * @param attribute name of the attribute. Leading and trailing white spaces if any, will be
   *     removed from the name. The name must start with letter, must only contain alphanumeric
   *     characters and underscore and must not start with "firebase_", "google_" and "ga_. The max
   *     length is limited to {@value #MAX_ATTRIBUTE_KEY_LENGTH}
   * @param value value of the attribute. The max length is limited to {@value
   *     #MAX_ATTRIBUTE_VALUE_LENGTH}
   */
  void putAttribute(@NonNull String attribute, @NonNull String value);

  /**
   * Returns the value of an attribute.
   *
   * @param attribute name of the attribute to fetch the value for
   * @return The value of the attribute if it exists or null otherwise.
   */
  @Nullable
  String getAttribute(@NonNull String attribute);

  /**
   * Removes the attribute from the list of attributes.
   *
   * @param attribute name of the attribute to be removed from the global pool.
   */
  void removeAttribute(@NonNull String attribute);

  /**
   * Returns the map of all the attributes currently added
   *
   * @return map of attributes and its values currently added
   */
  @NonNull
  Map<String, String> getAttributes();
}
