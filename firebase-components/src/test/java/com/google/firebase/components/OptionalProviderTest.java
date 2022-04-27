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
import static org.junit.Assert.assertThrows;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OptionalProviderTest {
  private static final String DEFERRED_VALUE = "hello";

  @Test
  public void set_correctlyUpdatesTheValue() {
    OptionalProvider<String> optional = OptionalProvider.empty();
    assertThat(optional.get()).isNull();

    optional.set(() -> DEFERRED_VALUE);

    assertThat(optional.get()).isEqualTo(DEFERRED_VALUE);
  }

  @Test
  public void set_correctlyCallsHandlers() {
    OptionalProvider<String> deferred = OptionalProvider.empty();
    AtomicReference<String> value1 = new AtomicReference<>();
    AtomicReference<String> value2 = new AtomicReference<>();
    String expected = "expected";
    deferred.whenAvailable(p -> value1.set(p.get()));
    deferred.whenAvailable(p -> value2.set(p.get()));
    assertThat(value1.get()).isNull();
    assertThat(value2.get()).isNull();

    deferred.set(() -> expected);
    assertThat(value1.get()).isEqualTo(expected);
    assertThat(value2.get()).isEqualTo(expected);
  }

  @Test
  public void foo() {
    String expected = "expected";
    OptionalProvider<String> deferred = OptionalProvider.of(() -> expected);
    AtomicReference<String> value1 = new AtomicReference<>();

    deferred.whenAvailable(p -> value1.set(p.get()));
    assertThat(value1.get()).isEqualTo(expected);
  }

  @Test
  public void bar() {
    String expected = "expected";
    OptionalProvider<String> deferred = OptionalProvider.empty();
    deferred.set(() -> expected);
    AtomicReference<String> value1 = new AtomicReference<>();

    deferred.whenAvailable(p -> value1.set(p.get()));
    assertThat(value1.get()).isEqualTo(expected);
  }

  @Test
  public void set_whenCalledMoreThanOnce_shouldThrow() {
    OptionalProvider<String> deferred = OptionalProvider.empty();
    String expected = "expected";
    deferred.set(() -> expected);
    assertThrows(IllegalStateException.class, () -> deferred.set(() -> expected));
  }
}
