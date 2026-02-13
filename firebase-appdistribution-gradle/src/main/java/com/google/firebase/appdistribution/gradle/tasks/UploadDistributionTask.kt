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

package com.google.firebase.appdistribution.gradle.tasks

import com.google.firebase.appdistribution.gradle.AppDistributionEnvironment.Companion.ENV_FIREBASE_TOKEN
import com.google.firebase.appdistribution.gradle.AppDistributionEnvironment.Companion.ENV_GOOGLE_APPLICATION_CREDENTIALS
import com.google.firebase.appdistribution.gradle.AppDistributionException
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.APP_NOT_ONBOARDED_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_APP_ID
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_CREDENTIALS
import com.google.firebase.appdistribution.gradle.BinaryType
import com.google.firebase.appdistribution.gradle.FirebaseAppDistribution.uploadDistribution
import com.google.firebase.appdistribution.gradle.UploadDistributionOptions
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

@DisableCachingByDefault(because = "Remote server operation")
abstract class UploadDistributionTask : DefaultTask() {
  // All fields marked annotated with @Input require getters
  @get:Option(
    option = "groups",
    description = "A comma-separated list of group aliases you want to distribute builds to."
  )
  @get:[Input Optional]
  abstract val groups: Property<String>

  // Ideally these fields would be of type RegularFileProperty, but we want to support command
  // line overrides, and Gradle limits the set of data types that can be used for declaring
  // command line options
  // https://docs.gradle.org/current/userguide/custom_tasks.html#sec:supported_task_option_data_types
  @get:Option(
    option = "groupsFile",
    description =
      "Absolute path to a file containing a comma or newline separated list of group aliases you want to distribute builds to."
  )
  @get:[Input Optional]
  abstract val groupsFile: Property<String>

  @get:Option(option = "releaseNotes", description = "Release notes for this build.")
  @get:[Input Optional]
  abstract val releaseNotes: Property<String>

  @get:Option(
    option = "releaseNotesFile",
    description = "Absolute path to a file containing release notes for this build."
  )
  @get:[Input Optional]
  abstract val releaseNotesFile: Property<String>

  @get:Option(
    option = "serviceCredentialsFile",
    description = "The path to your service account private key JSON file."
  )
  @get:[Input Optional]
  abstract val serviceCredentialsFile: Property<String>

  @get:Option(
    option = "testers",
    description =
      "A comma-separated list of email addresses of the the testers you want to distribute builds to."
  )
  @get:[Input Optional]
  abstract val testers: Property<String>

  @get:Option(
    option = "testersFile",
    description =
      "Absolute path to a file containing a comma or newline separated list of email addresses of the testers you want to distribute builds to."
  )
  @get:[Input Optional]
  abstract val testersFile: Property<String>

  @get:Option(option = "appId", description = "Your app's Firebase App ID.")
  @get:[Input Optional]
  abstract val appId: Property<String>

  @get:Option(
    option = "artifactPath",
    description = "Absolute path to the APK or AAB file you want to upload."
  )
  @get:[Input Optional]
  abstract val artifactPath: Property<String>

  @get:Option(
    option = "artifactType",
    description =
      "Specifies your app's file type. Can be set to \"AAB\" or \"APK\". If not set, assumed to be APK."
  )
  @get:[Input Optional]
  abstract val artifactType: Property<String>

  @get:Option(
    option = "testDevices",
    description =
      "A semicolon-separated list of devices to run automated tests on, in the format 'model=<model-id>,version=<os-version-id>,locale=<locale>,orientation=<orientation>;model=<model-id>,...'. Run 'gcloud firebase test android|ios models list' to see available devices. Note: This feature is in beta."
  )
  @get:[Input Optional]
  abstract val testDevices: Property<String>

  @get:Option(
    option = "testerDevicesFile",
    description =
      "Absolute path to a file containing a semicolon or newline separated list of devices to run automated tests on, in the format 'model=<model-id>,version=<os-version-id>,locale=<locale>,orientation=<orientation>;model=<model-id>,...'. Run 'gcloud firebase test android|ios models list' to see available devices. Note: This feature is in beta."
  )
  @get:[Input Optional]
  abstract val testerDevicesFile: Property<String>

  @get:Option(option = "testUsername", description = "Username for automatic login")
  @get:[Input Optional]
  abstract val testUsername: Property<String>

  @get:Option(
    option = "testPassword",
    description =
      "Password for automatic login. If using a real password consider using testPasswordFile"
  )
  @get:[Input Optional]
  abstract val testPassword: Property<String>

  @get:Option(
    option = "testPasswordFile",
    description = "Path to file containing password for automatic login"
  )
  @get:[Input Optional]
  abstract val testPasswordFile: Property<String>

  @get:Option(
    option = "testUsernameResource",
    description = "Resource name for the username for automatic login"
  )
  @get:[Input Optional]
  abstract val testUsernameResource: Property<String>

  @get:Option(
    option = "testPasswordResource",
    description = "Resource name for the password for automatic login"
  )
  @get:[Input Optional]
  abstract val testPasswordResource: Property<String>

  @get:Option(
    option = "testNonBlocking",
    description =
      "Run tests asynchronously. Visit the Firebase console for the automatic test results."
  )
  @get:[Input Optional]
  abstract val testNonBlocking: Property<Boolean>

  @get:Option(
    option = "testCases",
    description = "A comma-separated list of test case IDs for running AI-powered automated tests"
  )
  @get:[Input Optional]
  abstract val testCases: Property<String>

  @get:Option(
    option = "testCasesFile",
    description =
      "Absolute path to a file containing a comma or newline separated list of test case IDs for running AI-powered automated tests"
  )
  @get:[Input Optional]
  abstract val testCasesFile: Property<String>

  @Internal val inferredAab = fileProperty()

  // Multiple APKs can be generated for a single variant, so the provider returns a
  // directory instead of a single file.
  @Internal val inferredApkDirectory = directoryProperty()

  @get:[InputFiles Optional PathSensitive(PathSensitivity.RELATIVE)]
  abstract val googleServicesDirectory: DirectoryProperty

  init {
    // Disable up-to-date checks since the output of the task is being stored remotely
    // https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks
    try {
      val doNotTrackState = this.javaClass.getMethod("doNotTrackState")
      doNotTrackState.invoke(this)
    } catch (e: Exception) {
      when (e) {
        is NoSuchMethodException,
        is IllegalAccessException,
        is InvocationTargetException -> {
          // Ignore if `doNotTrackState` isn't available
        }
        else -> throw e
      }
    }
  }

  @TaskAction
  fun uploadDistribution() {
    val path = determineArtifactPath()
    logger.info("Uploading {} to Firebase App Distribution...%n", BinaryType.fromPath(path))
    if (serviceCredentialsFile.isPresent) {
      logger.info(
        "Using service credentials file specified by the serviceCredentialsFile " +
          "property in your app's build.gradle file: {} %n",
        serviceCredentialsFile.get()
      )
    }

    val appId = determineAppId() ?: throw AppDistributionException(MISSING_APP_ID)

    val options =
      UploadDistributionOptions(
        binaryPath = path,
        appId = appId,
        releaseNotesValue = releaseNotes.orNull,
        releaseNotesPath = releaseNotesFile.orNull,
        testersValue = testers.orNull,
        testersPath = testersFile.orNull,
        groupsValue = groups.orNull,
        groupsPath = groupsFile.orNull,
        testDevicesValue = testDevices.orNull,
        testDevicesPath = testerDevicesFile.orNull,
        testUsername = testUsername.orNull,
        testPassword = testPassword.orNull,
        testPasswordPath = testPasswordFile.orNull,
        testUsernameResource = testUsernameResource.orNull,
        testPasswordResource = testPasswordResource.orNull,
        testNonBlocking = testNonBlocking.getOrElse(false),
        testCasesValue = testCases.orNull,
        testCasesPath = testCasesFile.orNull,
        serviceCredentialsFile = serviceCredentialsFile.orNull
      )

    try {
      uploadDistribution(options)
      logger.info("App Distribution upload finished successfully!")
    } catch (e: AppDistributionException) {
      when (e.reason) {
        APP_NOT_ONBOARDED_ERROR ->
          throw GradleException(
            """
                  App Distribution could not find your app ${options.appId}
                  Make sure to onboard your app by pressing the "Get started" button on the App Distribution page in the Firebase console:
                    https://console.firebase.google.com/project/_/appdistribution  
                """
              .trimIndent()
          )
        MISSING_CREDENTIALS ->
          throw GradleException(
            """
                    Could not find credentials. To authenticate, you have a few options: 
                    1. Set the `serviceCredentialsFile` property in your gradle plugin
                    2. Set a refresh token with the $ENV_FIREBASE_TOKEN environment variable
                    3. Log in with the Firebase CLI
                    4. Set service credentials with the $ENV_GOOGLE_APPLICATION_CREDENTIALS environment variable
                """
              .trimIndent()
          )
        MISSING_APP_ID ->
          throw GradleException(
            "Could not find your app id. Please verify that you set the property `appId` in your app's build.gradle file and try again."
          )
        else -> throw GradleException(e.message ?: DEFAULT_UPLOAD_ERROR_MESSAGE, e)
      }
    }
  }

  /**
   * Creates a file property. Calls `getProject()`, so don't call this method during task execution
   */
  private fun fileProperty(): RegularFileProperty {
    return project.objects.fileProperty()
  }

  /**
   * Creates a directory property. Calls `getProject()`, so don't call this method during task
   * execution
   */
  private fun directoryProperty(): DirectoryProperty {
    return project.objects.directoryProperty()
  }

  // TODO: This app ID extraction code is very brittle. We should get the app ID from
  // the GMS task output like Crashlytics
  // firebase-crashlytics-gradle/src/main/kotlin/com/google/firebase/crashlytics/buildtools/gradle/AppIdFetcher.kt

  private fun determineAppId(): String? {
    if (appId.isPresent) {
      return appId.get()
    }

    if (!googleServicesDirectory.isPresent) {
      return null
    }

    val googleServicesXml = File(googleServicesDirectory.asFile.get(), "values/values.xml")
    if (!googleServicesXml.exists()) {
      return null
    }

    try {
      val factory = DocumentBuilderFactory.newInstance()
      val builder = factory.newDocumentBuilder()
      val doc = builder.parse(googleServicesXml)
      val stringNodes = doc.getElementsByTagName("string")
      for (i in 0 until stringNodes.length) {
        val stringNode = stringNodes.item(i)
        if (stringNode.nodeType == Node.ELEMENT_NODE) {
          val stringElement = stringNode as Element
          val name = stringElement.getAttribute("name")
          if (name == "google_app_id") {
            return stringNode.getTextContent()
          }
        }
      }
    } catch (e: Exception) {
      when (e) {
        is ParserConfigurationException,
        is SAXException,
        is IOException -> {
          // If we fail to parse the XML file return null
        }
        else -> throw e
      }
    }

    // Did not find value in file, return null.
    return null
  }

  private fun determineArtifactPath(): String {
    if (artifactPath.isPresent) {
      logger.info(
        "Using {} path specified by the artifactPath parameter in your app's build.gradle: {}.%n",
        BinaryType.fromPath(artifactPath.get()),
        artifactPath.get()
      )
      return artifactPath.get()
    } else {
      val path =
        if (artifactType.isPresent && artifactType.get() == ARTIFACT_TYPE_AAB) {
          if (!inferredAab.isPresent) {
            throw GradleException(
              "Could not find an AAB file for this variant for App Distribution"
            )
          }
          inferredAab.get().asFile.path
        } else {
          val apks = inferredApkDirectory.asFileTree.files.filter { it.path.endsWith(".apk") }
          when (apks.size) {
            0 ->
              throw GradleException(
                "Could not find an APK file for this variant for App Distribution"
              )
            1 -> apks[0].path
            else ->
              throw GradleException(
                "App Distribution found more " +
                  "than 1 output file for this variant. Please contact $SUPPORT_EMAIL for " +
                  "help using APK splits with App Distribution."
              )
          }
        }
      logger.info("Using {} path in the outputs directory: {}.%n", BinaryType.fromPath(path), path)
      return path
    }
  }

  companion object {
    private const val ARTIFACT_TYPE_AAB = "AAB"
    private const val SUPPORT_EMAIL = "firebase-support@google.com"
    private const val DEFAULT_UPLOAD_ERROR_MESSAGE = "Error uploading distribution"
  }
}
