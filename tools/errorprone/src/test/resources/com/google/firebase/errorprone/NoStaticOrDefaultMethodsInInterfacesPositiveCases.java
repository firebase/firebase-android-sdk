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

public class NoStaticOrDefaultMethodsInInterfacesPositiveCases {

  public interface InterfaceWithDefault {
    // BUG: Diagnostic contains: Avoid static/default
    default void method1() {
      return;
    }
  }

  public interface InterfaceWithStatic {
    // BUG: Diagnostic contains: Avoid static/default
    static void method1() {
      return;
    }
  }
}
