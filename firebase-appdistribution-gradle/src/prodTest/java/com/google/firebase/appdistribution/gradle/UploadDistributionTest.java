/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.appdistribution.gradle;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.gradle.testkit.runner.BuildResult;
import org.junit.Rule;
import org.junit.Test;

/**
 * Production tests for for UploadDistributionTask. Uses BeePlus to actually hit
 * production. The goal of this test suite is to verify that common successful
 * use cases work end-to-end, as a replacement for manual testing. For more
 * nuanced testing, i.e. APK/AAB parsing edge cases, rely on the integration
 * tests.
 */
public final class UploadDistributionTest {
  @Rule public final BeePlusGradleProject gradleProject = new BeePlusGradleProject();

  @Test
  public void testUploadDistribution_uploadsApkAndDistributes() throws IOException {
    gradleProject.writeBuildFile(
        BuildFileUploadOptions.builder()
            .releaseNotes("Uploading APK from gradle prod test")
            .testers("mallardcrash@gmail.com,mallardcrash+1@gmail.com")
            .groups("mallard-group")
            .build());

    BuildResult result = gradleProject.runUploadApk();

    assertEquals(SUCCESS, result.task(":appDistributionUploadDebug").getOutcome());
    assertThat(result.getOutput(), containsString("Using APK"));
    assertThat(result.getOutput(), containsString("build/outputs/apk/debug/app-debug.apk"));
    assertThat(result.getOutput(), containsString("Uploaded new release"));
    assertThat(result.getOutput(), containsString("Added release notes successfully 200"));
    assertThat(result.getOutput(), containsString("Added testers/groups successfully 200"));
    assertThat(
        result.getOutput(), containsString("App Distribution upload finished successfully!"));
  }

  @Test
  public void testUploadDistribution_uploadsAabAndDistributes() throws IOException {
    gradleProject.writeBuildFile(
        BuildFileUploadOptions.builder()
            .artifactType("AAB")
            .releaseNotes("Uploading AAB from gradle prod test")
            .testers("mallardcrash@gmail.com,mallardcrash+1@gmail.com")
            .groups("mallard-group")
            .build());

    BuildResult result = gradleProject.runUploadAab();

    assertEquals(SUCCESS, result.task(":appDistributionUploadDebug").getOutcome());
    assertThat(result.getOutput(), containsString("Using AAB"));
    assertThat(result.getOutput(), containsString("build/outputs/bundle/debug/app-debug.aab"));
    assertThat(result.getOutput(), containsString("Uploaded new release"));
    assertThat(result.getOutput(), containsString("Added release notes successfully 200"));
    assertThat(result.getOutput(), containsString("Added testers/groups successfully 200"));
    assertThat(
        result.getOutput(), containsString("App Distribution upload finished successfully!"));
  }
}
