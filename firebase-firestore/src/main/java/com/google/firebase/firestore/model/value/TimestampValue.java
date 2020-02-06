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

package com.google.firebase.firestore.model.value;

import com.google.firebase.Timestamp;
import com.google.firestore.v1.Value;

/** A wrapper for Date values in Timestamp. */
public class TimestampValue extends FieldValue {
  TimestampValue(Value value) {
    super(value);
  }

  public static TimestampValue valueOf(Timestamp timestamp) {
    return new TimestampValue(
        Value.newBuilder()
            .setTimestampValue(
                com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(timestamp.getSeconds())
                    .setNanos(timestamp.getNanoseconds()))
            .build());
  }
}
