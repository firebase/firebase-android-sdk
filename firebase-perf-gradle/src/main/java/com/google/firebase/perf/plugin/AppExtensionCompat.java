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

package com.google.firebase.perf.plugin;

import static com.google.firebase.perf.plugin.FirebasePerfPlugin.FIREBASE_PERF_EXTENSION_NAME;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import java.util.Optional;
import org.gradle.api.Project;

/**
 * A class to support {@link com.android.build.gradle.AppExtension}.
 *
 * @deprecated Do not initialize this class after AGP 8.1
 */
class AppExtensionCompat {
  private final AppExtension androidExt;

  AppExtensionCompat(Project project) {
    androidExt = project.getExtensions().getByType(AppExtension.class);
  }

  public void registerPluginForBuildVariantsAndProductFlavors() {
    androidExt
        .getBuildTypes()
        .all(
            buildType ->
                buildType
                    .getExtensions()
                    .add(FIREBASE_PERF_EXTENSION_NAME, FirebasePerfExtension.class));

    androidExt
        .getProductFlavors()
        .all(
            productFlavor ->
                productFlavor
                    .getExtensions()
                    .add(FIREBASE_PERF_EXTENSION_NAME, FirebasePerfExtension.class));
  }

  public Optional<Boolean> isInstrumentationEnabledForBuildType(String buildType) {
    BuildType dslBuildType = androidExt.getBuildTypes().getByName(buildType);
    FirebasePerfExtension buildTypeExt =
        dslBuildType.getExtensions().getByType(FirebasePerfExtension.class);

    return buildTypeExt != null ? buildTypeExt.isInstrumentationEnabled() : Optional.empty();
  }

  public Optional<Boolean> isInstrumentationEnabledForProductFlavor(String flavor) {
    ProductFlavor dslFlavor = androidExt.getProductFlavors().getByName(flavor);
    FirebasePerfExtension flavorExt =
        dslFlavor.getExtensions().getByType(FirebasePerfExtension.class);

    return flavorExt != null ? flavorExt.isInstrumentationEnabled() : Optional.empty();
  }
}
