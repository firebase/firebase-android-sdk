// Copyright 2021 Google LLC
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

package com.google.firebase.dynamiclinks.internal;

import androidx.annotation.Keep;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.dynamiclinks.BuildConfig;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * {@link ComponentRegistrar} for FirebaseDynamicLinks.
 *
 * <p>see go/firebase-components-android-integration-guide for more details
 *
 * @hide
 */
@KeepForSdk
@Keep
public final class FirebaseDynamicLinkRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-dl";

  @Override
  @Keep
  public List<Component<?>> getComponents() {
    Component<FirebaseDynamicLinks> firebaseDynamicLinks =
        Component.builder(FirebaseDynamicLinks.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.optionalProvider(AnalyticsConnector.class))
            .factory(
                container ->
                    new FirebaseDynamicLinksImpl(
                        container.get(FirebaseApp.class),
                        container.getProvider(AnalyticsConnector.class)))
            .build(); // no need for eager init for the Internal component.

    return Arrays.asList(
        firebaseDynamicLinks,
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
