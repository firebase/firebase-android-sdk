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

package com.google.firebase.inappmessaging.internal.injection.modules;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.events.Subscriber;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.inappmessaging.internal.ApiClient;
import com.google.firebase.inappmessaging.internal.DataCollectionHelper;
import com.google.firebase.inappmessaging.internal.GrpcClient;
import com.google.firebase.inappmessaging.internal.ProviderInstaller;
import com.google.firebase.inappmessaging.internal.SharedPreferencesUtils;
import com.google.firebase.inappmessaging.internal.TestDeviceHelper;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.internal.time.Clock;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

/**
 * Provider for ApiClient
 *
 * @hide
 */
@Module
public class ApiClientModule {
  private final FirebaseApp firebaseApp;
  private final FirebaseInstanceId firebaseInstanceId;
  private final Clock clock;

  public ApiClientModule(FirebaseApp firebaseApp, FirebaseInstanceId instanceId, Clock clock) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstanceId = instanceId;
    this.clock = clock;
  }

  @Provides
  FirebaseInstanceId providesFirebaseInstanceId() {
    return firebaseInstanceId;
  }

  @Provides
  FirebaseApp providesFirebaseApp() {
    return firebaseApp;
  }

  @Provides
  SharedPreferencesUtils providesSharedPreferencesUtils() {
    return new SharedPreferencesUtils(firebaseApp);
  }

  @Provides
  DataCollectionHelper providesDataCollectionHelper(
      SharedPreferencesUtils sharedPreferencesUtils, Subscriber firebaseEventSubscriber) {
    return new DataCollectionHelper(
        firebaseApp, sharedPreferencesUtils, firebaseInstanceId, firebaseEventSubscriber);
  }

  @Provides
  TestDeviceHelper providesTestDeviceHelper(SharedPreferencesUtils sharedPreferencesUtils) {
    return new TestDeviceHelper(sharedPreferencesUtils);
  }

  @Provides
  @FirebaseAppScope
  ApiClient providesApiClient(
      Lazy<GrpcClient> grpcClient,
      Application application,
      DataCollectionHelper dataCollectionHelper,
      ProviderInstaller providerInstaller) {
    return new ApiClient(
        grpcClient,
        firebaseApp,
        application,
        clock,
        providerInstaller);
  }
}
