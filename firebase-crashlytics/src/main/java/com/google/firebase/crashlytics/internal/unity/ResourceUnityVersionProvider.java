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
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;

public class ResourceUnityVersionProvider implements UnityVersionProvider {

  private static final String UNITY_EDITOR_VERSION =
      "com.google.firebase.crashlytics.unity_version";

  private static boolean isUnityVersionSet = false;
  private static String unityVersion = null;

  private final Context context;

  /**
   * @return the Unity Editor version resolved from String resources, or <code>null</code> if the
   *     value was not present. This method can be invoked directly; access via the instance method
   *     from UnityVersionProvider is provided to support mocking while testing.
   */
  public static synchronized String resolveUnityEditorVersion(Context context) {
    if (isUnityVersionSet) {
      return unityVersion;
    }
    final int id = CommonUtils.getResourcesIdentifier(context, UNITY_EDITOR_VERSION, "string");
    if (id != 0) {
      unityVersion = context.getResources().getString(id);
      isUnityVersionSet = true;
      Logger.getLogger().v("Unity Editor version is: " + unityVersion);
    }
    return unityVersion;
  }

  public ResourceUnityVersionProvider(Context context) {
    this.context = context;
  }

  @Override
  public String getUnityVersion() {
    return resolveUnityEditorVersion(this.context);
  }
}
