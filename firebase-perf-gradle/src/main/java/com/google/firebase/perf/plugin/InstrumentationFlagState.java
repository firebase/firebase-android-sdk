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

import com.android.build.api.AndroidPluginVersion;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import com.google.common.base.Ascii;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import kotlin.Pair;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.slf4j.Logger;

/**
 * Stores the state information for the "Project Property" and "Extension Property" Instrumentation
 * enabled flags.
 * <p>
 * https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#project-property-flag
 * https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#extension-property-flag
 */
public final class InstrumentationFlagState implements Serializable {

  private static final Logger logger = FirebasePerfPlugin.getLogger();

  // By default bytecode instrumentation is enabled
  private static final boolean INSTRUMENTATION_ENABLED_DEFAULT = true;

  // Optional that stores Instrumentation enabled value for the Project Property flag
  // https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#project-property-flag
  private final Optional<Boolean> parsedProjectPropertyValue;
  // A Compat class to read the value of the `instrumentationEnabled` flag prior to AGP 8.1.
  @Nullable private final AppExtensionCompat appExtensionCompat;

  /**
   * Lazily fetches the state information for the Project Property and Extension Property
   * Instrumentation enabled flags.
   *
   * @implNote "all-closure"
   * There are potential timing issues if we iterate on build variants during configuration time
   * as they might not all exist yet. To guarantee iteration on all the available variants whether
   * already added or subsequently added we use the "all" method -
   * https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all-org.gradle.api.Action-
   */
  public InstrumentationFlagState(Project project) {
    if (useAppExtension(project)) {
      appExtensionCompat = new AppExtensionCompat(project);
    } else {
      appExtensionCompat = null;
    }
    parsedProjectPropertyValue = fetchProjectPropertyValue(project);
  }

  // region Project Property
  // https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#project-property-flag

  /**
   * Checks the presence and correctness of project property flag {@code
   * firebasePerformanceInstrumentationEnabled} and its value (see
   * https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#project-property-flag).
   *
   * @return An {@code Optional} with the flag value present (if defined correctly).
   * @throws IllegalStateException If the flag value is other than {@code true} or {@code false}.
   */
  private static Optional<Boolean> fetchProjectPropertyValue(final Project project) {
    String propKey = FirebasePerfPlugin.FIREBASE_PERF_INSTRUMENTATION_ENABLED_KEY;
    Provider<String> property = project.getProviders().gradleProperty(propKey);
    if (!property.isPresent()) return Optional.empty();
    Optional<Boolean> parsedPropVal = parseBoolean(property.get());
    if (parsedPropVal.isPresent()) {
      return parsedPropVal;
    }
    throw new IllegalStateException(
        String.format(
            ""
                + "Could not get unknown value '%s' for the project property '%s'"
                + " defined in the 'gradle.properties' file."
                + " Correct format is either '%s=false' or '%s=true'.",
            property.get(), propKey, propKey, propKey));
  }

  private static Optional<Boolean> parseBoolean(String s) {
    if (s != null && (Ascii.equalsIgnoreCase(s, "true") || Ascii.equalsIgnoreCase(s, "false"))) {
      return Optional.of(Boolean.parseBoolean(s));
    }

    return Optional.empty();
  }

  // endregion

  // region Extension Property
  // https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#extension-property-flag
  /**
   * Returns if Instrumentation is enabled for the specified {@code variant} with
   * {@code buildType} and {@code flavors}..
   *
   * <p>When returning the enabled value for a particular variant we adhere to the below priority
   * guidelines which is a recommended guidance in Gradle (first one wins):
   *
   * <pre>
   *  P1. Project Property
   *
   *  P2. Extension Property
   *    P2.1. Build Type
   *    P2.2. Product Flavors (Earlier flavors have higher precedence than later ones)
   *
   *  P3: Default
   * </pre>
   */
  private boolean instrumentationEnabledFor(Variant applicationVariant) {
    if (parsedProjectPropertyValue.isPresent()) {
      logger.info(
          "Firebase Performance Instrumentation is {} per the Project"
              + " Property specified in the 'gradle.properties' file.",
          parsedProjectPropertyValue.get() ? "enabled" : "disabled");

      return parsedProjectPropertyValue.get();
    }

    // If it's older than AGP 8.1, use the value from {@link AppExtension}.
    if (appExtensionCompat != null) {
      return legacyIsInstrumentationEnabled(
          applicationVariant.getName(),
          applicationVariant.getBuildType(),
          applicationVariant.getProductFlavors().stream()
              .map(Pair::getSecond)
              .collect(Collectors.toList()));
    }

    Optional<FirebasePerfVariantExtension> extension =
        Optional.ofNullable(applicationVariant.getExtension(FirebasePerfVariantExtension.class));

    return extension
        .map(
            firebasePerfVariantExtension ->
                firebasePerfVariantExtension.getInstrumentationEnabled().getOrNull())
        .orElse(INSTRUMENTATION_ENABLED_DEFAULT);
  }

  /**
   * This allows fetching the `instrumentationEnabled` flag for productFlavors prior to AGP 8.1.
   *
   * @deprecated Will be removed after the minimum AGP version is bumped up to 8.1
   */
  private boolean legacyIsInstrumentationEnabled(
      String variant, String buildType, List<String> flavors) {
    Optional<Boolean> parsedBuildTypeVal =
        appExtensionCompat.isInstrumentationEnabledForBuildType(buildType);
    if (parsedBuildTypeVal.isPresent()) {
      logger.info(
          "Firebase Performance Instrumentation is {} for {} variant"
              + " per the Extension Property specified (for buildType={})"
              + " in the 'build.gradle' file.",
          parsedBuildTypeVal.get() ? "enabled" : "disabled",
          variant,
          buildType);

      return parsedBuildTypeVal.get();
    }

    for (String flavor : flavors) {
      Optional<Boolean> parsedFlavorsVal =
          appExtensionCompat.isInstrumentationEnabledForProductFlavor(flavor);

      // Earlier flavors have higher precedence than later ones
      if (parsedFlavorsVal.isPresent()) {
        logger.info(
            "Firebase Performance Instrumentation is {} for {}"
                + " variant per the Extension Property specified (for flavors={})"
                + " in the 'build.gradle' file.",
            parsedFlavorsVal.get() ? "enabled" : "disabled",
            variant,
            flavors);

        return parsedFlavorsVal.get();
      }
    }

    logger.info(
        "Firebase Performance Instrumentation is {} by default for {}" + " variant.",
        INSTRUMENTATION_ENABLED_DEFAULT ? "enabled" : "disabled",
        variant);
    return INSTRUMENTATION_ENABLED_DEFAULT;
  }

  private boolean useAppExtension(Project project) {
    AndroidComponentsExtension androidComponentsExtension =
        project.getExtensions().findByType(AndroidComponentsExtension.class);

    if (androidComponentsExtension != null) {
      return androidComponentsExtension.getPluginVersion().compareTo(new AndroidPluginVersion(8, 1))
          < 0;
    }

    logger.warn(
        "Firebase perf plugin was unable to detect the version of the android gradle plugin applied."
            + "\nThis might cause the firebase perf plugin to behave incorrectly. Please file a bug.");
    return false;
  }

  // endregion

  // region Exposed APIs

  /**
   * Returns whether instrumentation is enabled based on the Project Property value
   * (https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android#project-property-flag).
   */
  public Optional<Boolean> getProjectPropertyValue() {
    return parsedProjectPropertyValue;
  }

  /**
   * Returns {@code true} if the instrumentation is enabled for the specified {@code variant} with
   * {@code buildType} and {@code flavors}.
   *
   * @implNote This API is reliable both during Configuration and Execution phase.
   */
  public boolean isEnabledFor(Variant applicationVariant) {
    return instrumentationEnabledFor(applicationVariant);
  }
  // endregion

}
