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

import com.google.firebase.inject.Provider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LazySetTest {

  @Test
  public void lazySet_shouldBeExternallyImmutable() {
    Set<Integer> integers = setOf(() -> 1, () -> 2).get();
    assertThrows(UnsupportedOperationException.class, () -> integers.add(3));
  }

  @Test
  public void add_whenUnderlyingSetAlreadyInitialized_shouldUpdateUnderlyingSet() {

    LazySet<Integer> lazySet = setOf(() -> 1, () -> 2);

    Set<Integer> integers = lazySet.get();
    assertThat(integers).containsExactly(1, 2);

    lazySet.add(() -> 3);

    assertThat(integers).containsExactly(1, 2, 3);
  }

  @Test
  public void add_whenUnderlyingSetNotInitializedYet_shouldUpdateProviderSet() {

    LazySet<Integer> lazySet = setOf(() -> 1, () -> 2);

    lazySet.add(() -> 3);

    Set<Integer> integers = lazySet.get();
    assertThat(integers).containsExactly(1, 2, 3);
  }

  @Test
  public void get_shouldInitializeEachSetMemberExactlyOnce() {
    AtomicInteger initialized = new AtomicInteger(0);
    LazySet<Integer> lazySet =
        setOf(
            () -> 1,
            () -> {
              initialized.incrementAndGet();
              return 2;
            });
    assertThat(initialized.get()).isEqualTo(0);

    lazySet.add(() -> 3);

    assertThat(initialized.get()).isEqualTo(0);

    Set<Integer> integers = lazySet.get();
    assertThat(integers).containsExactly(1, 2, 3);
    assertThat(initialized.get()).isEqualTo(1);

    lazySet.get();
    assertThat(initialized.get()).isEqualTo(1);
  }

  @Test
  public void add_whenSetNotInitializedYet_shouldNotInitializeSet() {
    AtomicInteger initialized = new AtomicInteger(0);
    LazySet<Integer> lazySet =
        setOf(
            () -> 1,
            () -> {
              initialized.incrementAndGet();
              return 2;
            });
    assertThat(initialized.get()).isEqualTo(0);

    lazySet.add(() -> 3);

    assertThat(initialized.get()).isEqualTo(0);
  }

  @SafeVarargs
  private final <T> LazySet<T> setOf(Provider<T>... ts) {
    return new LazySet<>(new HashSet<>(Arrays.asList(ts)));
  }
}
