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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.AndroidPluginVersion;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.DslExtension;
import com.android.ide.common.repository.GradleVersion;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Plugin} that checks if the project is an Android project and if so, registers a
 * transform to begin the instrumentation.
 *
 * <p>Instrumentation is enabled by default but if you don't need to run the instrumentation, speed
 * up your builds by adding an {@code instrumentationEnabled} flag using the following options:
 *
 * <p>1. Extension Property ({@link #FIREBASE_PERF_EXTENSION_NAME}) — disables the plugin for a
 * specific build variant at compile time.
 *
 * <p>2. Project Property ({@link #FIREBASE_PERF_INSTRUMENTATION_ENABLED_KEY}) — disables the
 * plugin for all build variants at compile time.
 */
public class FirebasePerfPlugin implements Plugin<Project> {

  // Represents the tag name that can be used to filter out the Firebase Performance Plugin logs
  public static final String FIREBASE_PERF_TAG = "FirebasePerformancePlugin";

  public static final String MINIMUM_SUPPORTED_AGP_VERSION = "7.0.0";

  public static final String ANDROID_COMPONENTS_EXTENSION_MINIMUM_AGP = "8.1.0";

  public static final String AGP_NEW_INSTRUMENTATION_API_VERSION = "7.2.0";

  // instrumentFireperfE2ETest should only be set to true when running Fireperf E2E test.
  private static boolean instrumentFireperfE2ETest = false;
  private static final Logger logger = LoggerFactory.getLogger(FIREBASE_PERF_TAG);

  // LINT.IfChange
  /*
   * Represents the name of the Firebase Performance Extension that we register to provide
   * functionality to enable/disable the instrumentation for a specific build variant at compile
   * time.
   *
   * <p>Usage: Add the following flag to your module (app-level) "build.gradle" file, then set it
   * to false to disable the plugin.
   *
   * <pre>{@code
   * debug {
   *   FirebasePerformance {
   *     // Set this flag to 'false' to disable @AddTrace annotation processing and automatic
   *     // HTTP/S network request monitoring for a specific build variant at compile time.
   *     instrumentationEnabled false
   *   }
   * }
   * }</pre>
   */
  public static final String FIREBASE_PERF_EXTENSION_NAME = "FirebasePerformance";
  // LINT.ThenChange(firebase/firebase-android-sdk/firebase-perf-gradle/\
  //   src/test/java/com/google/firebase/perf/plugin/GradleBuildRunner.java:extension_name)

  // LINT.IfChange
  /*
   * Represents the Project Property flag that can be used to disable the instrumentation for all
   * build variants at compile time.
   *
   * <p>Usage: Add the following flag to your "gradle.properties" file, then set it to false to
   * disable the plugin.
   *
   * <pre>{@code
   * // ...
   *
   * // Set this flag to 'false' to disable @AddTrace annotation processing and automatic
   * // HTTP/S network request monitoring for all the build variants at compile time.
   * firebasePerformanceInstrumentationEnabled=false
   * }</pre>
   *
   * <p>Note: The Project Property flag (if defined) is applied to all build variants and takes
   * precedence over the Extension Property flag. To re-enable the plugin in your production app,
   * you must either comment out the Project Property flag or set it to true.
   */
  public static final String FIREBASE_PERF_INSTRUMENTATION_ENABLED_KEY =
      "firebasePerformanceInstrumentationEnabled";
  // LINT.ThenChange(firebase/firebase-android-sdk/firebase-perf-gradle/\
  //   src/test/java/com/google/firebase/perf/plugin/GradleBuildRunner.java:project_property)
  private boolean foundApplicationPlugin = false;

  @Override
  public void apply(final Project project) {
    instrumentFireperfE2ETest =
        project
            .getProviders()
            .gradleProperty("instrumentFireperfE2ETest")
            .map(Boolean::parseBoolean)
            .orElse(false)
            .get();

    // Only apply the performance plugin with the application plugin
    project
        .getPluginManager()
        .withPlugin(
            /* id= */ "com.android.application",
            androidPlugin -> {
              foundApplicationPlugin = true;
              try {
                perform(project);
              } catch (ClassNotFoundException e) {
                logger.error(
                    "Perform performance plugin failed: class not found {}", e.getMessage());
              }
            });

    // Only used to verify that the android app plugin has been applied
    project.afterEvaluate(
        project2 -> {
          if (!foundApplicationPlugin) {
            throw new IllegalStateException(
                FIREBASE_PERF_TAG
                    + " must only be used with Android application projects."
                    + " Need to apply the 'com.android.application' plugin with this plugin.");
          }
        });
  }

  /**
   * Performs the core logic for the performance plugin.
   */
  private void perform(Project project) throws ClassNotFoundException {
    if (!androidGradlePluginVersionIsAtLeast(project, MINIMUM_SUPPORTED_AGP_VERSION)) {
      // TODO: Explore removing this log after a larger refactor.
      logger.error(
          "Failed to apply {} Gradle plugin. Please upgrade AGP to {} or higher.",
          FIREBASE_PERF_EXTENSION_NAME,
          MINIMUM_SUPPORTED_AGP_VERSION);
      return;
    }

    registerExtension(project);

    InstrumentationFlagState instrumentationFlagState = new InstrumentationFlagState(project);
    Optional<Boolean> parsedProjectPropertyValue =
        instrumentationFlagState.getProjectPropertyValue();

    if (parsedProjectPropertyValue.isPresent() && !parsedProjectPropertyValue.get()) {
      logger.info(
          "{} is disabled globally for the project by specifying"
              + " '{}=false' flag in the 'gradle.properties' file.",
          FIREBASE_PERF_TAG,
          FIREBASE_PERF_INSTRUMENTATION_ENABLED_KEY);
    } else {
      // Register the Transform to perform the bytecode instrumentation
      FirebasePerfClassVisitorFactory.registerForProject(
          project, instrumentationFlagState, useInstrumentationApi(project));
    }
  }

  /**
   * Registers the {@link FirebasePerfExtension} on all the build variants.
   *
   * @implNote "all-closure"
   * There are potential timing issues if we iterate on build variants during configuration time
   * as they might not all exist yet. To guarantee iteration on all the available variants whether
   * already added or subsequently added we use the "all" method -
   * https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all-org.gradle.api.Action-
   */
  @SuppressWarnings("UnstableApiUsage")
  private void registerExtension(Project project) {
    if (useAndroidComponentsExtensionApi(project)) {
      ApplicationAndroidComponentsExtension androidComponents =
          project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);

      // Registering the extension in all cases will avoid errors on the developer's build when
      // using "FirebasePerformance" extension APIs when the instrumentation is disabled.
      DslExtension extensionToAllBuilds =
          new DslExtension.Builder(FIREBASE_PERF_EXTENSION_NAME)
              .extendBuildTypeWith(FirebasePerfExtension.class)
              .extendProductFlavorWith(FirebasePerfExtension.class)
              .build();

      androidComponents.registerExtension(
          extensionToAllBuilds,
          config -> project.getObjects().newInstance(FirebasePerfVariantExtension.class, config));
    } else {
      // TODO: Remove this with the next major version bump.
      AppExtensionCompat appExtensionCompat = new AppExtensionCompat(project);
      appExtensionCompat.registerPluginForBuildVariantsAndProductFlavors();
    }
  }

  // region version logic

  /**
   * Returns the current version (like {@code x.y.z}) of Firebase Performance Gradle Plugin or
   * {@code unknown} if it couldn't be located/loaded from the resources.
   */
  public static String getPluginVersion() {
    return getPluginVersion(
        FirebasePerfPlugin.class.getClassLoader(),
        /* projectPropsResFile= */ "com/google/firebase/perf/plugin/project.properties");
  }

  /**
   * Returns the current plugin version from the {@code projectPropsResFile}.
   *
   * @param classLoader         The classloader used to fetch the resource corresponding to {@code
   *                            projectPropsResFile}.
   * @param projectPropsResFile The relative path to the {@code project.properties} file in the
   *                            {@code src/main/resources} folder that contains information about the current plugin
   *                            version.
   */
  @VisibleForTesting
  static String getPluginVersion(ClassLoader classLoader, String projectPropsResFile) {
    Properties properties = new Properties();

    if (classLoader != null) {
      try (InputStream stream =
          new BufferedInputStream(classLoader.getResourceAsStream(projectPropsResFile))) {
        properties.load(stream);
      } catch (IOException e) {
        logger.warn(String.format("Could not load '%s' file.", projectPropsResFile), e);
      }
    }

    // LINT.IfChange(plugin_version)
    return properties.getProperty("pluginVersion", /* defaultValue= */ "unknown");
    // LINT.ThenChange(firebase/firebase-android-sdk/firebase-perf-gradle/\
    //
    // src/test/java/com/google/firebase/perf/plugin/FirebasePerfPluginTest.java:plugin_version_default)
  }

  @NonNull
  private List<Integer> splitVersionParts(@NonNull String version) {
    String[] splits = version.split("\\.");
    return Arrays.stream(splits).map(Integer::valueOf).collect(Collectors.toList());
  }

  private boolean androidGradlePluginVersionIsAtLeast(
      @NonNull Project project, @NonNull String minimumExpectedVersion) {
    // TODO: Explore simplifying this check given the minimum supported AGP version 7.0.0.
    try {
      AndroidComponentsExtension androidComponents =
          project.getExtensions().findByType(AndroidComponentsExtension.class);
      if (androidComponents != null) {
        List<Integer> parts = splitVersionParts(minimumExpectedVersion);
        AndroidPluginVersion minimumVersion =
            new AndroidPluginVersion(parts.get(0), parts.get(1), parts.get(2));
        return androidComponents.getPluginVersion().compareTo(minimumVersion) >= 0;
      }
    } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
      // We're running against an older version where these APIs don't exist
    }

    try {
      return GradleVersion.parseAndroidGradlePluginVersion(
                  com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION)
              .compareTo(GradleVersion.parseAndroidGradlePluginVersion(minimumExpectedVersion))
          >= 0;
    } catch (NoClassDefFoundError | NoSuchMethodError error) {
      logger.warn(
          "Firebase perf plugin was unable to detect the version of the android gradle plugin applied."
              + "\nThis might cause the firebase perf plugin to behave incorrectly. Please file a bug.");
      return false;
    }
  }

  private boolean useInstrumentationApi(@NonNull Project project) {
    return androidGradlePluginVersionIsAtLeast(project, AGP_NEW_INSTRUMENTATION_API_VERSION);
  }

  private boolean useAndroidComponentsExtensionApi(@NonNull Project project) {
    return androidGradlePluginVersionIsAtLeast(project, ANDROID_COMPONENTS_EXTENSION_MINIMUM_AGP);
  }

  // endregion

  /**
   * Returns the Logger for the Firebase Performance Plugin.
   */
  public static Logger getLogger() {
    return logger;
  }

  public static boolean getInstrumentFireperfE2ETest() {
    // TODO: Investigate switching this to read classes/package names from the extension,
    //  allowing users to set their own allow/denylist.
    return instrumentFireperfE2ETest;
  }
}
