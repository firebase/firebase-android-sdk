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

package com.google.firebase.gradle.bomgenerator;

import com.google.firebase.gradle.bomgenerator.model.Dependency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class PomXmlWriter {
  private static final String ARTIFACT_GROUP_ID = "com.google.firebase";
  private static final String ARTIFACT_ARTIFACT_ID = "firebase-bom";
  private final List<Dependency> firebaseDependencies;
  private final String version;
  private final Path rootDir;

  public PomXmlWriter(List<Dependency> firebaseDependencies, String version, Path rootDir) {
    this.firebaseDependencies = firebaseDependencies;
    this.version = version;
    this.rootDir = rootDir;
  }

  public Document generatePomXml()
      throws ParserConfigurationException, VersionRangeResolutionException {
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

    Element project = doc.createElement("project");
    project.setAttribute("xmlns", "http://maven.apache.org/POM/4.0.0");
    project.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    project.setAttribute(
        "xsi:schemaLocation",
        "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd");
    doc.appendChild(project);

    createAndAppendSimpleElement("modelVersion", "4.0.0", project, doc);
    createAndAppendSimpleElement("groupId", ARTIFACT_GROUP_ID, project, doc);
    createAndAppendSimpleElement("artifactId", ARTIFACT_ARTIFACT_ID, project, doc);
    createAndAppendSimpleElement("version", getVersion(), project, doc);
    createAndAppendSimpleElement("packaging", "pom", project, doc);

    Element licenses = createLicense(doc);
    project.appendChild(licenses);

    Element dependencyManagement = doc.createElement("dependencyManagement");
    project.appendChild(dependencyManagement);

    Element dependencies = doc.createElement("dependencies");
    dependencyManagement.appendChild(dependencies);

    for (Dependency dep : firebaseDependencies) {
      Element depXml = dependencyToMavenXmlElement(dep, doc);
      dependencies.appendChild(depXml);
    }

    return doc;
  }

  public void writeXmlDocument(Document document) throws IOException, TransformerException {

    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

    DOMSource source = new DOMSource(document);
    Path outputDir = rootDir.resolve("com/google/firebase/firebase-bom/" + version + "/");
    Files.createDirectories(outputDir);
    Path pom = outputDir.resolve("firebase-bom-" + version + ".pom");
    StreamResult file = new StreamResult(pom.toFile());
    transformer.transform(source, file);
  }

  public String getVersion() {
    return version;
  }

  private static void createAndAppendSimpleElement(
      String key, String value, Element toAppendTo, Document doc) {
    Element element = doc.createElement(key);
    element.appendChild(doc.createTextNode(value));
    toAppendTo.appendChild(element);
  }

  private static Element createLicense(Document doc) {
    Element licenses = doc.createElement("licenses");
    Element license = doc.createElement("license");
    createAndAppendSimpleElement("name", "The Apache Software License, Version 2.0", license, doc);
    createAndAppendSimpleElement(
        "url", "http://www.apache.org/licenses/LICENSE-2.0.txt", license, doc);
    createAndAppendSimpleElement("distribution", "repo", license, doc);
    licenses.appendChild(license);
    return licenses;
  }

  public Element dependencyToMavenXmlElement(Dependency dep, Document doc) {
    Element dependencyElement = doc.createElement("dependency");

    Element groupId = doc.createElement("groupId");
    groupId.appendChild(doc.createTextNode(dep.groupId()));

    Element artifactId = doc.createElement("artifactId");
    artifactId.appendChild(doc.createTextNode(dep.artifactId()));

    Element version = doc.createElement("version");
    version.appendChild(doc.createTextNode(dep.version()));

    dependencyElement.appendChild(groupId);
    dependencyElement.appendChild(artifactId);
    dependencyElement.appendChild(version);

    return dependencyElement;
  }
}
