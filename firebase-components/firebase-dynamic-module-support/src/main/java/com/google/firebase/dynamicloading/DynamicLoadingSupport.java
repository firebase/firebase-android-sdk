// Copyright 2020 Google LLC
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

package com.google.firebase.dynamicloading;

import android.content.Context;
import com.google.android.play.core.splitinstall.InternalFrameworkListenerExtensions;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

/** Registers a {@link SplitInstallStateUpdatedListener} to trigger component discovery. */
class DynamicLoadingSupport implements SplitInstallStateUpdatedListener {
  private final ComponentLoader loader;

  DynamicLoadingSupport(Context applicationContext, ComponentLoader loader) {
    this.loader = loader;
    InternalFrameworkListenerExtensions.registerFrameworkListener(applicationContext, this);
  }

  @Override
  public void onStateUpdate(SplitInstallSessionState state) {
    if (state.status() == SplitInstallSessionStatus.INSTALLED) {
      loader.discoverComponents();
    }
  }
}
