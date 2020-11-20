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

package com.google.firebase.platforminfo;

import android.content.Context;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;

/** Factory to create a component that publishes the version of an SDK */
public class LibraryVersionComponent {
  private LibraryVersionComponent() {}

  public interface VersionExtractor<T> {
    String extract(T context);
  }

  /** Creates a component that publishes SDK versions */
  public static Component<?> create(String sdkName, String version) {
    return Component.intoSet(LibraryVersion.create(sdkName, version), LibraryVersion.class);
  }

  public static Component<?> fromContext(String sdkName, VersionExtractor<Context> extractor) {
    return Component.intoSetBuilder(LibraryVersion.class)
        .add(Dependency.required(Context.class))
        .factory(c -> LibraryVersion.create(sdkName, extractor.extract(c.get(Context.class))))
        .build();
  }
}
