/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.gradle.plugins

import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

/** TODO(b/279466888) - Make GmavenHelper testable */
class GmavenHelper(val groupId: String, val artifactId: String) {
  val GMAVEN_ROOT = "https://dl.google.com/dl/android/maven2"

  fun getPomFileForVersion(version: String): String {
    val pomFileName = "${artifactId}-${version}.pom"
    val groupIdAsPath = groupId.replace(".", "/")
    return "${GMAVEN_ROOT}/${groupIdAsPath}/${artifactId}/${version}/${pomFileName}"
  }

  fun isPresentInGmaven(): Boolean {
    val groupIdAsPath = groupId.replace(".", "/")
    val u = URL("${GMAVEN_ROOT}/${groupIdAsPath}/${artifactId}/maven-metadata.xml")
    val huc: HttpURLConnection = u.openConnection() as HttpURLConnection
    huc.setRequestMethod("GET") // OR  huc.setRequestMethod ("HEAD");
    huc.connect()
    val code: Int = huc.getResponseCode()
    return code == HttpURLConnection.HTTP_OK
  }

  fun getArtifactForVersion(version: String, isJar: Boolean): String {
    val fileName =
      if (isJar == true) "${artifactId}-${version}.jar" else "${artifactId}-${version}.aar"
    val groupIdAsPath = groupId.replace(".", "/")
    return "${GMAVEN_ROOT}/${groupIdAsPath}/${artifactId}/${version}/${fileName}"
  }

  fun hasReleasedVersion(version: String): Boolean {
    val doc: Document? = getMavenMetadata()
    if (doc != null) {
      val versions = doc.getElementsByTagName("version")
      for (i in 0..versions.length - 1) {
        if (versions.item(i).textContent == version) {
          return true
        }
      }
    }
    return false
  }

  fun getLatestReleasedVersion(): String {
    val doc: Document? = getMavenMetadata()
    return doc?.getElementsByTagName("latest")?.item(0)?.getTextContent() ?: ""
  }

  fun getMavenMetadata(): Document? {
    val groupIdAsPath = groupId.replace(".", "/")
    val mavenMetadataUrl = "${GMAVEN_ROOT}/${groupIdAsPath}/${artifactId}/maven-metadata.xml"
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    val builder: DocumentBuilder = factory.newDocumentBuilder()
    try {
      val doc: Document = builder.parse(URL(mavenMetadataUrl).openStream())
      doc.documentElement.normalize()
      return doc
    } catch (e: FileNotFoundException) {
      return null
    }
  }
}
