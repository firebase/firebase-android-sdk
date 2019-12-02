// Copyright 2019 Google LLC
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

package com.google.firebase.encoders;

/**
 * An {@code ObjectEncoder} takes objects and encodes them using {@link ObjectEncoderContext}.
 *
 * <p>Use this interface if the object to encode can be represented as a map of field names to
 * values. If the object you want to encode should be represented by a single value, implement
 * {@link ValueEncoder}.
 */
public interface ObjectEncoder<T> extends Encoder<T, ObjectEncoderContext> {}
