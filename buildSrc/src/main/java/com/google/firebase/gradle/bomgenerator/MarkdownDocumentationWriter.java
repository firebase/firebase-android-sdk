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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MarkdownDocumentationWriter {
  private final List<Dependency> firebaseDependencies;
  private final Map<String, String> previousBomVersions;
  private final String version;
  private final String previousVersion;

  public MarkdownDocumentationWriter(
      List<Dependency> firebaseDependencies,
      String version,
      Map<String, String> previousBomVersions,
      String previousVersion) {
    this.firebaseDependencies = firebaseDependencies;
    this.previousBomVersions = previousBomVersions;
    this.version = version;
    this.previousVersion = previousVersion;
  }

  public String generateDocumentation() {
    StringBuilder docBuilder = new StringBuilder();
    docBuilder.append(generateHeader(version));
    firebaseDependencies.stream()
        .sorted(Comparator.comparing(Dependency::toGradleString))
        .map(this::generateListEntry)
        .forEach(docBuilder::append);
    docBuilder.append(generateFooter());
    return docBuilder.toString();
  }

  public void writeDocumentation(String document) throws IOException {
    Files.write(
        new File("bomReleaseNotes.md").toPath(),
        Collections.singleton(document),
        StandardCharsets.UTF_8);
  }

  public String getVersion() {
    return version;
  }

  private String generateHeader(String version) {
    return "### {{firebase_bom_long}} ({{bill_of_materials}}) version "
        + version
        + " "
        + headingId()
        + "\n"
        + "{% comment %}\n"
        + "These library versions must be flat-typed, do not use variables.\n"
        + "The release note for this BoM version is a library-version snapshot.\n"
        + "{% endcomment %}\n"
        + "\n"
        + "<section class=\"expandable\">\n"
        + "  <p class=\"showalways\">\n"
        + "    Firebase Android SDKs mapped to this {{bom}} version</p>\n"
        + " <p>Libraries that were versioned with this release are in highlighted rows.\n"
        + "    <br>Refer to a library's release notes (on this page) for details about its\n"
        + "    changes.\n"
        + "  </p>\n"
        + " <table>\n"
        + "    <thead>\n"
        + "      <th>Artifact name</th>\n"
        + "      <th>Version mapped<br>to previous {{bom}} v"
        + previousVersion
        + "</th>\n"
        + "      <th>Version mapped<br>to this {{bom}} v"
        + version
        + "</th>\n"
        + "    </thead>\n"
        + "    <tbody>\n";
  }

  private String generateListEntry(Dependency dep) {
    String previousDepVersion =
        previousBomVersions.containsKey(dep.fullArtifactId())
            ? previousBomVersions.get(dep.fullArtifactId())
            : "N/A";
    boolean depChanged = !dep.version().equals(previousDepVersion);
    String boldOpenTag = depChanged ? "<b>" : "";
    String boldClosedTag = depChanged ? "</b>" : "";
    String tableStyle = depChanged ? " class=\"alt\"" : "";
    return "      <tr"
        + tableStyle
        + ">\n"
        + "        <td>"
        + boldOpenTag
        + dep.fullArtifactId()
        + boldClosedTag
        + "</td>\n"
        + "        <td>"
        + previousDepVersion
        + "</td>\n"
        + "        <td>"
        + boldOpenTag
        + dep.version()
        + boldClosedTag
        + "</td>\n"
        + "      </tr>\n";
  }

  private String generateFooter() {
    return "    </tbody>\n  </table>\n</section>\n";
  }

  private String headingId() {
    return "{: #bom_v" + version.replaceAll("\\.", "-") + "}";
  }
}
