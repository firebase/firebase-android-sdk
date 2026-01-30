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

package com.google.firebase.perf.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

import com.android.SdkConstants;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.BuildResult;

/**
 * Wrapper around {@link BuildResult} that is customized to verify bytecode instrumentation
 * performed by Firebase Performance Gradle Plugin.
 */
public final class GradleBuildResult {

  /**
   * The directory for the artifact transform outputs in the gradle home directory.
   */
  private static final String GRADLE_ARTIFACT_TRANSFORM_CACHE_DIR = "caches/transforms-3";

  private final BuildResult result;

  private final File gradleHomeDir;

  private final String testAppClassClasspath;
  private final String pluginBuildClasspath;
  private final String libClasspathToLocate;
  private final String libJarName;
  private final String fireperfSdkInstrumentedDirectorypath;

  /**
   * Creates a new object of the {@link GradleBuildResult} for the specific {@code result}.
   *
   * @param result The {@link BuildResult} object of the {@link GradleBuildRunner} execution.
   * @param gradleHomeDir The temporary directory for gradle home.
   * @param testAppClassClasspath The classpath for one of the classes in the Test App against which
   *     the build is executed.
   * @param pluginBuildClasspath Path for the FirePerf Plugin build outputs.
   * @param libClasspathToLocate Path for one of the classes in the used 3rd party networking
   *     library that we would like to locate as a verification of the transformation performed by
   *     the FirePerf Gradle Plugin.
   * @param libJarName Name of the used 3rd party networking library jar that we would like to locate
   *      as a verification of the transformation performed by the FirePerf Gradle Plugin.
   * @param fireperfSdkInstrumentedDirectorypath Directory path which contains the implementation
   *     for the bytecode instrumented methods by the FirePerf SDK.
   */
  GradleBuildResult(
      BuildResult result,
      File gradleHomeDir,
      String testAppClassClasspath,
      String pluginBuildClasspath,
      String libClasspathToLocate,
      String libJarName,
      String fireperfSdkInstrumentedDirectorypath) {

    this.result = result;
    this.gradleHomeDir = gradleHomeDir;
    this.testAppClassClasspath = testAppClassClasspath;
    this.pluginBuildClasspath = pluginBuildClasspath;
    this.libClasspathToLocate = libClasspathToLocate;
    this.libJarName = libJarName;
    this.fireperfSdkInstrumentedDirectorypath = fireperfSdkInstrumentedDirectorypath;
  }

  public void verifyInstrumentationExecutedAndHasOutputsFor(GradleBuildVariant variant)
      throws IOException {
    verifyInstrumentationUsingAsmTransformApi(variant, /* executed= */ true);
    verifyInstrumentationResultsFor(variant);
  }

  public void verifyInstrumentationNotExecutedFor(GradleBuildVariant variant) throws IOException {
    verifyInstrumentationUsingAsmTransformApi(variant, /* executed= */ false);
  }

  public void verifyInstrumentationExecutedFor(GradleBuildVariant variant) throws IOException {
    verifyInstrumentationUsingAsmTransformApi(variant, /* executed= */ true);
  }

  public void verifyInstrumentationResultsFor(GradleBuildVariant variant) throws IOException {
    // TODO: These checks fail starting AGP 8.1. Explore better checks.
    verifyFileExistInOutputsFor(
        variant,
        testAppClassClasspath,
        /* isJarOutputExpected= */ false,
        /* isBytecodeInstrumentedWithFireperfSdkApis= */ true);

    verifyLibInstrumentedInArtifactTransform(
        libJarName, libClasspathToLocate, /* isBytecodeInstrumentedWithFireperfSdkApis= */ true);
  }

  private void verifyInstrumentationUsingAsmTransformApi(
      GradleBuildVariant variant, boolean executed) throws IOException {
    if (executed) {
      assertThat(
              result.task(
                  /* taskPath= */ String.format(
                      ":transform%sClassesWithAsm", variant.getBuildVariant())))
          .isNotNull();
      assertThat(
              result
                  .task(
                      /* taskPath= */ String.format(
                          ":transform%sClassesWithAsm", variant.getBuildVariant()))
                  .getOutcome())
          .isEqualTo(SUCCESS);
    } else {
      assertThat(
              result.task(
                  /* taskPath= */ String.format(
                      ":transform%sClassesWithAsm", variant.getBuildVariant())))
          .isNull();
    }
  }

  public void verifyLibInstrumentedInArtifactTransform(
      String libJarName,
      String libClasspathToLocate,
      boolean isBytecodeInstrumentedWithFireperfSdkApis)
      throws IOException {
    Path artifactTransformsOutputDir =
        Paths.get(String.format("%s/%s", gradleHomeDir, GRADLE_ARTIFACT_TRANSFORM_CACHE_DIR));
    try (Stream<Path> paths = Files.walk(artifactTransformsOutputDir)) {
      List<Path> jarFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(
                  path ->
                      path.getFileName().toString().contains(libJarName)
                          && path.toString().endsWith(SdkConstants.DOT_JAR))
              .collect(Collectors.toList());

      boolean instrumentedJarFound = false;
      for (Path jarFile : jarFiles) {
        Optional<String> repackagedClass =
            getRepackagedFileInJarFor(jarFile.toFile(), libClasspathToLocate);

        instrumentedJarFound |=
            isClassBytecodeInstrumentedWithFireperfSdkApis(repackagedClass.get());
      }

      if (isBytecodeInstrumentedWithFireperfSdkApis) {
        assertThat(instrumentedJarFound).isTrue();
      } else {
        assertThat(instrumentedJarFound).isFalse();
      }
    }
  }

  public void verifyConfigCacheIsNotAvailable(GradleBuildVariant variant) throws IOException {
    assertThat(result.getOutput().contains("no configuration cache is available")).isTrue();
  }

  public void verifyConfigCacheIsAvailable(GradleBuildVariant variant) throws IOException {
    assertThat(result.getOutput().contains("Reusing configuration cache")).isTrue();
  }

  public void verifyConfigCacheIsOutdated(GradleBuildVariant variant) throws IOException {
    assertThat(result.getOutput().contains("configuration cache cannot be reused")).isTrue();
  }

  /**
   * Validates if the Input (represented by the {@code filePathToLocate}) are correctly repackaged
   * to their destination output for the {@code variant}.
   *
   * @param variant The variant for which the {@code filePathToLocate} has to be searched in its
   *     outputs.
   * @param filePathToLocate The file class path that needs to be located for repackaging.
   * @param isJarOutputExpected {@code true} if the {@code filePathToLocate} has to be searched in a
   *     Jar Output. Note that all library dependencies for the project ends up as a Jar Output.
   * @param isBytecodeInstrumentedWithFireperfSdkApis {@code true} if the output class file is
   *     bytecode instrumented and expected to be decorated with the Fireperf SDK APIs. This will
   *     happen only if the class file either uses Fireperf SDK APIs or makes a network call with
   *     one of the networking library that the 'perf-plugin' supports for instrumentation (see
   *     https://firebase.google.com/docs/perf-mon/network-traces?platform=android).
   */
  public void verifyFileExistInOutputsFor(
      GradleBuildVariant variant,
      String filePathToLocate,
      boolean isJarOutputExpected,
      boolean isBytecodeInstrumentedWithFireperfSdkApis)
      throws IOException {

    Optional<String> repackagedFile =
        isJarOutputExpected
            ? getRepackagedFileInJarFor(variant, filePathToLocate)
            : getRepackagedFileInDirectoryFor(variant, filePathToLocate);
    assertThat(repackagedFile.isPresent()).isTrue();

    if (isBytecodeInstrumentedWithFireperfSdkApis) {
      assertThat(isClassBytecodeInstrumentedWithFireperfSdkApis(repackagedFile.get())).isTrue();
    } else {
      assertThat(isClassBytecodeInstrumentedWithFireperfSdkApis(repackagedFile.get())).isFalse();
    }
  }

  /**
   * Searches the {@code filePathToLocate} under {@code locationToSearch} and returns the {@link
   * Optional<String>} for the repackaged/output class file.
   *
   * @param variant The variant for which the {@code regexFilePathToLocate} has to be searched in
   *     its outputs.
   * @param regexFilePathToLocate The regex for the class path that needs to be located for
   *     repackaging.
   */
  private Optional<String> getRepackagedFileInDirectoryFor(
      GradleBuildVariant variant, String regexFilePathToLocate) throws IOException {
    Path locationToSearch = getOutputDirPathFor(variant);

    // The pattern for the pathMatcher may look something like this:
    // /tmp/.../transforms/FirebasePerformancePlugin/demoFree/debug/*/okhttp3/OkHttpClient.class
    // /tmp/.../debug/*/com/google/firebase/perf/plugin/test/SampleTestAppSource.class
    PathMatcher pathMatcher =
        FileSystems.getDefault()
            .getPathMatcher(String.format("glob:%s/%s", locationToSearch, regexFilePathToLocate));

    if (locationToSearch.toFile().exists()) {
      try (Stream<Path> stream =
          Files.find(
              locationToSearch,
              // Although at max we might just have to go 2-3 levels deep to resolve the regex
              // path, setting the max depth (levels to explore) to 10 (a big enough random
              // number) just in case.
              /* maxDepth= */ 10,
              (path, basicFileAttributes) -> pathMatcher.matches(path))) {
        Optional<Path> firstMatchPath = stream.findFirst();

        if (firstMatchPath.isPresent()) {
          return Optional.of(FileUtils.readFileToString(firstMatchPath.get().toFile()));
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Searches the {@code filePathToLocate} under {@code locationToSearch} and returns the {@link
   * Optional<String>} for the repackaged/output class file.
   *
   * @param variant The variant for which the {@code filePathToLocate} has to be searched in its
   *     outputs.
   * @param filePathToLocate The class path that needs to be located for repackaging.
   * @implNote This method extract all the output jars to search for the relevant file.
   */
  private Optional<String> getRepackagedFileInJarFor(
      GradleBuildVariant variant, String filePathToLocate) throws IOException {

    try (Stream<Path> paths = Files.walk(getOutputDirPathFor(variant))) {
      List<Path> jarFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(SdkConstants.DOT_JAR))
              .collect(Collectors.toList());

      for (Path jarFile : jarFiles) {
        Optional<String> repackagedClass =
            getRepackagedFileInJarFor(jarFile.toFile(), filePathToLocate);

        if (repackagedClass.isPresent()) {
          return repackagedClass;
        }
      }

      return Optional.empty();
    }
  }

  /**
   * Searches the {@code jarFileToExtract} and returns the {@link Optional<String>} for the
   * repackaged/output class file.
   */
  private static Optional<String> getRepackagedFileInJarFor(
      File jarFileToExtract, String filePathToLocate) {

    try (ZipFile inputZip = new ZipFile(jarFileToExtract);
        FileInputStream fis = new FileInputStream(jarFileToExtract);
        ZipInputStream zis = new ZipInputStream(fis)) {

      for (ZipEntry inEntry = zis.getNextEntry(); inEntry != null; inEntry = zis.getNextEntry()) {
        if (inEntry.getName().contains(filePathToLocate)) {
          try (BufferedInputStream bis =
              new BufferedInputStream(inputZip.getInputStream(inEntry))) {
            return Optional.of(IOUtils.toString(bis, StandardCharsets.UTF_8));
          }
        }
      }
    } catch (IOException e) {
      System.out.println(
          String.format("Can't process '%s' because of %s", jarFileToExtract, e.getMessage()));
    }

    return Optional.empty();
  }

  /**
   * Returns if the {@code classFileAsString} is bytecode instrumented and expected to be decorated
   * with the Fireperf SDK APIs.
   *
   * @implNote This will return {@code true} only if the class file either uses Fireperf SDK APIs or
   *     makes a network call with one of the networking library that the 'perf-plugin' supports for
   *     instrumentation (see
   *     https://firebase.google.com/docs/perf-mon/network-traces?platform=android).
   */
  private boolean isClassBytecodeInstrumentedWithFireperfSdkApis(String classFileAsString) {
    return classFileAsString.contains(fireperfSdkInstrumentedDirectorypath);
  }

  /** Returns the repackaged/output directory path for the {@code variant}. */
  private Path getOutputDirPathFor(GradleBuildVariant variant) {
    return Paths.get(String.format("%s/%s", pluginBuildClasspath, variant.getVariantName()));
  }
}
