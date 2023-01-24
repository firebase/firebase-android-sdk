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

package com.google.firebase.components;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class TestComponentTwo {
  private final FirebaseApp app;
  private final FirebaseOptions options;
  private final TestComponentOne one;

  public TestComponentTwo(FirebaseApp app, FirebaseOptions options, TestComponentOne one) {
    this.app = app;
    this.options = options;
    this.one = one;
  }

  public FirebaseApp getApp() {
    return app;
  }

  public FirebaseOptions getOptions() {
    return options;
  }

  public TestComponentOne getOne() {
    return one;
  }
}
