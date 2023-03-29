package com.google.firebase.gradle.plugins

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimaps

class LibraryGroupRegistrar {
  private val librariesByGroup =
    Multimaps.synchronizedSetMultimap(HashMultimap.create<String, FirebaseLibraryExtension>())

  fun registerLibrary(libraryGroup: String, project: FirebaseLibraryExtension) =
    librariesByGroup.put(libraryGroup, project)

  fun getLibrariesForGroup(name: String) = librariesByGroup.get(name)

  companion object {
    private val instance = LibraryGroupRegistrar()

    @JvmStatic public fun getInstance() = instance
  }
}
