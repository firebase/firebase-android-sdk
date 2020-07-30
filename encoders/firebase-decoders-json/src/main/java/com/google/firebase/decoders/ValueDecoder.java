// Copyright 2020 Google LLC
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

package com.google.firebase.decoders;

/**
 * This interface should be implemented by a specific ValueDecoder to provide its decoding logic. A
 * {@code ValueDecoder} writes its decoding logic in {@link ValueDecoderContext}.
 *
 * <p>Use this interface if the object to decode was represented as a single or primitive value.
 */
public interface ValueDecoder<T> extends Decoder<T, ValueDecoderContext> {}
