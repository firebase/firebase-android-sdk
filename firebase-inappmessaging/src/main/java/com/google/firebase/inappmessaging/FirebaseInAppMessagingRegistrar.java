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

package com.google.firebase.inappmessaging;

import android.app.Application;
import android.content.Context;
import androidx.annotation.Keep;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.abt.component.AbtComponent;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inappmessaging.internal.AbtIntegrationHelper;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.injection.components.AppComponent;
import com.google.firebase.inappmessaging.internal.injection.components.DaggerAppComponent;
import com.google.firebase.inappmessaging.internal.injection.components.DaggerUniversalComponent;
import com.google.firebase.inappmessaging.internal.injection.components.UniversalComponent;
import com.google.firebase.inappmessaging.internal.injection.modules.AnalyticsEventsModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ApiClientModule;
import com.google.firebase.inappmessaging.internal.injection.modules.AppMeasurementModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ApplicationModule;
import com.google.firebase.inappmessaging.internal.injection.modules.GrpcClientModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ProgrammaticContextualTriggerFlowableModule;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Registers {@link FirebaseInAppMessaging}.
 *
 * @hide
 */
@Keep
public class FirebaseInAppMessagingRegistrar implements ComponentRegistrar {
  @Override
  @Keep
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseInAppMessaging.class)
            .add(Dependency.required(Context.class))
            .add(Dependency.required(FirebaseInstallationsApi.class))
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(AbtComponent.class))
            .add(Dependency.optional(AnalyticsConnector.class))
            .add(Dependency.required(TransportFactory.class))
            .add(Dependency.required(Subscriber.class))
            .factory(this::providesFirebaseInAppMessaging)
            .eagerInDefaultApp()
            .build(),
        LibraryVersionComponent.create("fire-fiam", BuildConfig.VERSION_NAME));
  }

  private FirebaseInAppMessaging providesFirebaseInAppMessaging(ComponentContainer container) {
    FirebaseApp firebaseApp = container.get(FirebaseApp.class);
    FirebaseInstallationsApi firebaseInstallations = container.get(FirebaseInstallationsApi.class);
    AnalyticsConnector analyticsConnector = container.get(AnalyticsConnector.class);
    Subscriber firebaseEventsSubscriber = container.get(Subscriber.class);

    Application application = (Application) firebaseApp.getApplicationContext();

    UniversalComponent universalComponent =
        DaggerUniversalComponent.builder()
            .applicationModule(new ApplicationModule(application))
            .appMeasurementModule(
                new AppMeasurementModule(analyticsConnector, firebaseEventsSubscriber))
            .analyticsEventsModule(new AnalyticsEventsModule())
            .programmaticContextualTriggerFlowableModule(
                new ProgrammaticContextualTriggerFlowableModule(
                    new ProgramaticContextualTriggers()))
            .build();

    AppComponent instance =
        DaggerAppComponent.builder()
            .abtIntegrationHelper(
                new AbtIntegrationHelper(
                    container
                        .get(AbtComponent.class)
                        .get(FirebaseABTesting.OriginService.INAPP_MESSAGING)))
            .apiClientModule(
                new ApiClientModule(firebaseApp, firebaseInstallations, universalComponent.clock()))
            .grpcClientModule(new GrpcClientModule(firebaseApp))
            .universalComponent(universalComponent)
            .transportFactory(container.get(TransportFactory.class))
            .build();

    return instance.providesFirebaseInAppMessaging();
  }
}
