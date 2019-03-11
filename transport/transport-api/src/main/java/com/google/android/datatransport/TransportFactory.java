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

package com.google.android.datatransport;

/** Each factory is backed by a single transport backend. */
public interface TransportFactory {
  /**
   * Returns a named transport instance that can be used to send values of type T.
   *
   * @param name name of the transport.
   * @param payloadType The type that is sendable by the returned {@link Transport}.
   * @param payloadTransformer Transformer that converts values of T to byte arrays.
   */
  <T> Transport<T> getTransport(
      String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer);
}
