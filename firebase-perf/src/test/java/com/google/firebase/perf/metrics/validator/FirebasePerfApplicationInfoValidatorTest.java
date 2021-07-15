// Copyright 2021 Google LLC
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

package com.google.firebase.perf.metrics.validator;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.perf.v1.AndroidApplicationInfo;
import com.google.firebase.perf.v1.ApplicationInfo;
import com.google.firebase.perf.v1.ApplicationProcessState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link
 * com.google.firebase.perf.metrics.validator.FirebasePerfApplicationInfoValidator}.
 */
@RunWith(RobolectricTestRunner.class)
public class FirebasePerfApplicationInfoValidatorTest {
  @Test
  public void testAbsenceOfRequiredFieldsFailsValidation() {
    ApplicationInfo validApplicationInfo = createApplicationInfoWithAllRequiredFieldsPresent();

    assertThat(new FirebasePerfApplicationInfoValidator(validApplicationInfo).isValidPerfMetric())
        .isTrue();

    assertThat(
            new FirebasePerfApplicationInfoValidator(
                    validApplicationInfo.toBuilder().clearGoogleAppId().build())
                .isValidPerfMetric())
        .isFalse();

    assertThat(
            new FirebasePerfApplicationInfoValidator(
                    validApplicationInfo.toBuilder().clearAppInstanceId().build())
                .isValidPerfMetric())
        .isFalse();

    assertThat(
            new FirebasePerfApplicationInfoValidator(
                    validApplicationInfo.toBuilder().clearApplicationProcessState().build())
                .isValidPerfMetric())
        .isFalse();
  }

  @Test
  public void testAbsenceOfRequiredFieldsOnAndroidApplicationInfoFailsValidation() {
    ApplicationInfo validApplicationInfo = createApplicationInfoWithAllRequiredFieldsPresent();

    // All required fields present.
    assertThat(
            new FirebasePerfApplicationInfoValidator(
                    validApplicationInfo.toBuilder()
                        .setAndroidAppInfo(
                            AndroidApplicationInfo.newBuilder()
                                .setPackageName("validPackageName")
                                .setSdkVersion("1.3"))
                        .build())
                .isValidPerfMetric())
        .isTrue();

    // Package name missing.
    assertThat(
            new FirebasePerfApplicationInfoValidator(
                    validApplicationInfo.toBuilder()
                        .setAndroidAppInfo(AndroidApplicationInfo.newBuilder().setSdkVersion("1.3"))
                        .build())
                .isValidPerfMetric())
        .isFalse();

    // SDK Version missing.
    assertThat(
            new FirebasePerfApplicationInfoValidator(
                    validApplicationInfo.toBuilder()
                        .setAndroidAppInfo(
                            AndroidApplicationInfo.newBuilder().setPackageName("validPackageName"))
                        .build())
                .isValidPerfMetric())
        .isFalse();
  }

  private ApplicationInfo createApplicationInfoWithAllRequiredFieldsPresent() {
    return ApplicationInfo.newBuilder()
        .setGoogleAppId("validAppId")
        .setAppInstanceId("validAppInstanceId")
        .setApplicationProcessState(ApplicationProcessState.FOREGROUND)
        .build();
  }
}
