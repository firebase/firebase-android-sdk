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

package com.google.firebase.crashlytics.internal.breadcrumbs;

import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;

/** BreadcrumbSource for use when listening for breadcrumb events is disabled. */
public class DisabledBreadcrumbSource implements BreadcrumbSource {

  @Override
  public void registerBreadcrumbHandler(@Nullable BreadcrumbHandler breadcrumbHandler) {
    Logger.getLogger().d("Could not register handler for breadcrumbs events.");
  }
}
