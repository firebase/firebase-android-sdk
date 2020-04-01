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

package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;

/**
 * A ValueProvider defers the calculation of a Node's value until needed.
 *
 * <p>It's critical for performance that we do not calculate actual values from a SyncTree unless
 * and until the value is needed. Because we expose both a SyncTree and Node version of deferred
 * value resolution, we ned a wrapper class that will let us share code {@see
 * https://github.com/firebase/firebase-js-sdk/issues/2487}.
 */
public interface ValueProvider {
  ValueProvider getImmediateChild(ChildKey childKey);

  Node node();
}
