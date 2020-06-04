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

import com.google.firebase.inappmessaging.internal.injection.components.AppComponent;
import com.google.firebase.inappmessaging.internal.injection.components.UniversalComponent;
import com.google.firebase.inappmessaging.internal.injection.modules.GrpcClientModule;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import dagger.Component;

@FirebaseAppScope
@Component(
    dependencies = {UniversalComponent.class},
    modules = {
      // Test modules
      TestApiClientModule.class,
      TestEngagementMetricsLoggerClientModule.class,
      TestAbTestingModule.class,

      // Real modules
      GrpcClientModule.class
    })
public interface TestAppComponent extends AppComponent {}
