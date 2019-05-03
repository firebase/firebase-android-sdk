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
          if (firebaseLibrary.publishSources.get()) {

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
