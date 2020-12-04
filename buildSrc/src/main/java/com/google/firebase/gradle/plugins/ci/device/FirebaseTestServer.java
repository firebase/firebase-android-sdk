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

package com.google.firebase.gradle.plugins.ci.device;

import com.android.builder.testing.api.TestServer;
import com.google.common.collect.ImmutableList;
import com.google.firebase.gradle.plugins.ci.Environment;
import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import org.gradle.api.Project;

public class FirebaseTestServer extends TestServer {

  private final Project project;
  private final FirebaseTestLabExtension extension;
  private final Random random;

  public FirebaseTestServer(Project project, FirebaseTestLabExtension extension) {
    this.project = project;
    this.extension = extension;
    this.random = new Random(System.currentTimeMillis());
  }

  @Override
  public String getName() {
    return "firebase-test-lab";
  }

  @Override
  public void uploadApks(String variantName, File testApk, File testedApk) {
    // test lab requires an "app" apk, so we give an empty apk to it.
    String testedApkPath =
        testedApk != null
            ? testedApk.toString()
            : project.getRootDir() + "/buildSrc/resources/dummy.apk";
    project
        .getLogger()
        .lifecycle("Uploading for {}: testApk={}, testedApk={}", variantName, testApk, testedApk);

    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add(
        "gcloud",
        "firebase",
        "test",
        "android",
        "run",
        "--type=instrumentation",
        "--app=" + testedApkPath,
        "--test=" + testApk,
        "--use-orchestrator",
        "--no-auto-google-login",
        "--no-record-video",
        "--no-performance-metrics",
        "-q",
        "--results-history-name=" + project.getPath());
    args.addAll(
        extension.getDevices().stream()
            .flatMap(device -> ImmutableList.of("--device", device).stream())
            .collect(Collectors.toList()));

    Optional.ofNullable(System.getenv("FTL_RESULTS_BUCKET"))
        .map(Environment::expand)
        .ifPresent(bucket -> args.add("--results-bucket", bucket));

    Optional.ofNullable(System.getenv("FTL_RESULTS_DIR"))
        .map(Environment::expand)
        .ifPresent(
            dir ->
                args.add(
                    "--results-dir",
                    Paths.get(dir, project.getPath() + "_" + random.nextLong()).toString()));

    project.exec(
        spec -> {
          spec.commandLine(args.build());
        });
  }

  @Override
  public boolean isConfigured() {
    return extension.getEnabled();
  }
}
