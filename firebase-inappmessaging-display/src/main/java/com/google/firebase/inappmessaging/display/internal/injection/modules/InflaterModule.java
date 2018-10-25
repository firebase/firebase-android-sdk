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

package com.google.firebase.inappmessaging.display.internal.injection.modules;

import android.app.Application;
import android.content.Context;
import android.view.LayoutInflater;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.InAppMessageScope;
import com.google.firebase.inappmessaging.model.InAppMessage;
import dagger.Module;
import dagger.Provides;

/** @hide */
@Module
public class InflaterModule {
  private final InAppMessage inAppMessage;
  private final InAppMessageLayoutConfig inAppMessageLayoutConfig;
  private final Application application;

  public InflaterModule(
      InAppMessage inAppMessage,
      InAppMessageLayoutConfig inAppMessageLayoutConfig,
      Application application) {
    this.inAppMessage = inAppMessage;
    this.inAppMessageLayoutConfig = inAppMessageLayoutConfig;
    this.application = application;
  }

  @Provides
  @InAppMessageScope
  public LayoutInflater providesInflaterservice() {
    return (LayoutInflater) application.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  @Provides
  InAppMessage providesBannerMessage() {
    return inAppMessage;
  }

  @Provides
  @InAppMessageScope
  InAppMessageLayoutConfig inAppMessageLayoutConfig() {
    return inAppMessageLayoutConfig;
  }
}
