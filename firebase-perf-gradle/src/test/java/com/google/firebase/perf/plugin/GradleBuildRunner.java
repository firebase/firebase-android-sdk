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

import com.google.common.io.MoreFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;

/**
 * Wrapper around {@link GradleRunner} that is customized to run Gradle builds for Firebase
 * Performance Gradle Plugin.
 */
public final class GradleBuildRunner {

  // FirePerf SDK dependency against which we will test the FirePerf Gradle Plugin.
  // It can be kept in sync with the latest public/dev version but in general the Plugin should
  // work with any version of the SDK unless there is a strict dependency.
  // https://firebase.google.com/support/release-notes/android#latest_sdk_versions?q="firebase-perf"
  private static final String FIREPERF_SDK_DEP = "com.google.firebase:firebase-perf:20.1.0";
  private static final String OKHTTP3_LIB_DEP = "com.squareup.okhttp3:okhttp:4.8.1";

  // Any supported 3rd party networking library we want to instrument as part of the
  // transformation performed by the FirePerf Gradle Plugin.
  private static final String VOLLEY_LIB_DEP = "com.android.volley:volley:1.2.1";
  private static final String VOLLEY_LIB_JAR_NAME = "volley-1.2.1-runtime.jar";

  private static final String MULTIDEX_DEP = "com.android.support:multidex:2.0.1";

  // Path for one of the classes in the used 3rd party networking library (in this case volley)
  // that we would like to locate as a verification of the transformation performed by the
  // FirePerf Gradle Plugin.
  private static final String VOLLEY_CLASS_TO_LOCATE = "com/android/volley/toolbox/HurlStack.class";

  // Corresponding path of the class in the FirePerf SDK which contains the implementation for the
  // bytecode instrumented methods in the used 3rd party networking library (for example:
  // "com/google/firebase/perf/network/FirebasePerfOkHttpClient").
  private static final String FIREPERF_SDK_CLASS_PATH = "com/google/firebase/perf/network";

  // Kotlin Gradle Plugin.
  // Refer https://kotlinlang.org/docs/reference/using-gradle.html#using-gradle
  private static final String KOTLIN_GRADLE_PLUGIN_CLASSPATH_DEP =
      "org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10";

  // Kotlin Gradle Plugin for Android.
  // Refer https://developer.android.com/kotlin/add-kotlin
  private static final String KOTLIN_ANDROID_GRADLE_PLUGIN_DEP = "kotlin-android";

  // Ktor is an asynchronous framework for creating microservices, web applications etc.
  // Refer https://ktor.io/
  private static final String KTOR_FRAMEWORK_DEP = "io.ktor:ktor-client-apache:1.4.0";

  // Use the latest from https://developer.android.com/about/versions/10/setup-sdk#update-build
  // Sometimes build fails just because of this not being latest.
  private static final String COMPILE_SDK_VERSION = "29";

  // LINT.IfChange(asm_api_version)
  private static final String ASM_API_VERSION = "9.0";
  // LINT.ThenChange()

  private static final String JVM_MAX_MEMORY_SIZE = "7g"; // 7 GB

  private GradleRunner runner;

  private File testAppProjectDir;

  private File gradleHomeDir;
  private File gradlePropertiesFile;
  private File gradleBuildFile;

  private String pluginClasspathString;
  private String testAppPackageName;
  private String testAppClassName;

  // Version for Android Gradle plugin ("com.android.tools.build:gradle").
  // Default is the latest version.
  // Refer: https://developer.android.com/studio/releases/gradle-plugin#updating-gradle
  private String agpVersion = "7.2.0";

  // Version for Gradle to be used in building the test app
  // Refer: https://gradle.org/releases
  private String gradleVersion = "7.3.3";

  // Refer: https://developer.android.com/studio/build#properties-files
  private Boolean projectFlagEnabled;

  // Refer: https://developer.android.com/studio/build/build-variants#build-types
  private Boolean debugBuildFlagEnabled;
  private Boolean releaseBuildFlagEnabled;

  // Refer: https://developer.android.com/studio/build/build-variants#product-flavors
  private Boolean demoFlavorFlagEnabled;
  private Boolean fullFlavorFlagEnabled;
  private Boolean freeFlavorFlagEnabled;
  private Boolean paidFlavorFlagEnabled;
  private List<String> buildscriptReposContent = new ArrayList<>();
  private List<String> buildscriptDepsContent = new ArrayList<>();
  private List<String> applyPluginsContent = new ArrayList<>();
  private List<String> appDepsContent = new ArrayList<>();

  /** Generates a new clean instance of {@link GradleRunner}. */
  GradleBuildRunner(
      File testAppProjectDir,
      File gradleHomeDir,
      String testAppPackageName,
      String testAppClassName,
      File gradlePropertiesFile,
      File gradleBuildFile,
      List<File> pluginClasspathFiles,
      String pluginClasspathString) {

    this.testAppProjectDir = testAppProjectDir;
    this.gradleHomeDir = gradleHomeDir;
    this.testAppPackageName = testAppPackageName;
    this.testAppClassName = testAppClassName;
    this.gradlePropertiesFile = gradlePropertiesFile;
    this.gradleBuildFile = gradleBuildFile;
    this.pluginClasspathString = pluginClasspathString;

    this.runner =
        GradleRunner.create()
            .withProjectDir(testAppProjectDir)
            .forwardOutput() // Used for debugging
            .withPluginClasspath(pluginClasspathFiles);
  }

  /** Initializes the runner with Kotlin configurations. */
  GradleBuildRunner initWithKotlin() {
    this.buildscriptDepsContent.add(
        String.format("classpath '%s'", KOTLIN_GRADLE_PLUGIN_CLASSPATH_DEP));
    this.applyPluginsContent.add(
        String.format("apply plugin: '%s'", KOTLIN_ANDROID_GRADLE_PLUGIN_DEP));
    this.appDepsContent.add(String.format("implementation '%s'", KTOR_FRAMEWORK_DEP));

    return this;
  }

  public GradleBuildRunner withAndroidGradlePluginVersion(String agpVersion) {
    this.agpVersion = agpVersion;
    return this;
  }

  public GradleBuildRunner withGradleVersion(String gradleVersion) {
    this.gradleVersion = gradleVersion;
    return this;
  }

  /**
   * Sets the Project Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_INSTRUMENTATION_ENABLED_KEY} to enable/disable the Plugin for
   * the project.
   */
  public GradleBuildRunner setProjectFlagEnabled(Boolean projectFlagEnabled) {
    this.projectFlagEnabled = projectFlagEnabled;
    return this;
  }

  /**
   * Sets the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME} to enable/disable the Plugin for the {@link
   * GradleBuildVariant#DEBUG} variant.
   */
  public GradleBuildRunner setDebugBuildFlagEnabled(Boolean debugBuildFlagEnabled) {
    this.debugBuildFlagEnabled = debugBuildFlagEnabled;
    return this;
  }

  /**
   * Sets the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME} to enable/disable the Plugin for the {@link
   * GradleBuildVariant#RELEASE} variant.
   */
  public GradleBuildRunner setReleaseBuildFlagEnabled(Boolean releaseBuildFlagEnabled) {
    this.releaseBuildFlagEnabled = releaseBuildFlagEnabled;
    return this;
  }

  /**
   * Sets the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME} for the "Demo" flavor to enable/disable the
   * Plugin for one of the below variants (depending on which flavors are built):
   *
   * <pre>
   *  - {@link GradleBuildVariant#DEMO_DEBUG} variant.
   *  - {@link GradleBuildVariant#DEMO_RELEASE} variant.
   *  - {@link GradleBuildVariant#DEMO_FREE_DEBUG} variant.
   *  - {@link GradleBuildVariant#DEMO_FREE_RELEASE} variant.
   *  - {@link GradleBuildVariant#DEMO_PAID_DEBUG} variant.
   *  - {@link GradleBuildVariant#DEMO_PAID_RELEASE} variant.
   *
   * </pre>
   *
   * Note that only flavors which are specifically enabled (apart from Debug and Release) are built.
   */
  public GradleBuildRunner setDemoFlavorFlagEnabled(Boolean demoFlavorFlagEnabled) {
    this.demoFlavorFlagEnabled = demoFlavorFlagEnabled;
    return this;
  }

  /**
   * Sets the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME} for the "Full" flavor to enable/disable the
   * Plugin for one of the below variants (depending on which flavors are built):
   *
   * <pre>
   *  - {@link GradleBuildVariant#FULL_DEBUG} variant.
   *  - {@link GradleBuildVariant#FULL_RELEASE} variant.
   *  - {@link GradleBuildVariant#FULL_FREE_DEBUG} variant.
   *  - {@link GradleBuildVariant#FULL_FREE_RELEASE} variant.
   *  - {@link GradleBuildVariant#FULL_PAID_DEBUG} variant.
   *  - {@link GradleBuildVariant#FULL_PAID_RELEASE} variant.
   *
   * </pre>
   *
   * Note that only flavors which are specifically enabled (apart from Debug and Release) are built.
   */
  public GradleBuildRunner setFullFlavorFlagEnabled(Boolean fullFlavorFlagEnabled) {
    this.fullFlavorFlagEnabled = fullFlavorFlagEnabled;
    return this;
  }

  /**
   * Sets the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME} for the "Free" flavor to enable/disable the
   * Plugin for one of the below variants (depending on which flavors are built):
   *
   * <pre>
   *  - {@link GradleBuildVariant#FREE_DEBUG} variant.
   *  - {@link GradleBuildVariant#FREE_RELEASE} variant.
   *  - {@link GradleBuildVariant#DEMO_FREE_DEBUG} variant.
   *  - {@link GradleBuildVariant#DEMO_FREE_RELEASE} variant.
   *  - {@link GradleBuildVariant#FULL_FREE_DEBUG} variant.
   *  - {@link GradleBuildVariant#FULL_FREE_RELEASE} variant.
   *
   * </pre>
   *
   * Note that only flavors which are specifically enabled (apart from Debug and Release) are built.
   */
  public GradleBuildRunner setFreeFlavorFlagEnabled(Boolean freeFlavorFlagEnabled) {
    this.freeFlavorFlagEnabled = freeFlavorFlagEnabled;
    return this;
  }

  /**
   * Sets the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME} for the "Paid" flavor to enable/disable the
   * Plugin for one of the below variants (depending on which flavors are built):
   *
   * <pre>
   *  - {@link GradleBuildVariant#PAID_DEBUG} variant.
   *  - {@link GradleBuildVariant#PAID_RELEASE} variant.
   *  - {@link GradleBuildVariant#DEMO_PAID_DEBUG} variant.
   *  - {@link GradleBuildVariant#DEMO_PAID_RELEASE} variant.
   *  - {@link GradleBuildVariant#FULL_PAID_DEBUG} variant.
   *  - {@link GradleBuildVariant#FULL_PAID_RELEASE} variant.
   *
   * </pre>
   *
   * Note that only flavors which are specifically enabled (apart from Debug and Release) are built.
   */
  public GradleBuildRunner setPaidFlavorFlagEnabled(Boolean paidFlavorFlagEnabled) {
    this.paidFlavorFlagEnabled = paidFlavorFlagEnabled;
    return this;
  }

  /**
   * Sets the Project Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_INSTRUMENTATION_ENABLED_KEY} to enable/disable the Plugin for
   * the project.
   */
  public GradleBuildRunner addTransportDependencies() {
    this.appDepsContent.add(
        "implementation 'com.google.android.datatransport:transport-api:2.2.1'");
    this.appDepsContent.add("runtimeOnly 'com.google.firebase:firebase-datatransport:17.0.8'");
    return this;
  }

  /** Writes the build file (based on which parameters/flags are set) and executes a clean build. */
  @SuppressWarnings("UnstableApiUsage")
  public GradleBuildResult cleanBuild(GradleBuildVariant variantToExecute, String... args)
      throws IOException {

    Path buildOutput = testAppProjectDir.toPath().resolve("build");

    if (Files.exists(buildOutput)) {
      MoreFiles.deleteDirectoryContents(buildOutput);
    }

    return build(variantToExecute, args);
  }

  /**
   * Writes the build file (based on which parameters/flags are set) and executes the build for the
   * {@code variantToExecute} with the passed {@code args}.
   */
  public GradleBuildResult build(GradleBuildVariant variantToExecute, String... args)
      throws IOException {
    setBuildArguments(variantToExecute, args);
    writeGradlePropertiesFile();
    writeBuildFile();

    return new GradleBuildResult(
        runner.withGradleVersion(gradleVersion).build(),
        gradleHomeDir,
        String.format(
            "%s%s/%s.class",
            "",
            testAppPackageName.replace(/* target= */ ".", /* replacement= */ "/"),
            testAppClassName),
        String.format(
            "%s/%s",
            testAppProjectDir.getAbsolutePath(),
            "build/intermediates/asm_instrumented_project_classes"),
        VOLLEY_CLASS_TO_LOCATE,
        VOLLEY_LIB_JAR_NAME,
        FIREPERF_SDK_CLASS_PATH);
  }

  /** Sets the build arguments. */
  private void setBuildArguments(GradleBuildVariant variantToExecute, String... args) {
    List<String> buildArgs = new ArrayList<>();
    buildArgs.add(variantToExecute.getBuildTask());

    if (args != null && args.length > 0) {
      buildArgs.addAll(Arrays.asList(args));
    }
    buildArgs.add("-g");
    buildArgs.add(gradleHomeDir.getAbsolutePath());

    this.runner.withArguments(buildArgs);
  }

  /**
   * Writes the {@code gradle.properties} file for the test project.
   */
  private void writeGradlePropertiesFile() throws IOException {
    String projectPropertyContent =
        projectFlagEnabled == null
            ? ""
            : String.format(
                ""
                    // LINT.IfChange(project_property)
                    + "firebasePerformanceInstrumentationEnabled=%s\n",
                // LINT.ThenChange()
                projectFlagEnabled);
    projectPropertyContent += "android.useAndroidX=true\n" + "android.enableJetifier=true\n";
    projectPropertyContent +=
        String.format(
            "android.useAndroidX=true\n"
                + "android.enableJetifier=true\n"
                // Increasing the limit because of Metaspace OOM error while running the build
                // Refer https://stackoverflow.com/a/54045133
                + "org.gradle.jvmargs=-Xmx%s\n",
            JVM_MAX_MEMORY_SIZE);
    GradleBuildProject.writeFile(gradlePropertiesFile, projectPropertyContent);
  }

  /** Writes the {@code build.gradle} file for the test project. */
  private void writeBuildFile() throws IOException {

    GradleBuildProject.writeFile(
        gradleBuildFile,
        /* content= */ String.format(
            ""
                + "%s\n" // buildscript { ... } block
                + "%s\n" // apply plugins ... section
                + "\n"
                + "android {\n" // android { ... block START
                + "  namespace '%s'\n"
                + "  compileSdkVersion %s\n"
                + "  packagingOptions {\n"
                + "    exclude 'META-INF/DEPENDENCIES'\n"
                + "  }\n"
                + "  compileOptions {\n"
                + "    sourceCompatibility 1.8\n"
                + "    targetCompatibility 1.8\n"
                + "  }\n"
                + "\n"
                + "  defaultConfig {\n"
                + "    minSdkVersion 26\n"
                + "    applicationId '%s'\n"
                + "    multiDexEnabled true\n"
                + "  }\n"
                + "\n"
                + "  lintOptions {\n"
                + "     checkReleaseBuilds false\n"
                + "     abortOnError false\n"
                + "   }\n"
                + "\n"
                + "  %s\n" // buildTypes { ... } block
                + "\n"
                + "  %s\n" // productFlavors { ... } block
                + "\n"
                + "}\n" // android ... } block END
                + "\n"
                + "%s\n", // dependencies { ... } block
            generateBuildscriptContent(),
            generateApplyPluginsContent(),
            testAppPackageName,
            COMPILE_SDK_VERSION,
            testAppPackageName,
            generateBuildTypesContent(),
            generateProductFlavorsContent(),
            generateAppDepsContent()));
  }

  /**
   * Generates the content for the buildscript block.
   *
   * <p>Refer:
   * https://docs.gradle.org/current/userguide/tutorial_using_tasks.html#sec:build_script_external_dependencies
   */
  private String generateBuildscriptContent() {
    StringBuilder additionalRepos = new StringBuilder();
    for (String content : buildscriptReposContent) {
      additionalRepos.append(content).append("\n");
    }

    StringBuilder additionalDeps = new StringBuilder();
    for (String content : buildscriptDepsContent) {
      additionalDeps.append(content).append("\n");
    }

    return String.format(
        ""
            + "buildscript {\n"
            + "    repositories {\n"
            + "        %s\n"
            + "        google()\n"
            + "        mavenCentral()\n"
            + "    }\n"
            + "\n"
            + "    dependencies {\n"
            // LINT.IfChange(pom_dependencies)
            // Required because our Plugin defines the dependency on the ASM lib in the POM
            // file and thus is pulled automatically when classpath dependency is on
            // 'com.google.firebase:perf-plugin'. But since in this case we test the Plugin
            // locally, we need to explicitly define the ASM dependency.
            + "        classpath 'org.ow2.asm:asm:%s'\n"
            + "        classpath 'org.ow2.asm:asm-commons:%s'\n"
            // LINT.ThenChange()
            // Classpath for running tests on Gradle.
            + "        classpath files(%s)\n"
            + "        classpath 'com.android.tools.build:gradle:%s'\n"
            + "        %s"
            + "    }\n"
            + "}\n"
            + "\n"
            + "repositories {\n"
            + "  google()\n"
            + "  mavenCentral()\n"
            + "}\n",
        additionalRepos.toString(),
        ASM_API_VERSION,
        ASM_API_VERSION,
        pluginClasspathString,
        agpVersion,
        additionalDeps.toString());
  }

  /**
   * Generates the content for the plugins to be applied to the Test App.
   *
   * <p>Refer: https://docs.gradle.org/current/userguide/plugins.html
   */
  private String generateApplyPluginsContent() {
    StringBuilder additionalPlugins = new StringBuilder();
    for (String content : applyPluginsContent) {
      additionalPlugins.append(content).append("\n");
    }

    return String.format(
        ""
            + "apply plugin: 'com.google.firebase.firebase-perf'\n"
            + "apply plugin: 'com.android.application'\n"
            + "%s\n",
        additionalPlugins.toString());
  }

  /**
   * Generates the content for the app dependencies block.
   *
   * <p>Refer: https://developer.android.com/studio/build/dependencies#dependency-types
   */
  private String generateAppDepsContent() {
    StringBuilder additionalDeps = new StringBuilder();
    for (String content : appDepsContent) {
      additionalDeps.append(content).append("\n");
    }

    return String.format(
        "" // dependencies { ... } block
            + "dependencies {\n"
            + "  implementation fileTree(dir: 'libs', include: ['*.jar'])\n"
            + "  implementation '%s'\n"
            + "  implementation '%s'\n"
            + "  implementation '%s'\n"
            + "  implementation '%s'\n"
            + "  %s"
            + "}\n",
        FIREPERF_SDK_DEP, OKHTTP3_LIB_DEP, VOLLEY_LIB_DEP, MULTIDEX_DEP, additionalDeps.toString());
  }

  /**
   * Generates the content for the buildTypes block.
   *
   * <p>Refer: https://developer.android.com/studio/build/build-variants#build-types
   */
  private String generateBuildTypesContent() {
    String debugBuildContent =
        generateContentFor(/* variant= */ "debug", debugBuildFlagEnabled, /* dimension= */ null);

    String releaseBuildContent =
        generateContentFor(
            /* variant= */ "release", releaseBuildFlagEnabled, /* dimension= */ null);

    return String.format(
        "" // buildTypes { ... } block
            + "  buildTypes {\n"
            + "    %s\n" // debug build { ... } block
            + "    %s\n" // release build { ... } block
            + "  }\n"
            + "\n",
        debugBuildContent, releaseBuildContent);
  }

  /**
   * Generates the content for the productFlavors block.
   *
   * <p>Refer: https://developer.android.com/studio/build/build-variants#product-flavors
   */
  private String generateProductFlavorsContent() {
    String demoFlavorContent =
        generateContentFor(/* variant= */ "demo", demoFlavorFlagEnabled, /* dimension= */ "mode");

    String fullFlavorContent =
        generateContentFor(/* variant= */ "full", fullFlavorFlagEnabled, /* dimension= */ "mode");

    String freeFlavorContent =
        generateContentFor(
            /* variant= */ "free", freeFlavorFlagEnabled, /* dimension= */ "pricing");

    String paidFlavorContent =
        generateContentFor(
            /* variant= */ "paid", paidFlavorFlagEnabled, /* dimension= */ "pricing");

    String flavorDimensionsContent =
        generateContentForFlavorDimensions(
            !(demoFlavorContent.isEmpty() && fullFlavorContent.isEmpty()),
            !(freeFlavorContent.isEmpty() && paidFlavorContent.isEmpty()));

    return String.format(
        "" // productFlavors { ... } block
            + "  %s\n" // flavorDimensions content
            + "  productFlavors {\n"
            + "    %s\n" // demo flavor { ... } block
            + "    %s\n" // full flavor { ... } block
            + "    %s\n" // free flavor { ... } block
            + "    %s\n" // paid flavor { ... } block
            + "  }\n"
            + "\n",
        flavorDimensionsContent,
        demoFlavorContent,
        fullFlavorContent,
        freeFlavorContent,
        paidFlavorContent);
  }

  /**
   * Generates the content for the flavorDimensions.
   *
   * <p>Refer: https://developer.android.com/studio/build/build-variants#flavor-dimensions
   */
  private String generateContentForFlavorDimensions(
      boolean modeDimensionExists, boolean pricingDimensionExists) {

    if (modeDimensionExists && pricingDimensionExists) {
      return "  flavorDimensions 'mode', 'pricing'";

    } else if (modeDimensionExists) {
      return "  flavorDimensions 'mode'";

    } else if (pricingDimensionExists) {
      return "  flavorDimensions 'pricing'";
    }

    return "";
  }

  /**
   * Generates the content for the Extension Property flag represented by {@link
   * FirebasePerfPlugin#FIREBASE_PERF_EXTENSION_NAME}
   *
   * @param variant The build variant (either for buildType or productFlavor) for which the flag is
   *     applied.
   * @param flag The Boolean value of the flag. {@code null} means the Extension Property won't be
   *     set.
   * @param dimension The flavorDimension for which the flag is applied. {@code null} if the variant
   *     is a buildType (like debug or release).
   */
  private String generateContentFor(String variant, Boolean flag, String dimension) {
    return flag == null
        ? ""
        : String.format(
            "" // Setting the "FirebasePerformance { instrumentationEnabled }" Extension Property.
                + "    %s {\n"
                + "        %s\n"
                + "        %s {\n"
                + "            %s = %b\n"
                + "        }\n"
                + "    }\n",
            variant,
            dimension == null ? "" : String.format("dimension = '%s'", dimension),

            // LINT.IfChange(extension_name)
            "FirebasePerformance",
            // LINT.ThenChange()

            // LINT.IfChange(extension_property)
            "instrumentationEnabled",
            // LINT.ThenChange()
            flag);
  }
}
