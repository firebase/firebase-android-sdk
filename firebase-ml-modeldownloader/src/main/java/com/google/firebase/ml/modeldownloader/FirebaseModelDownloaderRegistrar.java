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

package com.google.firebase.ml.modeldownloader;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Registrar for setting up Firebase ML Model Downloader's dependency injections in Firebase Android
 * Components.
 *
 * @hide
 */
public class FirebaseModelDownloaderRegistrar implements ComponentRegistrar {

  @Override
  @NonNull
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseModelDownloader.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(FirebaseOptions.class))
            .factory(c -> new FirebaseModelDownloader(c.get(FirebaseOptions.class)))
            .build(),
        LibraryVersionComponent.create("firebase-ml-modeldownloader", BuildConfig.VERSION_NAME));
  }
}
