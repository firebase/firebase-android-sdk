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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

/** A gradle project that represents dev.firebase.beeplus, used for production testing. */
class BeePlusGradleProject extends ExternalResource {
  private static final String APP_ID_SYSTEM_PROPERTY = "firebase.appId";
  static final String APP_ID = getAppId();

  private static String getAppId() {
    return getRequiredSystemProperty(APP_ID_SYSTEM_PROPERTY);
  }

  private static String getRequiredSystemProperty(String propertyName) {
    String value = System.getProperty(propertyName);
    if (isNullOrEmpty(value)) {
      throw new IllegalStateException(
          String.format(
              "\n\n%s system property not set. This is required for production tests.\n",
              propertyName));
    }
    return value;
  }

  static final String PACKAGE_NAME = "com.firebase.appdistribution.prober";
  // Also remember to update the latest AGP/gradle versions in UploadDistributionTaskTest.kt
  // firebase-appdistribution-gradle/src/integrationTest/java/com/google/firebase/appdistribution/gradle/UploadDistributionTaskTest.kt#L724-L726
  static final String LATEST_AGP_VERSION = "9.1.0-alpha05";
  static final String LATEST_GRADLE_VERSION = "9.3.0";
  // The project number for App Distro Probes. We need to use this project
  // because this is the one that's actually linked to play for BeePlus,
  // which is required for AAB uploads.
  private static final String PROJECT_NUMBER_SYSTEM_PROPERTY = "firebase.projectNumber";
  static final long PROJECT_NUMBER = getProjectNumber();

  private static long getProjectNumber() {
    return Long.parseLong(getRequiredSystemProperty(PROJECT_NUMBER_SYSTEM_PROPERTY));
  }

  // A temporary folder for the test project and build outputs.
  private TemporaryFolder projectDir = new TemporaryFolder();

  private File buildFile;
  private File settingsFile;
  private File manifestFile;

  private List<File> pluginClasspathFiles;
  private String pluginClasspathString;

  /**
   * Utility function to write the String {@code content} in the specified File {@code destination}.
   */
  public static void writeFile(File destination, String content) throws IOException {
    FileUtils.writeStringToFile(destination, content);
  }

  /** Utility for creating a file in the project dir */
  public File createFile(String fileName) throws IOException {
    return projectDir.newFile(fileName);
  }

  /*
   * Helper methods for running gradle commands. These will be used by the
   * production tests for easier readability.
   */
  public BuildResult runAddTesters(Collection<String> emails) {
    return GradleRunner.create()
        .withProjectDir(projectDir.getRoot())
        .withArguments(
            "appDistributionAddTesters",
            "--info",
            "--projectNumber",
            String.valueOf(PROJECT_NUMBER),
            "--emails",
            String.join(",", emails))
        .withPluginClasspath(pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .withEnvironment(getEnvironment())
        .build();
  }

  public BuildResult runAddTesters(File emailsFile) {
    return GradleRunner.create()
        .withProjectDir(projectDir.getRoot())
        .withArguments(
            "appDistributionAddTesters",
            "--info",
            "--projectNumber",
            String.valueOf(PROJECT_NUMBER),
            "--file",
            emailsFile.getAbsolutePath())
        .withPluginClasspath(pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .withEnvironment(getEnvironment())
        .build();
  }

  public BuildResult runRemoveTesters(Collection<String> emails) {
    return GradleRunner.create()
        .withProjectDir(projectDir.getRoot())
        .withArguments(
            "appDistributionRemoveTesters",
            "--info",
            "--projectNumber",
            String.valueOf(PROJECT_NUMBER),
            "--emails",
            String.join(",", emails),
            "--debug")
        .withPluginClasspath(pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .withEnvironment(getEnvironment())
        .build();
  }

  public BuildResult runRemoveTesters(File emailsFile) {
    return GradleRunner.create()
        .withProjectDir(projectDir.getRoot())
        .withArguments(
            "appDistributionRemoveTesters",
            "--info",
            "--projectNumber",
            String.valueOf(PROJECT_NUMBER),
            "--file",
            emailsFile.getAbsolutePath(),
            "--debug")
        .withPluginClasspath(pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .withEnvironment(getEnvironment())
        .build();
  }

  public BuildResult runUploadApk() {
    return GradleRunner.create()
        .withProjectDir(projectDir.getRoot())
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info")
        .withPluginClasspath(pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .withEnvironment(getEnvironment())
        .build();
  }

  public BuildResult runUploadAab() {
    return GradleRunner.create()
        .withProjectDir(projectDir.getRoot())
        .withArguments("bundleDebug", "appDistributionUploadDebug", "--info")
        .withPluginClasspath(pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .withEnvironment(getEnvironment())
        .build();
  }

  private String getToken() {
    String token = System.getenv("FIREBASE_TOKEN");
    if (token == null || token.isEmpty()) {
      throw new IllegalStateException(
          "FIREBASE_TOKEN environment variable not set. This is required for production tests.");
    }
    return token;
  }

  private ImmutableMap<String, String> getEnvironment() {
    ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
    env.put("FIREBASE_TOKEN", getToken());
    String androidHome = System.getenv("ANDROID_HOME");
    if (isNullOrEmpty(androidHome)) {
      throw new IllegalStateException(
          "ANDROID_HOME environment variable not set. This is required for production tests.");
    }
    env.put("ANDROID_HOME", androidHome);
    return env.build();
  }

  /** Writes the {@code build.gradle} file for the test project with default build file options */
  public void writeBuildFile() throws IOException {
    writeBuildFile(BuildFileUploadOptions.builder().build());
  }

  /** Writes the {@code build.gradle} file for the test project. */
  public void writeBuildFile(BuildFileUploadOptions options) throws IOException {
    writeFile(
        buildFile,
        String.format(
            ""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        google()\n"
                + "        mavenCentral()\n"
                + "    }\n"
                + "\n"
                + "    dependencies {\n"
                + "        classpath 'com.android.tools.build:gradle:%s'\n"
                + "        classpath files(%s)\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "repositories {\n"
                + "    google()\n"
                + "    mavenCentral()\n"
                + "}\n"
                + "\n"
                + "apply plugin: 'com.android.application'\n"
                + "apply plugin: 'com.google.firebase.appdistribution'\n"
                + "\n"
                + "android {\n"
                + "    namespace '%s'\n"
                + "    compileSdkVersion 30\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        applicationId '%s'\n"
                + "        minSdkVersion 19\n"
                + "        versionCode %s\n"
                + "        versionName \"gradle-prod-test\"\n"
                + "    }\n"
                + "\n"
                + "  buildTypes {\n"
                + "      debug {\n"
                + "          firebaseAppDistribution {\n"
                + "              appId='%s'\n"
                + "              %s\n"
                + "              %s\n"
                + "              %s\n"
                + "              %s\n"
                + "          }\n"
                + "      }\n"
                + "  }\n"
                + "\n"
                + "}\n",
            LATEST_AGP_VERSION,
            pluginClasspathString,
            PACKAGE_NAME,
            PACKAGE_NAME,
            Math.abs(new Random().nextInt()),
            APP_ID,
            isNullOrEmpty(options.getArtifactType())
                ? ""
                : String.format("artifactType='%s'", options.getArtifactType()),
            isNullOrEmpty(options.getReleaseNotes())
                ? ""
                : String.format("releaseNotes='%s'", options.getReleaseNotes()),
            isNullOrEmpty(options.getTesters())
                ? ""
                : String.format("testers='%s'", options.getTesters()),
            isNullOrEmpty(options.getGroups())
                ? ""
                : String.format("groups='%s'", options.getGroups())));
  }

  /**
   * An overridden method from {@link ExternalResource#before()} that allocates the resources
   * required to run test builds.
   */
  @Override
  protected void before() throws IOException, URISyntaxException {
    projectDir.create();
    projectDir.newFolder("src", "main");

    buildFile = projectDir.newFile("build.gradle");
    settingsFile = projectDir.newFile("settings.gradle");
    manifestFile = projectDir.newFile("src/main/AndroidManifest.xml");

    pluginClasspathFiles = extractPluginClasspathFiles();
    pluginClasspathString = convertPluginClasspathString(pluginClasspathFiles);

    writeSettingsFile();
    writeManifestFile();
  }

  /**
   * An overridden method from {@link ExternalResource#after()} that releases any allocated
   * resources after the execution of the test methods.
   */
  @Override
  protected void after() {
    projectDir.delete();
  }

  /**
   * Retrieves and collects the plugin's classpath (generated by "createClasspathManifest" task in
   * build.gradle) into a {@link List<File>}. This is necessary because builds and tests are
   * executed in separate processes. Therefore, we need to explicitly specify the classpath to make
   * our gradle plugin available to our tests.
   *
   * <p>Refer to:
   * https://docs.gradle.org/current/userguide/test_kit.html#sub:test-kit-classpath-injection
   */
  private List<File> extractPluginClasspathFiles() throws IOException, URISyntaxException {
    URL pluginClasspathResource = getClass().getClassLoader().getResource("plugin-classpath.txt");
    if (pluginClasspathResource == null) {
      throw new IllegalStateException(
          "Did not find plugin classpath resource, run `testClasses` build task.");
    }

    return Files.readAllLines(Path.of(pluginClasspathResource.toURI())).stream()
        .map(File::new)
        .collect(Collectors.toList());
  }

  /**
   * Converts the plugin's classpath from {@link List<File>} to {@link String}.
   *
   * <p>Refer to:
   * https://docs.gradle.org/5.4.1/userguide/test_kit.html#sub:test-kit-classpath-injection
   */
  private String convertPluginClasspathString(List<File> pluginClasspathFiles) {
    return pluginClasspathFiles.stream()
        .map(file -> file.getAbsolutePath().replace("\\", "\\\\"))
        .map(path -> String.format("'%s'", path))
        .collect(Collectors.joining(","));
  }

  /** Writes the {@code settings.gradle} file for the test project. */
  private void writeSettingsFile() throws IOException {
    writeFile(settingsFile, "rootProject.name = 'app'");
  }

  /** Writes the {@code AndroidManifest.xml} file for the test project. */
  private void writeManifestFile() throws IOException {
    writeFile(
        manifestFile,
        /* content= */ String.format(
            "" // AndroidManifest.xml
                + "<manifest\n"
                + "    package= '%s'>\n"
                + "  <application/>\n"
                + "</manifest>",
            PACKAGE_NAME));
  }
}
