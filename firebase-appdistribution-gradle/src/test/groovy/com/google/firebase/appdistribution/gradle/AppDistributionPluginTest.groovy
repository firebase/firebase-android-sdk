/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.appdistribution.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class AppDistributionPluginTest {

  TemporaryFolder tempFolder = new TemporaryFolder()
  ManifestCopier manifestCopier = new ManifestCopier("src/test/fixtures/android_app", tempFolder)

  // Set up a temp folder which is automatically cleaned up after the test
  // We use a manifest copier to avoid cluttering the fixtures directory
  @Rule
  public RuleChain chain = RuleChain.outerRule(tempFolder)
  .around(manifestCopier)

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  Project project

  @Before
  void setUp() {
    // The Android Gradle plugin is a compile time dependency for the plugin, so we need to
    // set the path to the Android SDK for tests.
    def file = tempFolder.newFile("local.properties")
    file.write(String.format("sdk.dir=%s", System.getenv("ANDROID_SDK_ROOT")))
  }

  @Test
  void testApplyPlugin_failsIfAndroidPluginDoesNotExist() {
    exceptionRule.expectCause(CoreMatchers.isA(IllegalStateException.class))
    def project = ProjectBuilder
        .builder()
        .withProjectDir(tempFolder.root)
        .build()
    project.apply plugin: 'com.google.firebase.appdistribution'

    project.evaluate()
  }

  @Test
  void testApplyPlugin_succeedsIfAndroidPluginIsAppliedAfter() {
    def project = ProjectBuilder
        .builder()
        .withProjectDir(tempFolder.root)
        .build()
    project.apply plugin: 'com.google.firebase.appdistribution'
    project.apply plugin: 'com.android.application'
    project.android {
      compileSdkVersion 28
      buildToolsVersion '28.0.3'
      buildTypes {
        release {
        }
      }
    }

    project.evaluate()
  }

  @Test
  void testTaskSetup_correctlyCreatesTasksForEachBuildType() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release
        debug
        other
      }
    }
    project.evaluate()

    assertNotNull(project.tasks.findByName("appDistributionUploadRelease"))
    assertNotNull(project.tasks.findByName("appDistributionUploadDebug"))
    assertNotNull(project.tasks.findByName("appDistributionUploadOther"))
  }

  @Test
  void testExtensionPropertyParsing_parsesAllPropertiesCorrectly() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentialsFile="/release.json"
            artifactPath="/fake-apk-path"
            appId="fake-app-id"
            releaseNotes="Here is a new app!"
            releaseNotesFile="/fake-release-notes-file"
            testers="tester@tester.com"
            testersFile="/fake-testers-file"
            groups="group1"
            groupsFile="/fake-groups-file"
            testCases="test-case"
            testCasesFile="/fake-test-cases-file"
          }
        }
      }
    }
    project.evaluate()

    def releaseTask = project.tasks.getByName("appDistributionUploadRelease")

    assertEquals(releaseTask.serviceCredentialsFile.get(), "/release.json")
    assertEquals(releaseTask.artifactPath.get(), "/fake-apk-path")
    assertEquals(releaseTask.appId.get(), "fake-app-id")
    assertEquals(releaseTask.releaseNotes.get(), "Here is a new app!")
    assertEquals(releaseTask.releaseNotesFile.get(), "/fake-release-notes-file")
    assertEquals(releaseTask.testers.get(), "tester@tester.com")
    assertEquals(releaseTask.testersFile.get(), "/fake-testers-file")
    assertEquals(releaseTask.groups.get(), "group1")
    assertEquals(releaseTask.groupsFile.get(), "/fake-groups-file")
    assertEquals(releaseTask.testCases.get(), "test-case")
    assertEquals(releaseTask.testCasesFile.get(), "/fake-test-cases-file")
  }

  @Test
  void testExtensionPropertyParsing_withRelativePaths_parsesAllPropertiesCorrectly() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentialsFile="release.json"
            artifactPath="fake-apk-path"
            releaseNotesFile="fake-release-notes-file"
            testersFile="fake-testers-file"
            groupsFile="fake-groups-file"
            testCasesFile="fake-test-cases-file"
          }
        }
      }
    }
    project.evaluate()

    def releaseTask = project.tasks.getByName("appDistributionUploadRelease")

    assertEquals(releaseTask.serviceCredentialsFile.get(), project.rootDir.path + "/" + "release.json")
    assertEquals(releaseTask.artifactPath.get(), project.rootDir.path + "/" + "fake-apk-path")
    assertEquals(releaseTask.releaseNotesFile.get(), project.rootDir.path + "/" + "fake-release-notes-file")
    assertEquals(releaseTask.testersFile.get(), project.rootDir.path + "/" + "fake-testers-file")
    assertEquals(releaseTask.groupsFile.get(), project.rootDir.path + "/" + "fake-groups-file")
    assertEquals(releaseTask.testCasesFile.get(), project.rootDir.path + "/" + "fake-test-cases-file")
  }

  @Test
  void testExtensionPropertyParsing_withDefaultExtension_parsesAllPropertiesCorrectly() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentialsFile="release.json"
            artifactPath="fake-apk-path"
            releaseNotesFile="fake-release-notes-file"
            testersFile="fake-testers-file"
            groupsFile="fake-groups-file"
          }
        }
      }
    }
    project.firebaseAppDistributionDefault {
      serviceCredentialsFile="release.json-default"
      artifactPath="fake-apk-path-default"
      releaseNotesFile="fake-release-notes-file-default"
      testersFile="fake-testers-file-default"
      groupsFile="fake-groups-file-default"
      testCasesFile="fake-test-cases-file-default"
    }

    project.evaluate()

    def releaseTask = project.tasks.getByName("appDistributionUploadRelease")

    assertEquals(releaseTask.serviceCredentialsFile.get(), project.rootDir.path + "/" + "release.json")
    assertEquals(releaseTask.artifactPath.get(), project.rootDir.path + "/" + "fake-apk-path")
    assertEquals(releaseTask.releaseNotesFile.get(), project.rootDir.path + "/" + "fake-release-notes-file")
    assertEquals(releaseTask.testersFile.get(), project.rootDir.path + "/" + "fake-testers-file")
    assertEquals(releaseTask.groupsFile.get(), project.rootDir.path + "/" + "fake-groups-file")
    assertEquals(releaseTask.testCasesFile.get(), project.rootDir.path + "/" + "fake-test-cases-file-default")
  }

  @Test
  void testExtensionPropertyParsing_withDefaultExtensionAndDeprecatedExtension_parsesAllPropertiesCorrectly() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentialsFile="release.json"
            artifactPath="fake-apk-path"
            releaseNotesFile="fake-release-notes-file"
            testersFile="fake-testers-file"
          }
        }
      }
    }
    project.firebaseAppDistributionDefault {
      serviceCredentialsFile="release.json-default"
      artifactPath="fake-apk-path-default"
      releaseNotesFile="fake-release-notes-file-default"
      testersFile="fake-testers-file-default"
      groupsFile="fake-groups-file-default"
      testCasesFile="fake-test-cases-file-default"
    }

    project.firebaseAppDistribution {
      serviceCredentialsFile="release.json-deprecated"
      artifactPath="fake-apk-path-deprecated"
      releaseNotesFile="fake-release-notes-file-deprecated"
      testersFile="fake-testers-file-deprecated"
      testCasesFile="fake-test-cases-file-deprecated"
    }

    project.evaluate()

    def releaseTask = project.tasks.getByName("appDistributionUploadRelease")

    assertEquals(releaseTask.serviceCredentialsFile.get(), project.rootDir.path + "/" + "release.json")
    assertEquals(releaseTask.artifactPath.get(), project.rootDir.path + "/" + "fake-apk-path")
    assertEquals(releaseTask.releaseNotesFile.get(), project.rootDir.path + "/" + "fake-release-notes-file")
    assertEquals(releaseTask.testersFile.get(), project.rootDir.path + "/" + "fake-testers-file")
    assertEquals(releaseTask.groupsFile.get(), project.rootDir.path + "/" + "fake-groups-file-default")
    assertEquals(releaseTask.testCasesFile.get(), project.rootDir.path + "/" + "fake-test-cases-file-deprecated")
  }

  @Test
  void testExtensionPropertyParsing_parsesPropertiesByBuildType() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentialsFile="/release.json"
          }
        }
        debug {
          firebaseAppDistribution {
            serviceCredentialsFile="/debug.json"
          }
        }
      }
    }
    project.evaluate()

    def releaseTask = project.tasks.getByName("appDistributionUploadRelease")
    def debugTask = project.tasks.getByName("appDistributionUploadDebug")

    assertEquals(releaseTask.serviceCredentialsFile.get(), "/release.json")
    assertEquals(debugTask.serviceCredentialsFile.get(), "/debug.json")
  }

  @Test
  void testExtensionPropertyParsing_prioritizesBuildTypeOverTopLevel() {
    project = generateTestableProject()
    project.firebaseAppDistribution {
      serviceCredentialsFile="/top-level.json"
      appId="top-level-app-id"
    }
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentialsFile="/release.json"
          }
        }
        debug {
          firebaseAppDistribution {
            serviceCredentialsFile="/debug.json"
          }
        }
      }
    }
    project.evaluate()

    def releaseTask = project.tasks.getByName("appDistributionUploadRelease")
    def debugTask = project.tasks.getByName("appDistributionUploadDebug")

    assertEquals(releaseTask.serviceCredentialsFile.get(), "/release.json")
    assertEquals(releaseTask.appId.get(), 'top-level-app-id')
    assertEquals(debugTask.serviceCredentialsFile.get(), "/debug.json")
    assertEquals(debugTask.appId.get(), 'top-level-app-id')
  }

  @Test
  void testExtensionPropertyParsing_prioritizesProductFlavorOverBuildType() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        debug {
          firebaseAppDistribution {
            appId="buildTypeAppId"
            testers="buildTypeTesters"
          }
        }
      }
      flavorDimensions "mode"
      productFlavors {
        demo {
          firebaseAppDistribution {
            appId="productFlavorAppId"
          }
        }
      }
    }
    project.evaluate()

    def task = project.tasks.getByName("appDistributionUploadDemoDebug")

    assertEquals(task.appId.get(), "productFlavorAppId")
    assertEquals(task.testers.get(), "buildTypeTesters")
  }

  @Test
  void testExtensionPropertyParsing_withMultipleFlavorDimensions() {
    // Tests that the correct extension values are kept when using multiple
    // flavor dimensions.
    // https://developer.android.com/studio/build/build-variants#flavor-dimensions
    project = generateTestableProject()
    project.android {
      buildTypes {
        debug {
          firebaseAppDistribution {
            appId = "debugAppId"
          }
        }
      }
      // "api" dimension flavor values should override "mode" flavor values since priority
      // is determined by the order, from highest to lowest.
      flavorDimensions "api", "mode"
      productFlavors {
        latest {
          dimension "api"
          firebaseAppDistribution {
            // Highest priority dimension, this value should always take precedence
            appId = "latestAppId"
          }
        }
        oldest {
          dimension "api"
          firebaseAppDistribution {
            // No values here, so should fall back to "mode" dimension flavor values
          }
        }
        demo {
          dimension "mode"
          firebaseAppDistribution {
            // Lower priority dimension, so this value should only take precedence if
            // flavor with "api" dimension doesn't have a value i.e. "oldest"
            appId = "demoAppId"
          }
        }
        full {
          dimension "mode"
          // No `firebaseAppDistribution` declaration so should fall back to build type
        }
      }
    }
    project.evaluate()

    // "api" dimension flavor values should override "mode" dimension flavor values since it's
    // first in flavorDimensions
    assertEquals(getUploadDistributionTask("appDistributionUploadLatestDemoDebug").getAppId().get(), "latestAppId")
    assertEquals(getUploadDistributionTask("appDistributionUploadLatestDemoRelease").getAppId().get(), "latestAppId")
    assertEquals(getUploadDistributionTask("appDistributionUploadLatestFullDebug").getAppId().get(), "latestAppId")
    assertEquals(getUploadDistributionTask("appDistributionUploadLatestFullRelease").getAppId().get(), "latestAppId")

    // "oldest" flavor has no values so it should fall back to "mode" dimension flavor values
    assertEquals(getUploadDistributionTask("appDistributionUploadOldestDemoDebug").getAppId().get(), "demoAppId")
    assertEquals(getUploadDistributionTask("appDistributionUploadOldestDemoRelease").getAppId().get(), "demoAppId")
    assertEquals(getUploadDistributionTask("appDistributionUploadOldestFullDebug").getAppId().get(), "debugAppId")
    assertEquals(getUploadDistributionTask("appDistributionUploadOldestFullRelease").getAppId().isPresent(), false)
  }

  private Task getUploadDistributionTask(String name) {
    project.tasks.getByName(name)
  }

  @Test(expected = MissingPropertyException.class)
  void testExtensionPropertyParsing_withTypoThrowsMissingPropertyException() {
    project = generateTestableProject()
    project.android {
      buildTypes {
        release {
          firebaseAppDistribution {
            serviceCredentailsFile = "release.json"
          }
        }
      }
    }
  }

  private Project generateTestableProject() {
    def project = ProjectBuilder
        .builder()
        .withProjectDir(tempFolder.root)
        .build()

    project.apply plugin: 'com.android.application'
    project.apply plugin: 'com.google.firebase.appdistribution'

    project.android {
      compileSdkVersion 28
      buildToolsVersion '28.0.3'
    }

    return project
  }
}
