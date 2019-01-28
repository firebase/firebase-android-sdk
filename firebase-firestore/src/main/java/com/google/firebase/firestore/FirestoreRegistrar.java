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
import android.support.annotation.Keep;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.SDKVersionComponentFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registers {@link FirestoreMultiDbComponent}.
 *
 * @hide
 */
@Keep
public class FirestoreRegistrar implements ComponentRegistrar {
  @Override
  @Keep
  public List<Component<?>> getComponents() {
    return Arrays.asList(Component.builder(FirestoreMultiDbComponent.class)
        .add(Dependency.required(FirebaseApp.class))
        .add(Dependency.required(Context.class))
        .add(Dependency.optional(InternalAuthProvider.class))
        .factory(c -> new FirestoreMultiDbComponent(c.get(Context.class), c.get(FirebaseApp.class),
            c.get(InternalAuthProvider.class)))
        .build(), SDKVersionComponentFactory.createComponent("firebase-firestore", BuildConfig.VERSION_NAME));
  }
}
