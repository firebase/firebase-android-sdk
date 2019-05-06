// Copyright 2019 Google LLC
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

package com.google.firebase.gradle.plugins;

import com.android.build.gradle.LibraryExtension;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

public class FirebaseLibraryPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.apply(ImmutableMap.of("plugin", "com.android.library"));

    FirebaseLibraryExtension firebaseLibrary =
        project
            .getExtensions()
            .create("firebaseLibrary", FirebaseLibraryExtension.class, project.getObjects());

    LibraryExtension android = project.getExtensions().getByType(LibraryExtension.class);

    android.testServer(new FirebaseTestServer(project, firebaseLibrary.testLab));

    // TODO(vkryachko): include sources in firebasePublish
    project.afterEvaluate(
        p -> {
          if (firebaseLibrary.publishSources) {

            p.getTasks()
                .create(
                    "sourcesJar",
                    Jar.class,
                    jar -> {
                      jar.from(android.getSourceSets().getByName("main").getJava().getSrcDirs());
                      jar.setClassifier("sources");
                    });
          }
        });
  }
}
