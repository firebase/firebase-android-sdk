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

package com.google.firebase.inappmessaging.display.internal;

import android.app.Application;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.BindingWrapper;
import com.google.firebase.inappmessaging.display.internal.injection.components.DaggerInAppMessageComponent;
import com.google.firebase.inappmessaging.display.internal.injection.components.InAppMessageComponent;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterModule;
import com.google.firebase.inappmessaging.model.InAppMessage;
import javax.inject.Inject;
import javax.inject.Singleton;

/** @hide */
@Singleton
public class BindingWrapperFactory {

  private final Application application;

  @Inject
  BindingWrapperFactory(Application application) {
    this.application = application;
  }

  public BindingWrapper createImageBindingWrapper(
      InAppMessageLayoutConfig config, InAppMessage inAppMessage) {
    InAppMessageComponent inAppMessageComponent =
        DaggerInAppMessageComponent.builder()
            .inflaterModule(new InflaterModule(inAppMessage, config, application))
            .build();
    return inAppMessageComponent.imageBindingWrapper();
  }

  public BindingWrapper createModalBindingWrapper(
      InAppMessageLayoutConfig config, InAppMessage inAppMessage) {
    InAppMessageComponent inAppMessageComponent =
        DaggerInAppMessageComponent.builder()
            .inflaterModule(new InflaterModule(inAppMessage, config, application))
            .build();
    return inAppMessageComponent.modalBindingWrapper();
  }

  public BindingWrapper createBannerBindingWrapper(
      InAppMessageLayoutConfig config, InAppMessage inAppMessage) {
    InAppMessageComponent inAppMessageComponent =
        DaggerInAppMessageComponent.builder()
            .inflaterModule(new InflaterModule(inAppMessage, config, application))
            .build();
    return inAppMessageComponent.bannerBindingWrapper();
  }

  public BindingWrapper createCardBindingWrapper(
      InAppMessageLayoutConfig config, InAppMessage inAppMessage) {
    InAppMessageComponent inAppMessageComponent =
        DaggerInAppMessageComponent.builder()
            .inflaterModule(new InflaterModule(inAppMessage, config, application))
            .build();
    return inAppMessageComponent.cardBindingWrapper();
  }
}
