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
import android.util.Log
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.util.nextAlphanumericString
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

private const val TAG = "FDCTestAppCheckProvider"

class DataConnectTestCustomAppCheckProvider(firebaseApp: FirebaseApp, private val appId: String) :
  AppCheckProvider {

  private val applicationContext: Context = firebaseApp.applicationContext
  private val projectId = requireNotNull(firebaseApp.options.projectId)

  override fun getToken(): Task<AppCheckToken> {
    val invocationId = "acgt" + Random.nextAlphanumericString(length = 8)
    Log.d(TAG, "[$invocationId] getToken() called")
    val tcs = TaskCompletionSource<AppCheckToken>()

    thread(name = "DataConnectTestCustomAppCheckProvider") {
      runCatching { doTokenRefresh() }
        .fold(
          onSuccess = {
            Log.i(
              TAG,
              "[$invocationId] getToken() succeeded with token: " + it.token.toScrubbedAccessToken()
            )
            tcs.setResult(it)
          },
          onFailure = {
            Log.e(TAG, "[$invocationId] getToken() failed", it)
            tcs.setException(if (it is Exception) it else Exception(it))
          },
        )
    }

    return tcs.task
  }

  private fun doTokenRefresh(): DataConnectTestAppCheckToken {
    val account = loadServiceAccount(FIREBASE_ADMIN_SERVICE_ACCOUNT_ASSET_PATH)
    val authToken = GoogleAuthTokenRetriever(account).run()
    return AppCheckTokenRetriever(account, authToken, projectId, appId).run()
  }

  private fun loadServiceAccount(
    @Suppress("SameParameterValue") assetPath: String
  ): FirebaseAdminServiceAccount {
    val account = FirebaseAdminServiceAccount.fromAssetFile(applicationContext, assetPath)
    if (account.projectId != projectId) {
      throw ProjectIdMismatchException(
        "Project ID loaded from service account file $assetPath (${account.projectId})" +
          " does not match the Project ID of the FirebaseApp ($projectId)" +
          " (error code axhahc4e2q)"
      )
    }
    return account
  }

  private class ProjectIdMismatchException(message: String) : Exception(message)

  private companion object {
    const val FIREBASE_ADMIN_SERVICE_ACCOUNT_ASSET_PATH = "firebase-admin-service-account.key.json"
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

private data class GoogleAuthToken(
  val accessToken: String,
  val tokenType: String,
)

private data class FirebaseAdminServiceAccount(
  val privateKey: RSAPrivateKey,
  val projectId: String,
  val clientEmail: String,
) {

  private class FirebaseAdminServiceAccountAssetFileException(
    message: String,
    cause: Throwable? = null
  ) : Exception(message, cause)

  companion object {
    private const val EXPECTED_PRIVATE_KEY_PREFIX = "-----BEGIN PRIVATE KEY-----\n"
    private const val EXPECTED_PRIVATE_KEY_SUFFIX = "\n-----END PRIVATE KEY-----\n"

    private fun String.withEscapedNewlines(): String = replace("\n", "\\n")

    fun fromAssetFile(context: Context, assetPath: String): FirebaseAdminServiceAccount {
      val json = Json { ignoreUnknownKeys = true }

      @Serializable
      data class SerializedFirebaseAdminServiceAccount(
        @SerialName("project_id") val projectId: String,
        @SerialName("private_key") val privateKey: String,
        @SerialName("client_email") val clientEmail: String,
      )

      val serviceAccount =
        try {
          context.assets.open(assetPath).use {
            json.decodeFromStream<SerializedFirebaseAdminServiceAccount>(it)
          }
        } catch (e: Exception) {
          throw FirebaseAdminServiceAccountAssetFileException(
            "loading from service account asset file $assetPath failed: $e" +
              " (error code kqv4a3wekv)",
            e
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
            "base64 decoding of private key in service account asset file $assetPath failed: $e" +
              " (error code 45cq3mqyjx)",
            e
          )
        }

      val keyFactory = KeyFactory.getInstance("RSA")
      val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
      val privateKey = keyFactory.generatePrivate(keySpec)

      return FirebaseAdminServiceAccount(
        privateKey = privateKey as RSAPrivateKey,
        projectId = serviceAccount.projectId,
        clientEmail = serviceAccount.clientEmail,
      )
    }
  }
}

private class AppCheckTokenRetriever(
  private val account: FirebaseAdminServiceAccount,
  private val authToken: GoogleAuthToken,
  projectId: String,
  private val appId: String
) {

  private val exchangeTokenUrl =
    "https://firebaseappcheck.googleapis.com/v1/projects/$projectId/apps/$appId:exchangeCustomToken"

  fun run(): DataConnectTestAppCheckToken {
    val json = Json { ignoreUnknownKeys = true }

    @Serializable data class ExchangeTokenRequest(val customToken: String)
    val request = ExchangeTokenRequest(customToken = createFirebaseJavaWebToken(account))

    val connection = URL(exchangeTokenUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "Bearer ${authToken.accessToken}")
    connection.doOutput = true

    Log.i(TAG, "Sending exchange token refresh request to ${connection.url}")
    connection.outputStream.use { json.encodeToStream(request, it) }

    val responseCode = connection.responseCode
    if (responseCode != 200) {
      throw AppCheckTokenRetrieverException(
        "Unexpected response code from $exchangeTokenUrl: $responseCode " +
          "(error code bsywkft8rq)"
      )
    }

    @Serializable data class ExchangeTokenResponse(val token: String, val ttl: String)
    val response = connection.inputStream.use { json.decodeFromStream<ExchangeTokenResponse>(it) }

    val ttlMillis = response.ttl.drop(1).toLong()
    return DataConnectTestAppCheckToken(token = response.token, expireTimeMillis = ttlMillis).also {
      Log.i(
        TAG,
        "Exchange token refresh request succeeded with token: " + it.token.toScrubbedAccessToken()
      )
    }
  }

  private fun createFirebaseJavaWebToken(account: FirebaseAdminServiceAccount): String {
    val algorithm: Algorithm = Algorithm.RSA256(null, account.privateKey)

    val issueTime = Date()
    val expiryTime = Date(issueTime.time + 5.minutes.inWholeMilliseconds)

    return JWT.create()
      .withIssuer(account.clientEmail)
      .withAudience(FIREBASE_APP_CHECK_AUDIENCE)
      .withIssuedAt(issueTime)
      .withExpiresAt(expiryTime)
      .withSubject(account.clientEmail)
      .withClaim("app_id", appId)
      .sign(algorithm)
  }

  private class AppCheckTokenRetrieverException(message: String) : Exception(message)

  private companion object {
    const val FIREBASE_APP_CHECK_AUDIENCE =
      "https://firebaseappcheck.googleapis.com/google.firebase.appcheck.v1.TokenExchangeService"
  }
}

private class GoogleAuthTokenRetriever(private val account: FirebaseAdminServiceAccount) {

  fun run(): GoogleAuthToken {
    val json = Json { ignoreUnknownKeys = true }
    val token = createGoogleAuthJavaWebToken()
    val requestBody =
      "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$token"

    val connection =
      URL("https://accounts.google.com/o/oauth2/token").openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    connection.doOutput = true

    Log.i(TAG, "Sending auth token refresh request to ${connection.url}")
    connection.outputStream.use { it.write(requestBody.encodeToByteArray()) }

    val responseCode = connection.responseCode
    if (responseCode != 200) {
      throw GoogleAuthTokenRetrieverException(
        "Unexpected response code from ${connection.url}: $responseCode " +
          "(error code 6dmw4wv4db)"
      )
    }

    @Serializable
    data class GetAuthTokenResponse(
      @SerialName("access_token") val accessToken: String,
      @SerialName("expires_in") val expiresIn: Long,
      @SerialName("token_type") val tokenType: String,
    )
    val response = connection.inputStream.use { json.decodeFromStream<GetAuthTokenResponse>(it) }

    return GoogleAuthToken(
        accessToken = response.accessToken,
        tokenType = response.tokenType,
      )
      .also {
        Log.i(
          TAG,
          "Auth token refresh request succeeded with token: " +
            it.accessToken.toScrubbedAccessToken()
        )
      }
  }

  private fun createGoogleAuthJavaWebToken(): String {
    val algorithm: Algorithm = Algorithm.RSA256(null, account.privateKey)

    val issueTime = Date()
    val expiryTime = Date(issueTime.time + 1.hours.inWholeMilliseconds)

    return JWT.create()
      .withIssuer(account.clientEmail)
      .withAudience(GOOGLE_TOKEN_AUDIENCE)
      .withIssuedAt(issueTime)
      .withExpiresAt(expiryTime)
      .withClaim("scope", googleTokenScopes.joinToString(" "))
      .sign(algorithm)
  }

  private class GoogleAuthTokenRetrieverException(message: String) : Exception(message)

  private companion object {
    const val GOOGLE_TOKEN_AUDIENCE = "https://accounts.google.com/o/oauth2/token"

    val googleTokenScopes =
      listOf(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/firebase.database",
        "https://www.googleapis.com/auth/firebase.messaging",
        "https://www.googleapis.com/auth/identitytoolkit",
        "https://www.googleapis.com/auth/userinfo.email",
      )
  }
}
