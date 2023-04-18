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

package com.google.firebase.database;

import androidx.annotation.Keep;
import androidx.annotation.RestrictTo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/** @hide */
@Keep
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DatabaseRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-rtdb";

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseDatabaseComponent.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.deferred(InternalAuthProvider.class))
            .add(Dependency.deferred(InteropAppCheckTokenProvider.class))
            .factory(
                c ->
                    new FirebaseDatabaseComponent(
                        c.get(FirebaseApp.class),
                        c.getDeferred(InternalAuthProvider.class),
                        c.getDeferred(InteropAppCheckTokenProvider.class)))
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
