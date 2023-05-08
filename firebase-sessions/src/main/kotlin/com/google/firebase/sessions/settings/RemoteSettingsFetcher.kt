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

package com.google.firebase.sessions.settings

import android.net.Uri
import com.google.firebase.sessions.ApplicationInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

internal interface CrashlyticsSettingsFetcher {
  suspend fun doConfigFetch(
    headerOptions: Map<String, String>,
    onSuccess: suspend ((JSONObject)) -> Unit,
    onFailure: suspend () -> Unit
  )
}

internal class RemoteSettingsFetcher(val appInfo: ApplicationInfo) : CrashlyticsSettingsFetcher {
  override suspend fun doConfigFetch(
    headerOptions: Map<String, String>,
    onSuccess: suspend ((JSONObject)) -> Unit,
    onFailure: suspend () -> Unit
  ) {
    val connection = settingsUrl().openConnection() as HttpsURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/json")
    headerOptions.forEach { connection.setRequestProperty(it.key, it.value) }

    val responseCode = connection.responseCode
    if (responseCode == HttpsURLConnection.HTTP_OK) {
      val inputStream = connection.inputStream
      val bufferedReader = BufferedReader(InputStreamReader(inputStream))
      val response = StringBuilder()
      var inputLine: String?
      while (bufferedReader.readLine().also { inputLine = it } != null) {
        response.append(inputLine)
      }
      bufferedReader.close()
      inputStream.close()

      val responseJson = JSONObject(response.toString())
      onSuccess(responseJson)
    } else {
      onFailure()
    }
  }

  fun settingsUrl(): URL {
    var uri =
      Uri.Builder()
        .scheme("https")
        .authority(FIREBASE_SESSIONS_BASE_URL_STRING)
        .appendPath("spi")
        .appendPath("v2")
        .appendPath("platforms")
        .appendPath(FIREBASE_PLATFORM)
        .appendPath("gmp")
        .appendPath(appInfo.appId)
        .appendPath("settings")
    // TODO(visum) Setup build version and display version
    // .appendQueryParameter("build_version", "")
    // .appendQueryParameter("display_version", "")

    return URL(uri.build().toString())
  }

  companion object {
    private const val FIREBASE_SESSIONS_BASE_URL_STRING = "firebase-settings.crashlytics.com"
    private const val FIREBASE_PLATFORM = "android"
  }
}
