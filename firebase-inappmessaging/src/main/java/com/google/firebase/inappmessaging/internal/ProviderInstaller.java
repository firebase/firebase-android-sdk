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

package com.google.firebase.inappmessaging.internal;

import android.app.Application;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProviderInstaller {
  private final Application application;

  @Inject
  ProviderInstaller(Application application) {
    this.application = application;
  }

  public void install() {
    try {
      com.google.android.gms.security.ProviderInstaller.installIfNeeded(application);
    } catch (GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
      e.printStackTrace();
    }
  }
}
