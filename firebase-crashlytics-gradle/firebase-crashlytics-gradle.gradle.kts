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

plugins {
  id("kotlin")
  `java-gradle-plugin`
  id("maven-publish")
  idea
}

kotlin { jvmToolchain(17) }

val functionalTestSourceSet =
  sourceSets.create("functionalTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }

gradlePlugin {
  plugins {
    create("crashlytics") {
      id = "com.google.firebase.crashlytics"
      displayName = "Crashlytics Gradle plugin"
      implementationClass = "com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin"
    }
  }
  testSourceSets(functionalTestSourceSet)
}

idea {
  module {
    testSources.from(functionalTestSourceSet.allSource.srcDirs)
    testResources.from(functionalTestSourceSet.resources.srcDirs)
  }
}

java { withSourcesJar() }

publishing {
  publications.withType(MavenPublication::class.java).configureEach {
    pom {
      licenses {
        license {
          name.set("The Apache Software License, Version 2.0")
          url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
    }
  }
}

tasks {
  jar {
    manifest {
      attributes["Implementation-Title"] = project.name
      attributes["Implementation-Version"] = project.version
    }
  }
}

val functionalTestImplementation: Configuration by
  configurations.getting { extendsFrom(configurations.testImplementation.get()) }

tasks {
  validatePlugins { enableStricterValidation.set(true) }

  test {
    testLogging.showExceptions = true
    useJUnitPlatform()
  }

  val functionalTest by
    registering(Test::class) {
      description = "Runs the functional tests."
      group = "verification"
      testClassesDirs = functionalTestSourceSet.output.classesDirs
      classpath = functionalTestSourceSet.runtimeClasspath

      testLogging.showExceptions = true
      useJUnitPlatform()

      // Make the local published build accessible from functional tests.
      dependsOn(":firebase-crashlytics-gradle:publishToMavenLocal")
      dependsOn(":firebase-crashlytics-buildtools:publishToMavenLocal")
      // Make the local published version number accessible from Kotlin code.
      systemProperty("crashlytics.gradle.plugin.version", version)
      // Propagate the maven artifacts path, to be used as maven local in Gradle test kit.
      project.findProperty("maven.artifacts.path")?.let {
        systemProperty("crashlytics.maven.artifacts.path", it)
      }
    }

  check { dependsOn(functionalTest) }
}

dependencies {
  compileOnly("com.android.tools.build:gradle-api:8.1.4")
  compileOnly("com.google.gms:google-services:4.4.1")
  implementation(gradleKotlinDsl())
  implementation(project(":firebase-crashlytics-buildtools"))

  testImplementation("com.android.tools.build:gradle:8.1.4")
  testImplementation("com.android.tools.build:gradle-api:8.1.4")
  testImplementation("com.google.truth:truth:1.1.4")
  testImplementation(platform("org.junit:junit-bom:5.10.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")

  functionalTestImplementation(gradleTestKit())
}
