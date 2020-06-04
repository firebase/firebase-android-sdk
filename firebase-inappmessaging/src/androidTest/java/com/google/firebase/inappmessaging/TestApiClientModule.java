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
import com.google.firebase.FirebaseApp;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inappmessaging.internal.ApiClient;
import com.google.firebase.inappmessaging.internal.DataCollectionHelper;
import com.google.firebase.inappmessaging.internal.GrpcClient;
import com.google.firebase.inappmessaging.internal.ProviderInstaller;
import com.google.firebase.inappmessaging.internal.SharedPreferencesUtils;
import com.google.firebase.inappmessaging.internal.TestDeviceHelper;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.installations.FirebaseInstallationsApi;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/** Test bindings for API client */
@Module
public class TestApiClientModule {

  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private SharedPreferencesUtils sharedPreferencesUtils;
  private TestDeviceHelper testDeviceHelper;
  private Clock clock;

  public TestApiClientModule(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      TestDeviceHelper testDeviceHelper,
      Clock clock) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstallations = firebaseInstallations;
    this.testDeviceHelper = testDeviceHelper;
    this.sharedPreferencesUtils = new SharedPreferencesUtils(firebaseApp);
    this.clock = clock;
  }

  @Provides
  FirebaseInstallationsApi providesFirebaseInstallations() {
    return firebaseInstallations;
  }

  @Provides
  @Singleton
  public FirebaseApp providesfirebaseApp() {
    return firebaseApp;
  }

  @Provides
  DataCollectionHelper providesDataCollectionHelper(Subscriber firebaseEventSubscriber) {
    return new DataCollectionHelper(firebaseApp, sharedPreferencesUtils, firebaseEventSubscriber);
  }

  @Provides
  TestDeviceHelper providesTestDeviceHelper() {
    return this.testDeviceHelper;
  }

  @Provides
  @FirebaseAppScope
  ApiClient providesApiClient(
      Lazy<GrpcClient> grpcClient, Application application, ProviderInstaller providerInstaller) {
    return new ApiClient(grpcClient, firebaseApp, application, clock, providerInstaller);
  }
}
