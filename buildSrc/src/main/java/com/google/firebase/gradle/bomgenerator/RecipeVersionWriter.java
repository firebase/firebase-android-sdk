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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RecipeVersionWriter {
  private final List<Dependency> firebaseDependencies;

  public RecipeVersionWriter(List<Dependency> firebaseDependencies) {
    this.firebaseDependencies = firebaseDependencies;
  }

  public String generateVersionUpdate() {
    Map<String, Dependency> depsByArtifactId =
        firebaseDependencies.stream().collect(Collectors.toMap(Dependency::fullArtifactId, x -> x));
    StringBuilder outputBuilder = new StringBuilder();
    outputBuilder.append("<!DOCTYPE root [\n" + "  <!-- Common Firebase dependencies -->\n");
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Google Services Plugin",
            "google-services-plugin-class",
            "com.google.gms:google-services"));
    outputBuilder.append(
        "  <!ENTITY google-services-plugin \"com.google.gms.google-services\">\n"
            + "  <!ENTITY gradle-plugin-class \"com.android.tools.build:gradle:8.1.0\">\n");
    outputBuilder.append("\n");
    outputBuilder.append("  <!-- Firebase SDK libraries -->\n");
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Analytics",
            "analytics-dependency",
            "com.google.firebase:firebase-analytics"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Crashlytics",
            "crashlytics-dependency",
            "com.google.firebase:firebase-crashlytics"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Performance Monitoring",
            "perf-dependency",
            "com.google.firebase:firebase-perf"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Vertex AI for Firebase",
            "vertex-dependency",
            "com.google.firebase:firebase-vertexai"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Cloud Messaging",
            "messaging-dependency",
            "com.google.firebase:firebase-messaging"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Authentication",
            "auth-dependency",
            "com.google.firebase:firebase-auth"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Realtime Database",
            "database-dependency",
            "com.google.firebase:firebase-database"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Cloud Storage",
            "storage-dependency",
            "com.google.firebase:firebase-storage"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Remote Config",
            "remote-config-dependency",
            "com.google.firebase:firebase-config"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Admob",
            "ads-dependency",
            "com.google.android.gms:play-services-ads"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Cloud Firestore",
            "firestore-dependency",
            "com.google.firebase:firebase-firestore"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Firebase Functions",
            "functions-dependency",
            "com.google.firebase:firebase-functions"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "FIAM Display",
            "fiamd-dependency",
            "com.google.firebase:firebase-inappmessaging-display"));
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Firebase MLKit Vision",
            "ml-vision-dependency",
            "com.google.firebase:firebase-ml-vision"));
    outputBuilder.append("\n");
    outputBuilder.append("  <!-- Firebase Gradle plugins -->\n");
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "App Distribution",
            "appdistribution-plugin-class",
            "com.google.firebase:firebase-appdistribution-gradle"));
    outputBuilder.append(
        "  <!ENTITY appdistribution-plugin \"com.google.firebase.appdistribution\">\n\n");
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Crashlytics",
            "crashlytics-plugin-class",
            "com.google.firebase:firebase-crashlytics-gradle"));
    outputBuilder.append("  <!ENTITY crashlytics-plugin \"com.google.firebase.crashlytics\">\n\n");
    outputBuilder.append("  <!-- Performance Monitoring -->\n");
    outputBuilder.append(
        generateVersionVariable(
            depsByArtifactId,
            "Perf Plugin",
            "perf-plugin-class",
            "com.google.firebase:perf-plugin"));
    outputBuilder.append("  <!ENTITY perf-plugin \"com.google.firebase.firebase-perf\">\n");
    outputBuilder.append("]>\n");
    return outputBuilder.toString();
  }

  private static String generateVersionVariable(
      Map<String, Dependency> depsByArtifactId, String comment, String alias, String artifactId) {
    if (!depsByArtifactId.containsKey(artifactId)) {
      throw new RuntimeException("Error fetching newest version for " + artifactId);
    }
    return "  <!-- "
        + comment
        + "-->\n"
        + "  <!ENTITY "
        + alias
        + " \""
        + depsByArtifactId.get(artifactId).toGradleString()
        + "\">\n";
  }

  public void writeVersionUpdate(String document) throws IOException {
    Files.write(
        new File("recipeVersionUpdate.txt").toPath(),
        Collections.singleton(document),
        StandardCharsets.UTF_8);
  }
}
