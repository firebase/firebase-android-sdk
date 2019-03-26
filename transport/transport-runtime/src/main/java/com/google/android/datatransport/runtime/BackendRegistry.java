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

package com.google.android.datatransport.runtime;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Container for registered {@link TransportBackend}s. */
@Singleton
public class BackendRegistry {
  private final Map<String, TransportBackend> backends = new HashMap<>();

  @Inject
  public BackendRegistry() {}

  void register(String name, TransportBackend backend) {
    synchronized (backends) {
      backends.put(name, backend);
    }
  }

  public TransportBackend get(String name) {
    synchronized (backends) {
      return backends.get(name);
    }
  }
}
