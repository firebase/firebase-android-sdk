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
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

public class FirebaseUseExplicitDependenciesNegativeCases {

  /** Valid use with one app parameter. */
  public static FirebaseUseExplicitDependenciesNegativeCases getInstance(FirebaseApp app) {
    app.get(String.class);
    return null;
  }

  /** Valid use with multiple parameters. */
  public static FirebaseUseExplicitDependenciesNegativeCases getInstance(
      FirebaseApp app, String foo) {
    app.get(String.class);
    return null;
  }

  /** Valid use with no parameters. */
  public static FirebaseUseExplicitDependenciesNegativeCases getInstance() {
    new FirebaseApp().get(String.class);
    return null;
  }

  /** Valid private use with multiple parameters. */
  private static FirebaseUseExplicitDependenciesNegativeCases getInstance(
      FirebaseApp app, Integer i) {
    app.get(String.class);
    return null;
  }

  /** Use allowed in tests. */
  @RunWith(JUnit4.class)
  private static class MyTest {
    public void test() {
      new FirebaseApp().get(String.class);
    }
  }

  public static class SuperType {}

  public static class SubType extends SuperType {
    public static SuperType getInstance(FirebaseApp app) {
      app.get(String.class);
      return null;
    }
  }

  public interface Iface {}

  public static class IfaceImpl implements Iface {
    public static Iface getInstance(FirebaseApp app) {
      app.get(String.class);
      return null;
    }
  }
}
