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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependencyTest {
  @Test
  public void optional_shouldHaveExpectedInvariants() {
    Dependency dependency = Dependency.optional(String.class);

    assertThat(dependency.isRequired()).isFalse();
    assertThat(dependency.isSet()).isFalse();
    assertThat(dependency.isDirectInjection()).isTrue();
    assertThat(dependency.getInterface()).isEqualTo(String.class);
  }

  @Test
  public void required_shouldHaveExpectedInvariants() {
    Dependency dependency = Dependency.required(String.class);

    assertThat(dependency.isRequired()).isTrue();
    assertThat(dependency.isSet()).isFalse();
    assertThat(dependency.isDirectInjection()).isTrue();
    assertThat(dependency.getInterface()).isEqualTo(String.class);
  }

  @Test
  public void setOf_shouldHaveExpectedInvariants() {
    Dependency dependency = Dependency.setOf(String.class);

    assertThat(dependency.isRequired()).isFalse();
    assertThat(dependency.isSet()).isTrue();
    assertThat(dependency.isDirectInjection()).isTrue();
    assertThat(dependency.getInterface()).isEqualTo(String.class);
  }

  @Test
  public void optionalProvider_shouldHaveExpectedInvariants() {
    Dependency dependency = Dependency.optionalProvider(String.class);

    assertThat(dependency.isRequired()).isFalse();
    assertThat(dependency.isSet()).isFalse();
    assertThat(dependency.isDirectInjection()).isFalse();
    assertThat(dependency.getInterface()).isEqualTo(String.class);
  }

  @Test
  public void requiredProvider_shouldHaveExpectedInvariants() {
    Dependency dependency = Dependency.requiredProvider(String.class);

    assertThat(dependency.isRequired()).isTrue();
    assertThat(dependency.isSet()).isFalse();
    assertThat(dependency.isDirectInjection()).isFalse();
    assertThat(dependency.getInterface()).isEqualTo(String.class);
  }

  @Test
  public void setOfProvider_shouldHaveExpectedInvariants() {
    Dependency dependency = Dependency.setOfProvider(String.class);

    assertThat(dependency.isRequired()).isFalse();
    assertThat(dependency.isSet()).isTrue();
    assertThat(dependency.isDirectInjection()).isFalse();
    assertThat(dependency.getInterface()).isEqualTo(String.class);
  }
}
