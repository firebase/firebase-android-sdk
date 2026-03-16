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

import com.android.build.api.variant.VariantExtension;
import com.android.build.api.variant.VariantExtensionConfig;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.provider.Property;

@SuppressWarnings("UnstableApiUsage")
public abstract class FirebasePerfVariantExtension implements VariantExtension, Serializable {
  abstract Property<Boolean> getInstrumentationEnabled();

  @Inject
  public FirebasePerfVariantExtension(VariantExtensionConfig<?> config) {
    FirebasePerfExtension buildTypeExtension =
        config.buildTypeExtension(FirebasePerfExtension.class);
    List<FirebasePerfExtension> productFlavorsExtensions =
        config.productFlavorsExtensions(FirebasePerfExtension.class);

    for (FirebasePerfExtension e :
        listToIterateOver(productFlavorsExtensions, buildTypeExtension)) {
      e.isInstrumentationEnabled().ifPresent(value -> getInstrumentationEnabled().set(value));
    }
  }

  private List<FirebasePerfExtension> listToIterateOver(
      List<FirebasePerfExtension> productFlavorExtensions,
      FirebasePerfExtension buildTypeExtension) {
    // This reverses the list of productFlavorsExtensions and appends the buildType value
    // to give the buildType flag the highest priority.
    List<FirebasePerfExtension> result = new ArrayList<>(productFlavorExtensions);
    Collections.reverse(result);
    result.add(buildTypeExtension);
    return result;
  }
}
