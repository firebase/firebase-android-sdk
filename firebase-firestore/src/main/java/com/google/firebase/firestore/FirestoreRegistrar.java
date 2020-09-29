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

package com.google.firebase.firestore;

import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.RestrictTo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.firestore.remote.FirebaseClientGrpcMetadataProvider;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.Arrays;
import java.util.List;

/**
 * Registers {@link FirestoreMultiDbComponent}.
 *
 * @hide
 */
@Keep
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FirestoreRegistrar implements ComponentRegistrar {
  @Override
  @Keep
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirestoreMultiDbComponent.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(Context.class))
            .add(Dependency.optionalProvider(HeartBeatInfo.class))
            .add(Dependency.optionalProvider(UserAgentPublisher.class))
            .add(Dependency.optional(InternalAuthProvider.class))
            .add(Dependency.optional(FirebaseOptions.class))
            .factory(
                c ->
                    new FirestoreMultiDbComponent(
                        c.get(Context.class),
                        c.get(FirebaseApp.class),
                        c.get(InternalAuthProvider.class),
                        new FirebaseClientGrpcMetadataProvider(
                            c.getProvider(UserAgentPublisher.class),
                            c.getProvider(HeartBeatInfo.class),
                            c.get(FirebaseOptions.class))))
            .build(),
        LibraryVersionComponent.create("fire-fst", BuildConfig.VERSION_NAME));
  }
}
