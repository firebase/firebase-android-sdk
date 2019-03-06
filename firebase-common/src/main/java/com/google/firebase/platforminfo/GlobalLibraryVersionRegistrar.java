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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * In order to allow the C++ and Unity SDKs to publish their versions without the use of the
 * components framework, we have a mechanism where the versions can be wired as out of band as side
 * effects. See {@link GlobalLibraryVersionRegistrar#registerVersion(String, String)}
 *
 * <p>Java libraries should use {@link LibraryVersionComponent#create(String, String)} instead.
 */
public class GlobalLibraryVersionRegistrar {
  private final Set<LibraryVersion> infos = new HashSet<>();
  private static volatile GlobalLibraryVersionRegistrar INSTANCE;

  GlobalLibraryVersionRegistrar() {}

  /**
   * Thread safe method to publish versions outside of the components mechanics.
   *
   * <p>It is the responsibility of the caller to register the version at app launch.
   */
  public void registerVersion(String sdkName, String version) {
    synchronized (infos) {
      infos.add(LibraryVersion.create(sdkName, version));
    }
  }

  /** Returns registered versions */
  Set<LibraryVersion> getRegisteredVersions() {
    synchronized (infos) {
      return Collections.unmodifiableSet(infos);
    }
  }

  /** Returns an instance of {@link GlobalLibraryVersionRegistrar} */
  public static GlobalLibraryVersionRegistrar getInstance() {
    GlobalLibraryVersionRegistrar localRef = INSTANCE;
    if (localRef == null) {
      synchronized (GlobalLibraryVersionRegistrar.class) {
        localRef = INSTANCE;
        if (localRef == null) {
          INSTANCE = localRef = new GlobalLibraryVersionRegistrar();
        }
      }
    }
    return localRef;
  }
}
