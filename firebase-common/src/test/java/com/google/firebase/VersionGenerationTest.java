package com.google.firebase;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class VersionGenerationTest {

  @Test
  public void isVersionGenerated_shouldNotBeEmpty() {
    assertThat(BuildConfig.VERSION_NAME).isNotEmpty();
  }
}
