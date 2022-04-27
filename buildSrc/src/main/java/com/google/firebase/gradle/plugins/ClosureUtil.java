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

package com.google.firebase.gradle.plugins;

import groovy.lang.Closure;
import java.util.function.Consumer;

public final class ClosureUtil {

  private static final Object FAKE_THIS = new Object();

  private ClosureUtil() {}

  /** Create a groovy closure backed by a lambda. */
  public static <T> Closure<T> closureOf(Consumer<T> action) {
    return new Closure<T>(FAKE_THIS) {
      void doCall(T t) {
        action.accept(t);
      }
    };
  }
}
