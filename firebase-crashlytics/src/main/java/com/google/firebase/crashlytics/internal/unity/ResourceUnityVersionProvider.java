// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.unity;

import android.content.Context;
import com.google.firebase.crashlytics.internal.common.CommonUtils;

public class ResourceUnityVersionProvider implements UnityVersionProvider {

  private final Context context;

  private boolean hasRead = false;
  private String unityVersion;

  public ResourceUnityVersionProvider(Context context) {
    this.context = context;
  }

  @Override
  public String getUnityVersion() {
    if (!hasRead) {
      unityVersion = CommonUtils.resolveUnityEditorVersion(context);
      hasRead = true;
    }
    if (unityVersion != null) {
      return unityVersion;
    }
    return null;
  }
}
