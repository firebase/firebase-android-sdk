package com.google.firebase.gradle.plugins

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimaps

public class LibraryGroupRegistrar {
  private val librariesByGroup =
    Multimaps.synchronizedSetMultimap(HashMultimap.create<String, FirebaseLibraryExtension>())

  public fun addLibrary(libraryGroup: String, project: FirebaseLibraryExtension) =
    librariesByGroup.put(libraryGroup, project)

  public fun getLibrariesForGroup(name: String): Set<FirebaseLibraryExtension> {
    return librariesByGroup.get(name)
  }
  companion object {
    private val instance = LibraryGroupRegistrar()

    public fun getInstance() = instance
  }
}
