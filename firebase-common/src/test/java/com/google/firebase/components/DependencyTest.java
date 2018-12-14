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
