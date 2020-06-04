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

package com.google.firebase.inappmessaging.internal.injection.components;

import com.google.android.datatransport.TransportFactory;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.inappmessaging.internal.AbtIntegrationHelper;
import com.google.firebase.inappmessaging.internal.DisplayCallbacksFactory;
import com.google.firebase.inappmessaging.internal.injection.modules.ApiClientModule;
import com.google.firebase.inappmessaging.internal.injection.modules.GrpcClientModule;
import com.google.firebase.inappmessaging.internal.injection.modules.TransportClientModule;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import dagger.BindsInstance;
import dagger.Component;

/**
 * Dagger component to create FirebaseInAppMessaging Objects. One component is created per firebase
 * app found on the client.
 *
 * @hide
 */
@FirebaseAppScope
@Component(
    dependencies = {UniversalComponent.class},
    modules = {ApiClientModule.class, GrpcClientModule.class, TransportClientModule.class})
public interface AppComponent {
  FirebaseInAppMessaging providesFirebaseInAppMessaging();

  DisplayCallbacksFactory displayCallbacksFactory();

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder abtIntegrationHelper(AbtIntegrationHelper integrationHelper);

    Builder apiClientModule(ApiClientModule module);

    Builder grpcClientModule(GrpcClientModule module);

    Builder universalComponent(UniversalComponent component);

    @BindsInstance
    Builder transportFactory(TransportFactory transportFactory);

    AppComponent build();
  }
}
