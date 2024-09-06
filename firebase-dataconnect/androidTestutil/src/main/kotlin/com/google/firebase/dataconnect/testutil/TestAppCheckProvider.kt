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
import com.google.android.gms.tasks.Tasks
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

private const val TAG = "FDCTestAppCheckProvider"

/**
 * An App Check provider that creates _real_ App Check tokens from production servers.
 *
 * Normally, a custom App Check provider would make an HTTP call to a server somewhere which would
 * use the https://github.com/firebase/firebase-admin-node SDK to create and return an App Check
 * token. However, that is somewhat inconvenient for integration tests to have this external
 * dependency. So, instead, this provider has ported the logic from AppCheck.createToken() in the
 * firebase-admin-node SDK to Kotlin and makes direct calls to the backend. See instuctions at
 * https://firebase.google.com/docs/app-check/custom-provider for details.
 *
 * In order for this to work, the `google-services.json` must point to a valid project and
 * `androidTestutil/src/main/assets/firebase-admin-service-account.key.json` must be a valid service
 * account key created by the Google Cloud console.
 */
class DataConnectTestAppCheckProviderFactory(
  private val appId: String,
  private val initialToken: String? = null,
) : AppCheckProviderFactory {

  private val _tokens = MutableSharedFlow<AppCheckToken>(replay = Int.MAX_VALUE)
  val tokens: SharedFlow<AppCheckToken> = _tokens.asSharedFlow()

  override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
    return DataConnectTestAppCheckProvider(firebaseApp, appId, initialToken, ::onTokenProduced)
  }

  private fun onTokenProduced(token: AppCheckToken) {
    check(_tokens.tryEmit(token)) {
      "tryEmit() should have succeeded since _tokens is configured with replay=Int.MAX_VALUE" +
        " (error code ty9kkxmqhp)"
    }
  }
}

private class DataConnectTestAppCheckProvider(
  firebaseApp: FirebaseApp,
  private val appId: String,
  private val initialToken: String?,
  private val onTokenProduced: (AppCheckToken) -> Unit,
) : AppCheckProvider {

  private val applicationContext: Context = firebaseApp.applicationContext
  private val projectId = requireNotNull(firebaseApp.options.projectId)
  private val initialTokenUsed = AtomicBoolean(false)

  override fun getToken(): Task<AppCheckToken> {
    Log.d(TAG, "getToken() called")
    val task = getTokenImpl()

    task.addOnCompleteListener {
      if (it.isSuccessful) {
        val appCheckToken = it.result

        val decodedToken = runCatching { JWT.decode(appCheckToken.token) }.getOrNull()
        Log.i(
          TAG,
          "getToken() succeeded with" +
            " token=${appCheckToken.token.toScrubbedAccessToken()}" +
            " expiresAt=${decodedToken?.expiresAt}"
        )
        onTokenProduced(appCheckToken)
      } else {
        Log.e(TAG, "getToken() failed", it.exception)
      }
    }

    return task
  }

  private fun getTokenImpl(): Task<AppCheckToken> {
    if (!initialTokenUsed.getAndSet(true) && initialToken !== null) {
      Log.d(
        TAG,
        "getToken() unconditionally returning initialToken: " + initialToken.toScrubbedAccessToken()
      )
      val expireTimeMillis = Date().time + 1.hours.inWholeMilliseconds
      val appCheckToken = DataConnectTestAppCheckToken(initialToken, expireTimeMillis)
      return Tasks.forResult(appCheckToken)
    }

    val tcs = TaskCompletionSource<AppCheckToken>()

    thread(name = "DataConnectTestCustomAppCheckProvider") {
      runCatching { doTokenRefresh() }
        .fold(
          onSuccess = { tcs.setResult(it) },
          onFailure = { tcs.setException(if (it is Exception) it else Exception(it)) }
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

private data class GoogleAuthToken(
  val accessToken: String,
  val tokenType: String,
  val expiresIn: Long,
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
    val requestBody = json.encodeToString(request).encodeToByteArray()

    val connection = URL(exchangeTokenUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "Bearer ${authToken.accessToken}")
    connection.setRequestProperty("Content-Type", "application/json;charset=utf-8")
    connection.setRequestProperty("Content-Length", "${requestBody.size}")
    connection.doOutput = true

    val requestId = "ect${Random.nextAlphanumericString(length=8)}"
    Log.i(
      TAG,
      "[rid=$requestId]" +
        " Sending exchange token refresh request at ${Date()} to ${connection.url}"
    )
    connection.outputStream.use { it.write(requestBody) }

    val responseCode = connection.responseCode
    Log.i(TAG, "[rid=$requestId] Got HTTP response code $responseCode")
    if (responseCode != 200) {
      throw AppCheckTokenRetrieverException(
        "[rid=$requestId] Unexpected response code from $exchangeTokenUrl: $responseCode " +
          "(error code bsywkft8rq)"
      )
    }

    @Serializable data class ExchangeTokenResponse(val token: String, val ttl: String)
    val response = connection.inputStream.use { json.decodeFromStream<ExchangeTokenResponse>(it) }

    if (!response.ttl.endsWith("s")) {
      throw AppCheckTokenRetrieverException(
        "[rid=$requestId] Expected \"ttl\" in response to end with \"s\"," +
          " but got: ${response.ttl} (error code c2mqk3b5an)"
      )
    }
    val ttlMillis = response.ttl.dropLast(1).toLong()
    val expireTimeMillis = Date().time + ttlMillis

    return DataConnectTestAppCheckToken(response.token, expireTimeMillis).also {
      val decodedToken = runCatching { JWT.decode(it.token) }.getOrNull()
      Log.i(
        TAG,
        "[rid=$requestId] Exchange token refresh request succeeded with" +
          " ttl=${response.ttl} expiresAt=${decodedToken?.expiresAt}" +
          " token=${it.token.toScrubbedAccessToken()}"
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

    val requestId = "atr${Random.nextAlphanumericString(length=8)}"
    Log.i(
      TAG,
      "[rid=$requestId]" + " Sending auth token refresh request at ${Date()} to ${connection.url}"
    )
    connection.outputStream.use { it.write(requestBody.encodeToByteArray()) }

    val responseCode = connection.responseCode
    Log.i(TAG, "[rid=$requestId] Got HTTP response code $responseCode")
    if (responseCode != 200) {
      throw GoogleAuthTokenRetrieverException(
        "[rid=$requestId] Unexpected response code from ${connection.url}: $responseCode " +
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
        expiresIn = response.expiresIn,
      )
      .also {
        val decodedToken = runCatching { JWT.decode(it.accessToken) }.getOrNull()
        Log.i(
          TAG,
          "[rid=$requestId] Auth token refresh request succeeded with" +
            " expiresAt=${decodedToken?.expiresAt}" +
            " expires_in=${it.expiresIn} token_type=${it.tokenType}" +
            " token=${it.accessToken.toScrubbedAccessToken()}"
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
