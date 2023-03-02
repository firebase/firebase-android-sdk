// Copyright 2022 Google LLC
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

@file:JvmName("CheckHeadDependency")

package com.google.firebase.gradle.plugins

import java.util.stream.Collectors
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

fun getHeadDependencies(pomNodeList: NodeList): MutableSet<String> {
  val headDependencies: MutableSet<String> = HashSet()
  for (i in 0..pomNodeList.length - 1) {
    val node: Node = pomNodeList.item(i)
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      val element = node as Element
      val artifact = element.getElementsByTagName("artifactId").item(0).getTextContent()
      val groupId = element.getElementsByTagName("groupId").item(0).getTextContent()
      val version = element.getElementsByTagName("version").item(0).getTextContent()
      val gMavenHelper = GmavenHelper(groupId, artifact)
      if (version > gMavenHelper.getLatestReleasedVersion()) {
        println(artifact)
        headDependencies.add(artifact)
      }
    }
  }
  return headDependencies
}

fun check(projectsToPublish: Set<FirebaseLibraryExtension>, allFirebaseProjects: Set<String>) {
  val projectsReleasing: MutableSet<String> =
    projectsToPublish.map { it.artifactId.get() }.toSet() as MutableSet<String>
  var allProjectsToRelease: MutableSet<String> = mutableSetOf()
  projectsToPublish.forEach {
    val projectDependencies: Set<String> =
      it.project
        .getConfigurations()
        .getByName(it.getRuntimeClasspath())
        .dependencies
        .stream()
        .filter({ dep: Dependency? -> dep is ProjectDependency })
        .map { it.name }
        .collect(Collectors.toSet())
    allProjectsToRelease.addAll(projectDependencies)
  }

  allProjectsToRelease = (allProjectsToRelease intersect allFirebaseProjects) as MutableSet<String>
  val projectsToRelease: Set<String> = (allProjectsToRelease subtract projectsReleasing)
  if (projectsToRelease.isNotEmpty()) {
    throw GradleException(
      "Following Sdks have to release as well. Please update the release config.\n${projectsToRelease.joinToString("\n")}"
    )
  }
}
