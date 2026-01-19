/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin.transform;

import com.google.firebase.perf.plugin.GradleBuildProject;
import com.google.firebase.perf.plugin.GradleBuildResult;
import com.google.firebase.perf.plugin.GradleBuildRunner;
import com.google.firebase.perf.plugin.GradleBuildVariant;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Functional tests for the Firebase Performance Monitoring Gradle plugin's bytecode
 * instrumentation.
 */
public class InstrumentationApiTest {
  @RegisterExtension public GradleBuildProject gradleBuildProject = new GradleBuildProject();
  private final String TEST_AGP_VERSION = "7.2.0";
  private final String TEST_GRADLE_VERSION = "7.3.3";

  private GradleBuildRunner getJavaRunnerBuilder() throws IOException {
    return gradleBuildProject
        .getJavaRunnerBuilder()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withAndroidGradlePluginVersion(TEST_AGP_VERSION);
  }

  private GradleBuildRunner getKotlinRunnerBuilder() throws IOException {
    return gradleBuildProject
        .getKotlinRunnerBuilder()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withAndroidGradlePluginVersion(TEST_AGP_VERSION);
  }

  @Test
  public void
      gradleBuild_whenRunForAllWithoutAnySpecificVariant_runsInstrumentationForDebugAndReleaseVariants()
          throws Exception {

    GradleBuildResult result = getJavaRunnerBuilder().build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void gradleBuild_whenEnabledByProjectProperty_runsInstrumentationForAllVariants()
      throws Exception {

    GradleBuildResult result =
        getJavaRunnerBuilder().setProjectFlagEnabled(true).build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void gradleBuild_whenDisabledByProjectProperty_doesNotRunInstrumentationForAnyVariant()
      throws Exception {

    GradleBuildResult result =
        getJavaRunnerBuilder().setProjectFlagEnabled(false).build(GradleBuildVariant.ALL);

    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void
      gradleBuild_whenEnabledByProjectAndDisabledByExtensionProperty_runsInstrumentationForAllVariants()
          throws Exception {

    GradleBuildResult result =
        getJavaRunnerBuilder()
            .setProjectFlagEnabled(true)
            .setReleaseBuildFlagEnabled(false)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void
      gradleBuild_whenDisabledByProjectAndEnabledByExtensionProperty_doesNotRunInstrumentationForAnyVariant()
          throws Exception {

    GradleBuildResult result =
        getJavaRunnerBuilder()
            .setProjectFlagEnabled(false)
            .setReleaseBuildFlagEnabled(true)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void
      gradleBuild_whenEnabledByExtensionPropertyOnBuildType_runsInstrumentationForReleaseVariant()
          throws Exception {

    GradleBuildResult result =
        getJavaRunnerBuilder().setReleaseBuildFlagEnabled(true).build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void
      gradleBuild_whenDisabledByExtensionPropertyOnBuildType_doesNotRunInstrumentationForReleaseVariant()
          throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder().setReleaseBuildFlagEnabled(false).build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void
      gradleBuild_whenEnabledByExtensionPropertyOnProductFlavor_runsInstrumentationForDemoVariants()
          throws Exception {

    GradleBuildResult result =
        getJavaRunnerBuilder().setDemoFlavorFlagEnabled(true).build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_RELEASE);
  }

  @Test
  public void
      gradleBuild_whenDisabledByExtensionPropertyOnProductFlavor_doesNotRunInstrumentationForDemoVariants()
          throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder().setDemoFlavorFlagEnabled(false).build(GradleBuildVariant.ALL);

    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_DEBUG);
    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_RELEASE);
  }

  @Test
  public void
      gradleBuild_whenEnabledForOneButDisabledForAnotherBuildType_doesNotRunInstrumentationForDisabledVariant()
          throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder()
            .setDebugBuildFlagEnabled(false)
            .setReleaseBuildFlagEnabled(true)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void
      gradleBuild_whenEnabledForBuildTypeButDisabledForProductFlavor_runsInstrumentationForDemoReleaseVariant()
          throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder()
            .setReleaseBuildFlagEnabled(true)
            .setDemoFlavorFlagEnabled(false)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_RELEASE);
  }

  @Test
  public void
      gradleBuild_whenDisabledForBuildTypeButEnabledForProductFlavor_doesNotRunInstrumentationForDemoReleaseVariant()
          throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder()
            .setReleaseBuildFlagEnabled(false)
            .setDemoFlavorFlagEnabled(true)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_DEBUG);
    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_RELEASE);
  }

  @Test
  public void
      assembleWithFlavorsFromSameDimensions_whenDecidingToRunInstrumentation_doesNotInterfere()
          throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder()
            .setDemoFlavorFlagEnabled(false)
            .setFullFlavorFlagEnabled(true)
            .build(GradleBuildVariant.ALL);

    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_DEBUG);
    result.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_RELEASE);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.FULL_DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.FULL_RELEASE);
  }

  /** Refer to {@link GradleBuildVariant} on how priorities are defined. */
  @Test
  public void
      assembleWithMultipleFlavors_whenDecidingToRunInstrumentation_givesPriorityToEarlierFlavors()
          throws Exception {
    GradleBuildResult resultPart1 =
        getJavaRunnerBuilder()
            .setDemoFlavorFlagEnabled(false)
            .setFreeFlavorFlagEnabled(true)
            .build(GradleBuildVariant.ALL);

    resultPart1.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_FREE_DEBUG);
    resultPart1.verifyInstrumentationNotExecutedFor(GradleBuildVariant.DEMO_FREE_RELEASE);

    // Running another build with different set of flavors

    GradleBuildResult resultPart2 =
        getJavaRunnerBuilder()
            .setFullFlavorFlagEnabled(true)
            .setPaidFlavorFlagEnabled(false)
            .build(GradleBuildVariant.ALL);

    resultPart2.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.FULL_PAID_DEBUG);
    resultPart2.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.FULL_PAID_RELEASE);

    // Running another build with different set of flavors

    GradleBuildResult resultPart3 =
        getJavaRunnerBuilder()
            .setDemoFlavorFlagEnabled(true)
            .setFullFlavorFlagEnabled(false)
            .setFreeFlavorFlagEnabled(false)
            .setPaidFlavorFlagEnabled(true)
            .build(GradleBuildVariant.ALL);

    resultPart3.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_FREE_DEBUG);
    resultPart3.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_FREE_RELEASE);
    resultPart3.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_PAID_DEBUG);
    resultPart3.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEMO_PAID_RELEASE);

    resultPart3.verifyInstrumentationNotExecutedFor(GradleBuildVariant.FULL_FREE_DEBUG);
    resultPart3.verifyInstrumentationNotExecutedFor(GradleBuildVariant.FULL_FREE_RELEASE);
    resultPart3.verifyInstrumentationNotExecutedFor(GradleBuildVariant.FULL_PAID_DEBUG);
    resultPart3.verifyInstrumentationNotExecutedFor(GradleBuildVariant.FULL_PAID_RELEASE);
  }

  /** See https://github.com/firebase/firebase-android-sdk/issues/1556 */
  @Test
  public void
      gradleBuild_whenUsingKotlinFileWithBothInlineFunctionAndMultiplatformProject_performInstrumentationationCorrectly()
          throws Exception {
    GradleBuildResult result = getKotlinRunnerBuilder().build(GradleBuildVariant.ALL);

    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.DEBUG);
    result.verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant.RELEASE);
  }

  @Test
  public void gradleBuild_dependsOnTransportLibrary_noInstrumentForDenyList() throws Exception {
    GradleBuildResult result =
        getJavaRunnerBuilder().addTransportDependencies().build(GradleBuildVariant.ALL);

    // TODO: https://github.com/firebase/firebase-android-sdk/pull/2119 has removed proguard,
    // wait for new release of transport library so we can add plain text class name validation.
    // Explaination: Transport library uses HttpURLConnection to communicate with backend, this
    // network request should not be instrumented because it will cause infinite performance
    // event generation. Currently this library `transport-backend-cct` is pro-guarded, workaround
    // is to validate all files under this directory to make sure no file is instrumented.
    result.verifyLibInstrumentedInArtifactTransform(
        /* libJarName= */ "transport-backend-cct",
        /* directoryPathToLocate= */ "com/google/android/datatransport/cct",
        /* isBytecodeInstrumentedWithFireperfSdkApis= */ false);
  }

  // TODO: Verify the behavior with config caching.
}
