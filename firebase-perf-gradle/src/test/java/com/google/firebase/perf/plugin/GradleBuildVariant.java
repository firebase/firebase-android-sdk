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

/**
 * Wrapper for all the build variants defined in the {@link GradleBuildRunner}.
 *
 * <pre>
 * We have 2 buildTypes:
 *   1. debug
 *   2. release
 *
 * And 4 productFlavors:
 *   flavorDimensions 'mode'
 *       |---- 1. demo
 *       `---- 2. full
 *
 *   flavorDimensions 'pricing'
 *       |---- 3. free
 *       `---- 4. paid
 * </pre>
 *
 * Gradle automatically creates build variants based on the defined build types and product flavors,
 * and names them according to <product-flavor><build-type> with camel casing.
 *
 * <p>Using the build configuration defined above as an example, Gradle creates a total of 18 build
 * variants with the following naming scheme:
 *
 * <p>Build variant: [demo, full][free, paid][debug, release]
 *
 * <p>Also when Gradle names each build variant, product flavors belonging to higher-priority flavor
 * dimension appear first, followed by those from lower-priority dimensions, followed by the build
 * type. In this case (if all flavors are declared) the order looks something like this:
 *
 * <pre>
 *      |---- demo
 *             |---- free --
 *             |            | --- [debug, release]
 *             `---- paid --
 *
 *      |---- full
 *             |---- free --
 *             |            | --- [debug, release]
 *             `---- paid --
 * </pre>
 *
 * <p>Refer: https://developer.android.com/studio/build/build-variants
 */
public enum GradleBuildVariant {
  ALL(),

  DEBUG("Debug"),
  RELEASE("Release"),

  DEMO_DEBUG("Demo", "Debug"),
  DEMO_RELEASE("Demo", "Release"),
  FULL_DEBUG("Full", "Debug"),
  FULL_RELEASE("Full", "Release"),

  FREE_DEBUG("Free", "Debug"),
  FREE_RELEASE("Free", "Release"),
  PAID_DEBUG("Paid", "Debug"),
  PAID_RELEASE("Paid", "Release"),

  DEMO_FREE_DEBUG("DemoFree", "Debug"),
  DEMO_FREE_RELEASE("DemoFree", "Release"),
  DEMO_PAID_DEBUG("DemoPaid", "Debug"),
  DEMO_PAID_RELEASE("DemoPaid", "Release"),

  FULL_FREE_DEBUG("FullFree", "Debug"),
  FULL_FREE_RELEASE("FullFree", "Release"),
  FULL_PAID_DEBUG("FullPaid", "Debug"),
  FULL_PAID_RELEASE("FullPaid", "Release");

  private String productFlavor;
  private String buildType;

  GradleBuildVariant() {
    this.productFlavor = "";
    this.buildType = "";
  }

  GradleBuildVariant(String buildType) {
    this.productFlavor = "";
    this.buildType = buildType;
  }

  GradleBuildVariant(String productFlavor, String buildType) {
    this.productFlavor = productFlavor;
    this.buildType = buildType;
  }

  /**
   * Returns the current build variant.
   *
   * <p>For e.g., Debug, FullRelease, DemoFreeDebug etc.
   */
  String getBuildVariant() {
    return productFlavor + buildType;
  }

  /**
   * Returns the current build variant name.
   *
   * <p>For e.g., debug, fullRelease, demoFreeDebug etc.
   */
  String getVariantName() {
    return (productFlavor.isEmpty()
        ? buildType.toLowerCase()
        : (productFlavor.substring(0, 1).toLowerCase() + productFlavor.substring(1)) + buildType);
  }

  /**
   * Returns the path substring for the current build variant where gradle generates the build
   * outputs.
   *
   * <pre>
   * For e.g.:
   *    For Debug           ----->       debug
   *    For FullRelease     ----->       full/release
   *    For DemoFreeDebug   ----->       demoFree/debug
   * </pre>
   */
  String getVariantPath() {
    return (productFlavor.isEmpty()
            ? ""
            : (productFlavor.substring(0, 1).toLowerCase() + productFlavor.substring(1) + "/"))
        + buildType.toLowerCase();
  }

  /**
   * Returns the build task associated with this variant.
   *
   * <pre>
   * For e.g.:
   *    For All             ----->       assemble
   *    For Debug           ----->       assembleDebug
   *    For FullRelease     ----->       assembleFullRelease
   *    For DemoFreeDebug   ----->       assembleDemoFreeDebug
   * </pre>
   */
  String getBuildTask() {
    return "assemble" + getBuildVariant();
  }
}
