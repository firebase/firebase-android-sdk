/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect.testutil

import android.content.Context
import android.util.Base64
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

class DataConnectTestCustomAppCheckProvider(firebaseApp: FirebaseApp, private val appId: String) :
  AppCheckProvider {

  private val applicationContext: Context = firebaseApp.applicationContext
  private val projectId = firebaseApp.options.projectId
  private val exchangeTokenUrl =
    "https://firebaseappcheck.googleapis.com/v1/projects/$projectId/apps/$appId:exchangeCustomToken"

  override fun getToken(): Task<AppCheckToken> {
    val taskCompletionSource = TaskCompletionSource<AppCheckToken>()
    thread(name = "DataConnectTestCustomAppCheckProvider") {
      val result = runCatching {
        val token = createJavaWebToken()
        val url = URL(exchangeTokenUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { it.write(token.encodeToByteArray()) }
        val responseCode = connection.responseCode
        if (responseCode != 200) {
          throw Exception(
            "Unexpected response code from $exchangeTokenUrl: $responseCode " +
              "(error code bsywkft8rq)"
          )
        }

        @Serializable data class ExchangeTokenResponse(val token: String, val ttl: String)
        val response =
          connection.inputStream.use { Json.decodeFromStream<ExchangeTokenResponse>(it) }

        val ttlMillis = response.ttl.drop(1).toLong()
        println("zzyzx ${Date(ttlMillis)}")
        DataConnectTestAppCheckToken(token = response.token, expireTimeMillis = ttlMillis)
      }
      result.fold(
        onSuccess = { taskCompletionSource.setResult(it) },
        onFailure = {
          taskCompletionSource.setException(if (it is Exception) it else Exception(it))
        }
      )
    }
    return taskCompletionSource.task
  }

  private fun createJavaWebToken(): String {
    val account = loadFirebaseAdminServiceAccount(FIREBASE_ADMIN_SERVICE_ACCOUNT_ASSET_PATH)
    val algorithm: Algorithm = Algorithm.RSA256(null, account.privateKey)

    val issueTime = Date()
    val expiryTime = Date(issueTime.time + 5.minutes.inWholeMilliseconds)

    return JWT.create()
      .withIssuer(account.clientEmail)
      .withSubject(account.clientEmail)
      .withClaim("app_id", appId)
      .withAudience(FIREBASE_APP_CHECK_AUDIENCE)
      .withIssuedAt(issueTime)
      .withExpiresAt(expiryTime)
      .sign(algorithm)
  }

  private fun loadFirebaseAdminServiceAccount(assetPath: String): FirebaseAdminServiceAccount {
    @Serializable
    data class SerializedFirebaseAdminServiceAccount(
      @SerialName("project_id") val projectId: String,
      @SerialName("private_key") val privateKey: String,
      @SerialName("client_email") val clientEmail: String,
    )

    val serviceAccount =
      try {
        applicationContext.assets.open(assetPath).use {
          Json.decodeFromStream<SerializedFirebaseAdminServiceAccount>(it)
        }
      } catch (e: Exception) {
        throw FirebaseAdminServiceAccountAssetFileException(
          "loading from service account asset file failed: $assetPath",
          e
        )
      }

    if (serviceAccount.projectId != projectId) {
      throw FirebaseAdminServiceAccountAssetFileException(
        "Project ID loaded from service account file $assetPath (${serviceAccount.projectId})" +
          " does not equal the Project ID from the FirebaseApp ($projectId)" +
          " (error code: ae547ynhw7)"
      )
    }
    val privateKeyPrefix = serviceAccount.privateKey.take(EXPECTED_PRIVATE_KEY_PREFIX.length)
    if (privateKeyPrefix != EXPECTED_PRIVATE_KEY_PREFIX) {
      throw FirebaseAdminServiceAccountAssetFileException(
        "Invalid private key loaded from service account file $assetPath: " +
          " expected it to start with ${EXPECTED_PRIVATE_KEY_PREFIX.withEscapedNewlines()} " +
          " but it actually started with: " +
          privateKeyPrefix.withEscapedNewlines() +
          " (error code bvgfrmj7e7)"
      )
    }
    val privateKeySuffix =
      serviceAccount.privateKey
        .drop(EXPECTED_PRIVATE_KEY_PREFIX.length)
        .takeLast(EXPECTED_PRIVATE_KEY_SUFFIX.length)
    if (privateKeySuffix != EXPECTED_PRIVATE_KEY_SUFFIX) {
      throw FirebaseAdminServiceAccountAssetFileException(
        "Invalid private key loaded from service account file $assetPath: " +
          " expected it to end with " +
          EXPECTED_PRIVATE_KEY_SUFFIX.withEscapedNewlines() +
          " but it actually ended with: " +
          privateKeySuffix.withEscapedNewlines() +
          " (error code hr27bmxm4h)"
      )
    }

    val base64EncodedPrivateKey =
      serviceAccount.privateKey
        .drop(privateKeyPrefix.length)
        .dropLast(privateKeySuffix.length)
        .replace("\n", "")
    val privateKeyBytes: ByteArray =
      try {
        Base64.decode(base64EncodedPrivateKey, Base64.DEFAULT)
      } catch (e: Exception) {
        throw FirebaseAdminServiceAccountAssetFileException(
          "base64 decoding of private key in service account asset file failed: $assetPath",
          e
        )
      }

    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
    val privateKey = keyFactory.generatePrivate(keySpec)
    return FirebaseAdminServiceAccount(
      privateKey = privateKey as RSAPrivateKey,
      clientEmail = serviceAccount.clientEmail,
    )
  }

  private data class FirebaseAdminServiceAccount(
    val privateKey: RSAPrivateKey,
    val clientEmail: String,
  )

  class FirebaseAdminServiceAccountAssetFileException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

  private companion object {
    /** Audience to use for Firebase App Check Custom tokens */
    const val FIREBASE_APP_CHECK_AUDIENCE =
      "https://firebaseappcheck.googleapis.com/google.firebase.appcheck.v1.TokenExchangeService"

    const val FIREBASE_ADMIN_SERVICE_ACCOUNT_ASSET_PATH = "firebase-admin-service-account.key.json"
    const val EXPECTED_PRIVATE_KEY_PREFIX = "-----BEGIN PRIVATE KEY-----\n"
    const val EXPECTED_PRIVATE_KEY_SUFFIX = "\n-----END PRIVATE KEY-----\n"

    fun String.withEscapedNewlines(): String = replace("\n", "\\n")
  }
}

class DataConnectTestAppCheckToken(
  private val token: String,
  private val expireTimeMillis: Long,
) : AppCheckToken() {
  override fun getToken(): String = token

  override fun getExpireTimeMillis(): Long = expireTimeMillis
}

class DataConnectTestCustomAppCheckProviderFactory(private val appId: String) :
  AppCheckProviderFactory {
  override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
    return DataConnectTestCustomAppCheckProvider(firebaseApp, appId)
  }
}
