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

package com.google.firebase.components;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeferredTest {
  private static final String DEFERRED_VALUE = "hello";

  @Test
  public void test() {
    Deferred<String> deferred = new Deferred<>();
    assertThat(deferred.get()).isNull();

    deferred.set(() -> DEFERRED_VALUE);

    assertThat(deferred.get()).isEqualTo(DEFERRED_VALUE);
  }
}
