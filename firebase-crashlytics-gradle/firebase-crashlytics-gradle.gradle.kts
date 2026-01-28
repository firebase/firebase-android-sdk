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
}

kotlin { jvmToolchain(17) }

gradlePlugin {
  plugins {
    create("crashlytics") {
      id = "com.google.firebase.crashlytics"
      displayName = "Crashlytics Gradle plugin"
      implementationClass = "com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin"
    }
  }
}

java { withSourcesJar() }

tasks {
  // Include source (and KDoc) for only the Crashlytics extension.
  named<Jar>("sourcesJar") {
    include("com/google/firebase/crashlytics/buildtools/gradle/CrashlyticsExtension.kt")
  }
}

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
  publications {
    create<MavenPublication>("pluginMaven") {
      val artifactId: String by project
      this.artifactId = artifactId
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

tasks { validatePlugins { enableStricterValidation.set(true) } }

dependencies {
  compileOnly("com.android.tools.build:gradle-api:8.1.4")
  compileOnly("com.google.gms:google-services:4.4.1")
  implementation(gradleKotlinDsl())
  implementation(project(":firebase-crashlytics-buildtools"))

  testImplementation("com.android.tools.build:gradle-api:8.1.4")
  testImplementation("com.google.truth:truth:1.1.4")
  testImplementation(platform("org.junit:junit-bom:5.10.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation(gradleTestKit())
}

tasks {
  test {
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
}
