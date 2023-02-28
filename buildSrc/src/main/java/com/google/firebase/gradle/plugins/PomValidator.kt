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
import org.gradle.api.GradleException
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
    var output: String = diffWithPomFileUrl(releasedVersionPomUrl).trim()
    if (output.isNotEmpty()) {
      throw GradleException("${output}\nPlease fix the above errors")
    }
  }

  fun getMapFromXml(pomNodeList: NodeList): Map<String, String> {
    val pomMap = mutableMapOf<String, String>()
    for (i in 0..pomNodeList.length - 1) {
      val node: Node = pomNodeList.item(i)
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        val element = node as Element
        val artifact = element.getElementsByTagName("artifactId").item(0).getTextContent()
        val version = element.getElementsByTagName("version").item(0).getTextContent()
        pomMap[artifact] = version
      }
    }
    return pomMap
  }

  fun diffWithPomFileUrl(pomUrl: String): String {
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    val oldPomBuilder: DocumentBuilder = factory.newDocumentBuilder()
    val oldPomDoc: Document = oldPomBuilder.parse(URL(pomUrl).openStream())
    val currentPomBuilder: DocumentBuilder = factory.newDocumentBuilder()
    val currentPomDoc: Document = currentPomBuilder.parse(pomFilePath.get())
    val oldPomMap = getMapFromXml(oldPomDoc.getElementsByTagName("dependency"))
    val currentPomMap = getMapFromXml(currentPomDoc.getElementsByTagName("dependency"))

    return currentPomMap
      .filter {
        (oldPomMap.get(it.key) != null) && (oldPomMap.get(it.key)!!.trim()) > it.value.trim()
      }
      .map { "Artifacts ${it.key} has been degraded to ${it.value} from ${oldPomMap.get(it.key)}" }
      .joinToString("\n")
  }
}
