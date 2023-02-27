// Copyright 2023 Google LLC
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

import java.io.File
import java.net.URL
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

abstract class PomValidator : DefaultTask() {
  @get:InputFile abstract val pomFilePath: Property<File>
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>

  @TaskAction
  fun run() {
    val gMavenHelper = GmavenHelper(groupId.get(), artifactId.get())
    val latestReleasedVersion = gMavenHelper.getLatestReleasedVersion()
    val releasedVersionPomUrl = gMavenHelper.getPomFileForVersion(latestReleasedVersion)
    val output = diffWithPomFileUrl(releasedVersionPomUrl)
    println(output)
  }

  fun diffWithPomFileUrl(pomUrl: String): String {
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    val oldPomBuilder: DocumentBuilder = factory.newDocumentBuilder()
    val oldPomDoc: Document = oldPomBuilder.parse(URL(pomUrl).openStream())
    val currentPomBuilder: DocumentBuilder = factory.newDocumentBuilder()
    val currentPomDoc: Document = currentPomBuilder.parse(pomFilePath.get())
    val oldPomMap = mutableMapOf<String, String>()
    val currentPomMap = mutableMapOf<String, String>()
    val oldPomNodeList: NodeList = oldPomDoc.getElementsByTagName("dependency")
    val currentPomNodeList: NodeList = currentPomDoc.getElementsByTagName("dependency")
    for (i in oldPomNodeList.length - 1 downTo 0) {
      val node: Node = oldPomNodeList.item(i)
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        val element = node as Element
        val artifact = element.getElementsByTagName("artifactId").item(0).getTextContent()
        val version = element.getElementsByTagName("version").item(0).getTextContent()
        oldPomMap[artifact] = version
      }
    }

    for (i in currentPomNodeList.length - 1 downTo 0) {
      val node: Node = currentPomNodeList.item(i)
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        val element = node as Element
        val artifact = element.getElementsByTagName("artifactId").item(0).getTextContent()
        val version = element.getElementsByTagName("version").item(0).getTextContent()
        currentPomMap[artifact] = version
      }
    }
    val outputList = ArrayList<String>()
    for (entry in currentPomMap.entries.iterator()) {
      val curDepVersion = entry.value.replace("-SNAPSHOT", "").trim()
      val oldDepVersion = oldPomMap.get(entry.key)
      if (oldDepVersion == null) {
        continue
      }
      if (oldDepVersion.trim() > curDepVersion) {
        val outputString =
          "Artifact " +
            entry.key +
            " has been degraded to " +
            curDepVersion +
            " from " +
            oldDepVersion
        outputList.add(outputString)
      }
    }
    if (outputList.isEmpty()) {
      return ""
    } else {
      outputList.add("Please have a look at the above errors and fix them.")
      return outputList.joinToString("\n")
    }
  }
}
