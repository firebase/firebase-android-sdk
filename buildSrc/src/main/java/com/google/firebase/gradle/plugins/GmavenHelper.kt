package com.google.firebase.gradle.plugins

import java.net.URL
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

class GmavenHelper(val groupId: String, val artifactId: String) {
  val GMAVEN_ROOT = "https://dl.google.com/dl/android/maven2/"

  fun getPomFileForVersion(version: String): String {
    val pomFileName = artifactId + "-" + version + ".pom"
    return GMAVEN_ROOT +
      groupId.replace(".", "/") +
      "/" +
      artifactId +
      "/" +
      version +
      "/" +
      pomFileName
  }

  fun getLatestReleasedVersion(): String {
    val mavenMetadataUrl =
      GMAVEN_ROOT + groupId.replace(".", "/") + "/" + artifactId + "/maven-metadata.xml"
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    val builder: DocumentBuilder = factory.newDocumentBuilder()
    val doc: Document = builder.parse(URL(mavenMetadataUrl).openStream())
    doc.documentElement.normalize()
    return doc.getElementsByTagName("latest").item(0).getTextContent()
  }
}
