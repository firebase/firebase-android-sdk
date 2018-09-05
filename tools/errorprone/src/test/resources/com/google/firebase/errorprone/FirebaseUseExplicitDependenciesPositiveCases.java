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

package com.google.firebase.errorprone;

import com.google.firebase.FirebaseApp;

public class FirebaseUseExplicitDependenciesPositiveCases {
  private FirebaseApp app = new FirebaseApp();

  public void useOfAppGetInInstanceMethod(FirebaseApp app) {
    // BUG: Diagnostic contains: FirebaseApp#get(Class) is discouraged
    app.get(String.class);
  }

  public void useOfAppGetInStaticMethod() {
    // BUG: Diagnostic contains: FirebaseApp#get(Class) is discouraged
    app.get(String.class);
  }

  /** method returns void so it is not allowed. */
  public static void getInstance(FirebaseApp app, String foo) {
    // BUG: Diagnostic contains: FirebaseApp#get(Class) is discouraged
    app.get(String.class);
  }

  /** method returns int so it is not allowed. */
  public static int getInstance(String foo) {
    // BUG: Diagnostic contains: FirebaseApp#get(Class) is discouraged
    new FirebaseApp().get(String.class);
    return 0;
  }

  /** method returns String so it is not allowed. */
  public static String getInstance(FirebaseApp app) {
    // BUG: Diagnostic contains: FirebaseApp#get(Class) is discouraged
    app.get(String.class);
    return "";
  }
}
