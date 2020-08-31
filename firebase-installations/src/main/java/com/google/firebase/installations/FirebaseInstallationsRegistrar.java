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

package com.google.firebase.installations;

import androidx.annotation.Keep;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.Arrays;
import java.util.List;

/** @hide */
@Keep
public class FirebaseInstallationsRegistrar implements ComponentRegistrar {

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseInstallationsApi.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.optionalProvider(HeartBeatInfo.class))
            .add(Dependency.optionalProvider(UserAgentPublisher.class))
            .factory(
                c ->
                    new FirebaseInstallations(
                        c.get(FirebaseApp.class),
                        c.getProvider(UserAgentPublisher.class),
                        c.getProvider(HeartBeatInfo.class)))
            .build(),
        LibraryVersionComponent.create("fire-installations", BuildConfig.VERSION_NAME));
  }
}
