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

package com.google.firebase.storage.network;

import java.io.IOException;

/**
 * Interface used to inject exceptions during test runs. Implementing classes need to override
 * `injectInputStream` and `injectOutputStream` which can then throw IOExceptions based on the range
 * provided via the arguments.
 */
public interface ConnectionInjector {
  void injectInputStream(int start, int end) throws IOException;

  void injectOutputStream(int start, int end) throws IOException;
}
