// Copyright 2019 Google LLC
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

package com.google.firebase.components.threading;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.components.Dependency;
import com.google.firebase.inject.Provider;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeadlockTests {
  @Test(timeout = 2000)
  public void concurrentInitialization_withValueComponents_shouldNotDeadlock() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(Runnable::run)
            .addComponent(
                Component.builder(EagerComponent.class)
                    .add(Dependency.requiredProvider(AnotherEagerComponent.class))
                    .factory(
                        c -> {
                          Provider<AnotherEagerComponent> another =
                              c.getProvider(AnotherEagerComponent.class);
                          CountDownLatch latch = new CountDownLatch(1);
                          Executors.newSingleThreadExecutor()
                              .execute(
                                  () -> {
                                    another.get();
                                    latch.countDown();
                                  });
                          try {
                            latch.await();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("Deadlock detected.", e);
                          }
                          return new EagerComponent();
                        })
                    .alwaysEager()
                    .build())
            .addComponent(
                Component.builder(AnotherEagerComponent.class)
                    .add(Dependency.optionalProvider(String.class))
                    .factory(
                        c -> {
                          c.getProvider(String.class);
                          return new AnotherEagerComponent();
                        })
                    .alwaysEager()
                    .build())
            .build();
    runtime.initializeEagerComponents(true);
  }

  @Test(timeout = 2000)
  public void concurrentInitialization_withDeferredComponents_shouldNotDeadlock()
      throws InterruptedException {
    CountDownLatch stringDeferredInjected = new CountDownLatch(1);
    @SuppressWarnings("unchecked")
    Provider<ComponentRegistrar> missingRegistrar = mock(Provider.class);
    ComponentRuntime runtime =
        ComponentRuntime.builder(Runnable::run)
            .addComponent(
                Component.builder(EagerComponent.class)
                    .add(Dependency.deferred(String.class))
                    .factory(
                        c -> {
                          c.getDeferred(String.class)
                              .whenAvailable(
                                  p -> {
                                    p.get();
                                    stringDeferredInjected.countDown();
                                  });
                          return new EagerComponent();
                        })
                    .alwaysEager()
                    .build())
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .build();
    runtime.initializeEagerComponents(true);

    when(missingRegistrar.get())
        .thenReturn(
            () ->
                Collections.singletonList(
                    Component.builder(String.class)
                        .add(Dependency.optionalProvider(Double.class))
                        .factory(
                            c -> {
                              CountDownLatch latch = new CountDownLatch(1);
                              Executors.newSingleThreadExecutor()
                                  .execute(
                                      () -> {
                                        c.getProvider(Double.class);
                                        latch.countDown();
                                      });
                              try {
                                latch.await();
                              } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError("Deadlock detected", e);
                              }
                              return "Hello";
                            })
                        .build()));

    runtime.discoverComponents();
    stringDeferredInjected.await();
  }
}
