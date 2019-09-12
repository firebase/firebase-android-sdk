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

package com.google.firebase.inappmessaging.display;

import android.app.Application;
import androidx.annotation.Keep;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.inappmessaging.display.internal.injection.components.AppComponent;
import com.google.firebase.inappmessaging.display.internal.injection.components.DaggerAppComponent;
import com.google.firebase.inappmessaging.display.internal.injection.components.DaggerUniversalComponent;
import com.google.firebase.inappmessaging.display.internal.injection.components.UniversalComponent;
import com.google.firebase.inappmessaging.display.internal.injection.modules.ApplicationModule;
import com.google.firebase.inappmessaging.display.internal.injection.modules.HeadlessInAppMessagingModule;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Registers {@link FirebaseInAppMessagingDisplay}.
 *
 * @hide
 */
@Keep
public class FirebaseInAppMessagingDisplayRegistrar implements ComponentRegistrar {
  @Override
  @Keep
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseInAppMessagingDisplay.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(AnalyticsConnector.class))
            .add(Dependency.required(FirebaseInAppMessaging.class))
            .factory(this::buildFirebaseInAppMessagingUI)
            .eagerInDefaultApp()
            .build(),
        LibraryVersionComponent.create("fire-fiamd", BuildConfig.VERSION_NAME));
  }

  private FirebaseInAppMessagingDisplay buildFirebaseInAppMessagingUI(
      ComponentContainer container) {
    FirebaseApp firebaseApp = FirebaseApp.getInstance();
    FirebaseInAppMessaging headless = container.get(FirebaseInAppMessaging.class);
    Application firebaseApplication = (Application) firebaseApp.getApplicationContext();

    UniversalComponent universalComponent =
        DaggerUniversalComponent.builder()
            .applicationModule(new ApplicationModule(firebaseApplication))
            .build();
    AppComponent instance =
        DaggerAppComponent.builder()
            .universalComponent(universalComponent)
            .headlessInAppMessagingModule(new HeadlessInAppMessagingModule(headless))
            .build();

    FirebaseInAppMessagingDisplay firebaseInAppMessagingDisplay =
        instance.providesFirebaseInAppMessagingUI();
    firebaseApplication.registerActivityLifecycleCallbacks(firebaseInAppMessagingDisplay);
    return firebaseInAppMessagingDisplay;
  }
}
