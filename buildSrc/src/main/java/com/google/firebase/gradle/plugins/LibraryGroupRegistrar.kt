// Copyright 2021 Google LLC
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
package com.google.firebase.gradle.plugins

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimaps

class LibraryGroupRegistrar {
  private val librariesByGroup =
    Multimaps.synchronizedSetMultimap(HashMultimap.create<String, FirebaseLibraryExtension>())

  fun registerLibrary(libraryGroup: String, project: FirebaseLibraryExtension) {
    librariesByGroup.put(libraryGroup, project)
    val maxVersion =
      librariesByGroup
        .get(libraryGroup)
        .map { it.project.version.toString() }
        .mapNotNull { ModuleVersion.fromStringOrNull(it) }
        .maxOrNull()
    if (maxVersion != null) {
      librariesByGroup
        .get(libraryGroup)
        .filter { ModuleVersion.fromStringOrNull(it.project.version.toString()) == null }
        .forEach { it.project.version = maxVersion.toString() }
    }
  }

  fun getLibrariesForGroup(name: String) = librariesByGroup.get(name)

  companion object {
    private val instance = LibraryGroupRegistrar()

    @JvmStatic public fun getInstance() = instance
  }
}
