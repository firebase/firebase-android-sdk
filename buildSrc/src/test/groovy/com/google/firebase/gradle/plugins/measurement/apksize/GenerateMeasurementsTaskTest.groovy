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

package com.google.firebase.gradle.plugins.measurement.apksize

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

import java.nio.file.Files
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Smoke tests for the generate measurements task. */
@RunWith(JUnit4.class)
public class GenerateMeasurementsTaskTest {

    @Rule public final ApkSizeTestProject testProject = new ApkSizeTestProject()

    @Test
    public void generate_withJson() {
        BuildResult result = testProject.build("generate", "-Ppull_request=977")

        if (!result.tasks(TaskOutcome.FAILED).isEmpty()) {
            throw new AssertionError("Smoke test for generate failed", result.getFailure())
        }

	assertTrue(Files.exists(testProject.getApkSizeReportPath()))
    }

    @Test
    public void generate_withTable() {
        BuildResult result = testProject.build("generate")

        if (!result.tasks(TaskOutcome.FAILED).isEmpty()) {
            throw new AssertionError("Smoke test for generate failed", result.getFailure())
        }

	assertFalse(Files.exists(testProject.getApkSizeReportPath()))
        assertTrue("Output missing `APK Sizes`", result.getOutput().contains("APK Sizes"))
    }
}
