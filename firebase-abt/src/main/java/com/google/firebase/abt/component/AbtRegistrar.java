// Copyright 2018 Google LLC
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

package com.google.firebase.abt.component;

import android.content.Context;
import androidx.annotation.Keep;
import com.google.firebase.abt.BuildConfig;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Registrar for setting up Firebase ABT's dependency injections in Firebase Android Components.
 *
 * @author Miraziz Yusupov
 */
@Keep
public class AbtRegistrar implements ComponentRegistrar {

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(AbtComponent.class)
            .add(Dependency.required(Context.class))
            .add(Dependency.optional(AnalyticsConnector.class))
            .factory(
                container ->
                    new AbtComponent(
                        container.get(Context.class), container.get(AnalyticsConnector.class)))
            .build(),
        LibraryVersionComponent.create("fire-abt", BuildConfig.VERSION_NAME));
  }
}
