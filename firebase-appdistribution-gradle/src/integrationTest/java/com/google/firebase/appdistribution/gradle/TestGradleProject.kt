/*
 * Copyright 2025 Google LLC
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

import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.apache.commons.io.FileUtils
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder

class TestGradleProject : ExternalResource() {
  @JvmField internal val projectDir = TemporaryFolder()

  internal val projectNumber = DEFAULT_PROJECT_NUMBER
  internal val appId = DEFAULT_APP_ID
  internal val packageName = DEFAULT_PACKAGE_NAME

  internal lateinit var serviceCredentialsFile: File
  internal lateinit var pluginClasspathFiles: List<File>
  internal lateinit var pluginClasspathString: String

  private lateinit var localProperties: File
  private lateinit var manifestFile: File
  private lateinit var googleServicesFile: File

  /**
   * An overridden method from [ExternalResource.before] that allocates the resources required to
   * run test builds.
   */
  @Throws(IOException::class, URISyntaxException::class)
  override fun before() {
    // Create project directory and all the necessary files
    projectDir.create()
    projectDir.newFolder("app", "src", "main")

    manifestFile = projectDir.newFile("app/src/main/AndroidManifest.xml")
    serviceCredentialsFile = projectDir.newFile("app/service-credentials.json")
    googleServicesFile = projectDir.newFile("app/google-services.json")
    localProperties = projectDir.newFile("app/local.properties")

    pluginClasspathFiles = extractPluginClasspathFiles()
    pluginClasspathString = convertPluginClasspathString(pluginClasspathFiles)

    writeManifestFile()
    writeServiceCredentialsFile()
    writeGoogleServicesJsonFile()
    writeLocalProperties()
  }

  /**
   * An overridden method from [ExternalResource.after] that releases any allocated resources after
   * the execution of the test methods.
   */
  override fun after() = projectDir.delete()

  /** Create a new file in the project directory */
  fun createAndWriteFile(path: String, content: String) =
    projectDir.newFile(path).also { Companion.writeFile(it, content) }

  /** Writes the `service-credentials.json` file for the test project. */
  @Throws(IOException::class)
  internal fun writeServiceCredentialsFile() =
    writeFile(
      serviceCredentialsFile,
      """
        {
          "type": "service_account",
          "project_id": "my-test-project",
          "private_key_id": "abcdefg123456789",
          "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCwFW3VVG0t5/83\nCmCCIeybpI0fi8t52rVQ98h7xwT+8DL0sZAofNB4A/NuE9GysZ6WpNImz1vWqEIO\nMSUFQaGuybzgAObzbhg03MtAqjWKyp+zBbbdtQ35Eu7VcMNT+yOmsyGRs6z06rML\nKa5KaetN+f+F38X1gKrxe2oX7wQznVqpWfWDaVWpNajDwqM7khrBUMAhg/jWKGjk\nQUmlDeFZ4fqbJkUyK61fSKNNzRpwoCD7fkWhRdP2PtJF0zBMAWxkXWuDeslHRwoR\nHJVpIriXjLfOMvmXXt5crYVOnY2X8eygzLOi0ydXDJV/KDIWGbJZBWtD0MTBzb7K\nm8dRNeZPAgMBAAECggEAKabgBMMEUoQa8FuhiZbZv9V0Vn58gtYT5tO+Fl11FpMe\nEpjAB3vC2mjg1+yTQYhXgb36Qhjx2fySJ4ZDghNM2io5ZemAuBuWWUbTQ3gf0zVs\nAm57G8W8yOrMGSwj4EU7YS7lZXBRnNu5v427/zk/4oGdCn9s9c5aYWX2qjOtVEHj\nWHZQGA+bb1/kY+IMOeoW5TJVYK7kIakOh9Bcch3rMvpWYvuDTon8XqL6Sr+qtsBQ\nWUvmJQdinig6j2r8IJNiVdLr0QSqI1PPaQQW/3zpvnwWlZVdTfBhEFwNYEvHZgvQ\nUw/5LrS2pj+4at+uVOZSKvtyReBq19E2IweHNSHamQKBgQDlEZNmx/yhdRgsrVcs\nAtA3n+r4S5pn3DKyhXZw32polg3nc2GVWJRnM8A1p62qTaZDH/x9qpZl3cgKoBPs\nszE5nrGza3Fucom91ScEgbVi8k2CGIfeC6s7JbNSLLBPkb0BITpWQBG81wdhAsAg\nHeh1EIyoQ8UFnBZlftmZLW+OdwKBgQDEySG6Ow3K97pw2pev3Q+m+9Ne+Y1f7Bwz\nvh4+ZXENCGZlBhlYv7ODfaPw4LBA5JiRUNKhyz8BA+HqZ+dIn9CWflhKb+4FvtoJ\nGgHvM+95LQgabTMsEhfwt33RAPaaKfzmbkTa08zYgVGcLkNftqwykbDHiTp1qrLA\nJWUfeR+k6QKBgQDONMgwWg89eR9N+KzkXZP6vubSpZxVqo+ozSQV78jmZU4W8HMD\n8j2FubxpkIxxJn1pJ74vkgTZppCRoBDPn2/MouLs1OfDuS/tx5fcIreaXu0PE+4b\nIP3/vKx0aO4+cr9l6PeO9RYCnL9zwPoa71F3MHKudnNB3YT70PkpPxGReQKBgBat\nFrXfGDfLVDCs/83EK4mSe0j2eNQU4SsVPWbzSZO25BXAHiub65O7ZqjbO3Q+41Xb\nemoqgZgcWmwojP5RbDfrV0E8pLEEzRs/Y/msgmv0RHrHGp55d0jF3Dm5YrFhJUNo\ncYbF4VURkdXtftYIts8c+sIDjjkA8pgxtvVpf7wBAoGBAJpNKe3nBHqbIVNjxGly\na1FiIGgK1Hi/2uWFm+vKjj6NHi6Th18CHLIa3/xk3QZ8PDNmVgXitalyHwP4oJLt\nY0TaZIYuP3peZ4GLT+7uakWvW0DEGXuYS9sAnXBJ17ombPX7uXkhnn1vHZDKZtlS\nhD5zo283I/NwgvceIlTLLv31\n-----END PRIVATE KEY-----\n",
          "client_email": "gradle-functional-test@fad-test-apps.iam.gserviceaccount.com",
          "client_id": "1234567890",
          "auth_uri": "https://accounts.google.com/o/oauth2/auth",
          "token_uri": "https://oauth2.googleapis.com/token",
          "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
          "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/gradle-functional-test%40fad-test-apps.iam.gserviceaccount.com"
        }
        """
        .trimIndent()
    )

  /**
   * Retrieves and collects the plugin's classpath (generated by "createClasspathManifest" task in
   * build.gradle) into a [List] with type [File]. This is necessary because builds and tests are
   * executed in separate processes. Therefore, we need to explicitly specify the classpath to make
   * our gradle plugin available to our tests.
   *
   * Refer to:
   * https://docs.gradle.org/current/userguide/test_kit.html#sub:test-kit-classpath-injection
   */
  @Throws(URISyntaxException::class, IOException::class)
  private fun extractPluginClasspathFiles(): List<File> {
    val pluginClasspathResource =
      javaClass.classLoader.getResource("plugin-classpath.txt")
        ?: throw IllegalStateException(
          "Did not find plugin classpath resource, run `testClasses` build task."
        )
    return Files.readAllLines(Path.of(pluginClasspathResource.toURI())).map(::File)
  }

  /**
   * Converts the plugin's classpath from [List] with type [File] to [String].
   *
   * Refer to:
   * https://docs.gradle.org/5.4.1/userguide/test_kit.html#sub:test-kit-classpath-injection
   */
  private fun convertPluginClasspathString(pluginClasspathFiles: List<File>?): String {
    return pluginClasspathFiles!!
      .stream()
      .map { file: File -> file.absolutePath.replace("\\", "\\\\") }
      .map { path: String? -> "'$path'" }
      .collect(Collectors.joining(","))
  }

  /** Writes the `AndroidManifest.xml` file for the test project. */
  @Throws(IOException::class)
  private fun writeManifestFile() =
    writeFile(
      manifestFile,
      """
          <manifest package= '$packageName'>
            <application/>
          </manifest>
         """
        .trimIndent()
    )

  @Throws(IOException::class)
  private fun writeGoogleServicesJsonFile() =
    writeFile(
      googleServicesFile,
      """
        {
          "project_info": {
            "project_number": "$DEFAULT_PROJECT_NUMBER",
            "project_id": "mytestgradleapp",
            "storage_bucket": "mytestgradleapp.appspot.com"
          },
          "client": [
            {
              "client_info": {
              "mobilesdk_app_id": "$DEFAULT_APP_ID",
              "android_client_info": {
                "package_name": "$DEFAULT_PACKAGE_NAME"
              }
            },
              "api_key": [
                {
                  "current_key": "aaaaaaaa"
                }
              ]
            }
          ],
          "configuration_version": "1"
        }
      """
        .trimIndent()
    )

  /**
   * Write the `local.properties` file for the test project. The Android Gradle plugin is a compile
   * time dependency for the plugin, so we need to set the path to the Android SDK for use with
   * integration tests.
   */
  @Throws(IOException::class)
  private fun writeLocalProperties() =
    writeFile(localProperties, "sdk.dir=${System.getenv("ANDROID_HOME")}")

  /** Utility for creating a file in the project dir */
  @Throws(IOException::class) fun createFile(fileName: String?) = projectDir.newFile(fileName)

  companion object {
    private const val DEFAULT_PROJECT_NUMBER = "123"
    private const val DEFAULT_APP_ID = "1:123:android:deadbeef"
    private const val DEFAULT_PACKAGE_NAME = "firebase.app.distribution.plugin.test"

    /** Utility function to write the String `content` in the specified File `destination`. */
    @JvmStatic
    @Throws(IOException::class)
    internal fun writeFile(destination: File?, content: String?) {
      FileUtils.writeStringToFile(destination, content, "UTF-8")
    }
  }
}
