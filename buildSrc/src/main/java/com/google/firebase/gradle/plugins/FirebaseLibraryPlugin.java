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

import static com.google.firebase.gradle.plugins.ClosureUtil.closureOf;

import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatExtension;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.gradle.plugins.ci.Coverage;
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer;
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin;
import java.io.File;
import java.nio.file.Paths;
import kotlin.Unit;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile;

public class FirebaseLibraryPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.apply(ImmutableMap.of("plugin", "com.android.library"));
    project.apply(ImmutableMap.of("plugin", LicenseResolverPlugin.class));
    project.apply(ImmutableMap.of("plugin", "com.github.sherter.google-java-format"));
    project.getExtensions().getByType(GoogleJavaFormatExtension.class).setToolVersion("1.10.0");

    FirebaseLibraryExtension firebaseLibrary =
        project
            .getExtensions()
            .create(
                "firebaseLibrary", FirebaseLibraryExtension.class, project, LibraryType.ANDROID);

    LibraryExtension android = project.getExtensions().getByType(LibraryExtension.class);

    android.compileOptions(
        options -> {
          options.setSourceCompatibility(JavaVersion.VERSION_1_8);
          options.setTargetCompatibility(JavaVersion.VERSION_1_8);
        });

    // In the case of and android library signing config only affects instrumentation test APK.
    // We need it signed with default debug credentials in order for FTL to accept the APK.
    android.buildTypes(
        (NamedDomainObjectContainer<BuildType> types) ->
            types
                .getByName("release")
                .setSigningConfig(types.getByName("debug").getSigningConfig()));
    android.defaultConfig(
        cfg -> {
          cfg.buildConfigField("String", "VERSION_NAME", "\"" + project.getVersion() + "\"");
        });

    // see https://github.com/robolectric/robolectric/issues/5456
    android.testOptions(
        (TestOptions options) ->
            options
                .getUnitTests()
                .all(
                    closureOf(
                        test -> {
                          test.systemProperty("robolectric.dependency.repo.id", "central");
                          test.systemProperty(
                              "robolectric.dependency.repo.url", "https://repo1.maven.org/maven2");
                          test.systemProperty("javax.net.ssl.trustStoreType", "JKS");
                        })));

    // skip debug tests in CI
    // TODO(vkryachko): provide ability for teams to control this if needed
    if (System.getenv().containsKey("FIREBASE_CI")) {
      android.setTestBuildType("release");
      project
          .getTasks()
          .all(
              task -> {
                if ("testDebugUnitTest".equals(task.getName())) {
                  task.setEnabled(false);
                }
              });
    }

    setupApiInformationAnalysis(project, android);
    android.testServer(new FirebaseTestServer(project, firebaseLibrary.testLab, android));

    setupStaticAnalysis(project, firebaseLibrary);

    configurePublishing(project, firebaseLibrary, android);

    // reduce the likelihood of kotlin module files colliding.
    project
        .getTasks()
        .withType(
            KotlinCompile.class,
            kotlin ->
                kotlin
                    .getKotlinOptions()
                    .setFreeCompilerArgs(
                        ImmutableList.of("-module-name", kotlinModuleName(project))));

    project.getPluginManager().apply(DackkaPlugin.class);
  }

  private static void setupApiInformationAnalysis(Project project, LibraryExtension android) {
    AndroidSourceSet mainSourceSet = android.getSourceSets().getByName("main");
    File outputFile =
        project
            .getRootProject()
            .file(
                Paths.get(
                    project.getRootProject().getBuildDir().getPath(),
                    "apiinfo",
                    project.getPath().substring(1).replace(":", "_")));
    File outputApiFile = new File(outputFile.getAbsolutePath() + "_api.txt");

    File apiTxt =
        project.file("api.txt").exists()
            ? project.file("api.txt")
            : project.file(project.getRootDir() + "/empty-api.txt");
    TaskProvider<ApiInformationTask> apiInfo =
        project
            .getTasks()
            .register(
                "apiInformation",
                ApiInformationTask.class,
                task -> {
                  task.getSources()
                      .value(project.provider(() -> mainSourceSet.getJava().getSrcDirs()));
                  task.getApiTxtFile().set(apiTxt);
                  task.getBaselineFile().set(project.file("baseline.txt"));
                  task.getOutputFile().set(outputFile);
                  task.getOutputApiFile().set(outputApiFile);
                  task.getUpdateBaseline().set(project.hasProperty("updateBaseline"));
                });

    TaskProvider<GenerateApiTxtTask> generateApiTxt =
        project
            .getTasks()
            .register(
                "generateApiTxtFile",
                GenerateApiTxtTask.class,
                task -> {
                  task.getSources()
                      .value(project.provider(() -> mainSourceSet.getJava().getSrcDirs()));
                  task.getApiTxtFile().set(project.file("api.txt"));
                  task.getBaselineFile().set(project.file("baseline.txt"));
                  task.getUpdateBaseline().set(project.hasProperty("updateBaseline"));
                });

    TaskProvider<GenerateStubsTask> docStubs =
        project
            .getTasks()
            .register(
                "docStubs",
                GenerateStubsTask.class,
                task ->
                    task.getSources()
                        .value(project.provider(() -> mainSourceSet.getJava().getSrcDirs())));
    project.getTasks().getByName("check").dependsOn(docStubs);

    android
        .getLibraryVariants()
        .all(
            v -> {
              if (v.getName().equals("release")) {
                FileCollection jars =
                    v.getCompileConfiguration()
                        .getIncoming()
                        .artifactView(
                            config ->
                                config.attributes(
                                    container ->
                                        container.attribute(
                                            Attribute.of("artifactType", String.class),
                                            "android-classes")))
                        .getArtifacts()
                        .getArtifactFiles();
                apiInfo.configure(t -> t.setClassPath(jars));
                generateApiTxt.configure(t -> t.setClassPath(jars));
                docStubs.configure(t -> t.setClassPath(jars));
              }
            });
  }

  private static void setupStaticAnalysis(Project project, FirebaseLibraryExtension library) {
    project.afterEvaluate(
        p ->
            project
                .getConfigurations()
                .all(
                    c -> {
                      if ("lintChecks".equals(c.getName())) {
                        for (String checkProject :
                            library.staticAnalysis.androidLintCheckProjects) {
                          project
                              .getDependencies()
                              .add("lintChecks", project.project(checkProject));
                        }
                      }
                    }));

    project.getTasks().register("firebaseLint", task -> task.dependsOn("lint"));
    Coverage.apply(library);
  }

  private static String kotlinModuleName(Project project) {

    String fullyQualifiedProjectPath = project.getPath().replaceAll(":", "-");

    return project.getRootProject().getName() + fullyQualifiedProjectPath;
  }

  private static void configurePublishing(
      Project project, FirebaseLibraryExtension firebaseLibrary, LibraryExtension android) {
    android.publishing(
        p -> {
          p.singleVariant(
              "release",
              v -> {
                v.withSourcesJar();
                return Unit.INSTANCE;
              });
        });
    project
        .getTasks()
        .withType(
            GenerateModuleMetadata.class,
            task -> {
              task.setEnabled(false);
            });
    project.afterEvaluate(
        p -> {
          project.apply(ImmutableMap.of("plugin", "maven-publish"));
          PublishingExtension publishing =
              project.getExtensions().getByType(PublishingExtension.class);
          publishing.repositories(
              repos ->
                  repos.maven(
                      repo -> {
                        String s = project.getRootProject().getBuildDir() + "/m2repository";
                        File file = new File(s);
                        repo.setUrl(file.toURI());
                        repo.setName("BuildDir");
                      }));
          publishing.publications(
              publications ->
                  publications.create(
                      "mavenAar",
                      MavenPublication.class,
                      publication -> {
                        publication.from(
                            project
                                .getComponents()
                                .findByName(firebaseLibrary.type.getComponentName()));
                        publication.setArtifactId(firebaseLibrary.artifactId.get());
                        publication.setGroupId(firebaseLibrary.groupId.get());
                        firebaseLibrary.applyPomCustomization(publication.getPom());
                      }));
        });
  }
}
