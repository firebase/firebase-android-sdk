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

import java.io.File
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.w3c.dom.Document
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
      val version = element.getElementsByTagName("version").item(0).getTextContent()
      print("${artifact} - ${version}")
      if (version.contains("-SNAPSHOT")) {
        headDependencies.add(artifact)
      }
    }
  }
  return headDependencies
}

fun check(projectsToPublish: Set<FirebaseLibraryExtension>) {
  val allProjectsToRelease: MutableSet<String> = HashSet()
  projectsToPublish.forEach {
    val pomPath = "${it.project.buildDir}/publications/mavenAar/pom-default.xml"
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    val builder: DocumentBuilder = factory.newDocumentBuilder()
    val doc: Document = builder.parse(File(pomPath))
    allProjectsToRelease.addAll(getHeadDependencies(doc.getElementsByTagName("dependency")))
  }
  val projectsReleasing = projectsToPublish.map { it.artifactId.get() }
  println(projectsReleasing)
  println(allProjectsToRelease)
  val projectsToRelease = allProjectsToRelease subtract projectsReleasing
  if (projectsToRelease.isNotEmpty()) {
    throw GradleException(
      "${projectsToRelease.toString()} have to release as well. Please update the release config\n"
    )
  }
}
