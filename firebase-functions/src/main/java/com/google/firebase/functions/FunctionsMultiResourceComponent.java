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

package com.google.firebase.functions;

import android.content.Context;
import androidx.annotation.GuardedBy;
import com.google.firebase.FirebaseApp;
import java.util.HashMap;
import java.util.Map;

/** Multi-resource container for Functions. */
class FunctionsMultiResourceComponent {
  /**
   * A static map from instance key to FirebaseFunctions instances. Instance keys region names.
   *
   * <p>To ensure thread safety it should only be accessed when it is being synchronized on.
   */
  @GuardedBy("this")
  private final Map<String, FirebaseFunctions> instances = new HashMap<>();

  private final Context applicationContext;
  private final ContextProvider contextProvider;
  private final FirebaseApp app;

  FunctionsMultiResourceComponent(
      Context applicationContext, ContextProvider contextProvider, FirebaseApp app) {
    this.applicationContext = applicationContext;
    this.contextProvider = contextProvider;
    this.app = app;
  }

  synchronized FirebaseFunctions get(String regionOrCustomDomain) {
    FirebaseFunctions functions = instances.get(regionOrCustomDomain);
    String projectId = app.getOptions().getProjectId();

    if (functions == null) {
      functions =
          new FirebaseFunctions(
              app, applicationContext, projectId, regionOrCustomDomain, contextProvider);
      instances.put(regionOrCustomDomain, functions);
    }
    return functions;
  }
}
