// Copyright 2018 Google LLC
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
package com.google.firebase.functions

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.android.gms.common.internal.Preconditions
import com.google.android.gms.security.ProviderInstaller
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.annotations.concurrent.UiThread
import com.google.firebase.emulators.EmulatedServiceSettings
import com.google.firebase.functions.FirebaseFunctionsException.Code.Companion.fromHttpStatus
import com.google.firebase.functions.FirebaseFunctionsException.Companion.fromResponse
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.Executor
import javax.inject.Named
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

/** FirebaseFunctions lets you call Cloud Functions for Firebase. */
class FirebaseFunctions
@AssistedInject
internal constructor(
  context: Context,
  @Named("projectId") projectId: String?,
  @Assisted regionOrCustomDomain: String?,
  contextProvider: ContextProvider?,
  @param:Lightweight private val executor: Executor,
  @UiThread uiExecutor: Executor
) {
  // The network client to use for HTTPS requests.
  private val client: OkHttpClient

  // A serializer to encode/decode parameters and return values.
  private val serializer: Serializer

  // A provider of client metadata to include with calls.
  private val contextProvider: ContextProvider

  // The projectId to use for all functions references.
  private val projectId: String

  // The region to use for all function references.
  private var region: String? = null

  // A custom domain for the http trigger, such as "https://mydomain.com"
  private var customDomain: String? = null

  // The format to use for constructing urls from region, projectId, and name.
  private var urlFormat = "https://%1\$s-%2\$s.cloudfunctions.net/%3\$s"

  // Emulator settings
  private var emulatorSettings: EmulatedServiceSettings? = null

  init {
    client = OkHttpClient()
    serializer = Serializer()
    this.contextProvider = Preconditions.checkNotNull(contextProvider)
    this.projectId = Preconditions.checkNotNull(projectId)
    val isRegion: Boolean
    isRegion =
      try {
        URL(regionOrCustomDomain)
        false
      } catch (malformedURLException: MalformedURLException) {
        true
      }
    if (isRegion) {
      region = regionOrCustomDomain
      customDomain = null
    } else {
      region = "us-central1"
      customDomain = regionOrCustomDomain
    }
    maybeInstallProviders(context, uiExecutor)
  }

  /** Returns a reference to the callable HTTPS trigger with the given name. */
  fun getHttpsCallable(name: String): HttpsCallableReference {
    return HttpsCallableReference(this, name, HttpsCallOptions())
  }

  /** Returns a reference to the callable HTTPS trigger with the provided URL. */
  fun getHttpsCallableFromUrl(url: URL): HttpsCallableReference {
    return HttpsCallableReference(this, url, HttpsCallOptions())
  }

  /** Returns a reference to the callable HTTPS trigger with the given name and call options. */
  fun getHttpsCallable(name: String, options: HttpsCallableOptions): HttpsCallableReference {
    return HttpsCallableReference(this, name, HttpsCallOptions(options))
  }

  /** Returns a reference to the callable HTTPS trigger with the provided URL and call options. */
  fun getHttpsCallableFromUrl(url: URL, options: HttpsCallableOptions): HttpsCallableReference {
    return HttpsCallableReference(this, url, HttpsCallOptions(options))
  }

  /**
   * Returns the URL for a particular function.
   *
   * @param function The name of the function.
   * @return The URL.
   */
  @VisibleForTesting
  fun getURL(function: String): URL {
    val emulatorSettings = emulatorSettings
    if (emulatorSettings != null) {
      urlFormat =
        ("http://" + emulatorSettings.host + ":" + emulatorSettings.port + "/%2\$s/%1\$s/%3\$s")
    }
    var str = String.format(urlFormat, region, projectId, function)
    if (customDomain != null && emulatorSettings == null) {
      str = "$customDomain/$function"
    }
    return try {
      URL(str)
    } catch (mfe: MalformedURLException) {
      throw IllegalStateException(mfe)
    }
  }

  @Deprecated("Use {@link #useEmulator(String, int)} to connect to the emulator. ")
  fun useFunctionsEmulator(origin: String) {
    Preconditions.checkNotNull(origin, "origin cannot be null")
    urlFormat = "$origin/%2\$s/%1\$s/%3\$s"
  }

  /**
   * Modifies this FirebaseFunctions instance to communicate with the Cloud Functions emulator.
   *
   * Note: Call this method before using the instance to do any functions operations.
   *
   * @param host the emulator host (for example, 10.0.2.2)
   * @param port the emulator port (for example, 5001)
   */
  fun useEmulator(host: String, port: Int) {
    emulatorSettings = EmulatedServiceSettings(host, port)
  }

  /**
   * Calls a Callable HTTPS trigger endpoint.
   *
   * @param name The name of the HTTPS trigger.
   * @param data Parameters to pass to the function. Can be anything encodable as JSON.
   * @return A Task that will be completed when the request is complete.
   */
  fun call(name: String, data: Any?, options: HttpsCallOptions): Task<HttpsCallableResult?> {
    return providerInstalled.task
      .continueWithTask(executor) { task: Task<Void>? ->
        contextProvider.getContext(options.limitedUseAppCheckTokens)
      }
      .continueWithTask(executor) { task: Task<HttpsCallableContext?> ->
        if (!task.isSuccessful) {
          return@continueWithTask Tasks.forException<HttpsCallableResult>(task.exception!!)
        }
        val context = task.result
        val url = getURL(name)
        call(url, data, context, options)
      }
  }

  /**
   * Calls a Callable HTTPS trigger endpoint.
   *
   * @param url The url of the HTTPS trigger
   * @param data Parameters to pass to the function. Can be anything encodable as JSON.
   * @return A Task that will be completed when the request is complete.
   */
  fun call(url: URL, data: Any?, options: HttpsCallOptions): Task<HttpsCallableResult?> {
    return providerInstalled.task
      .continueWithTask(executor) { task: Task<Void>? ->
        contextProvider.getContext(options.limitedUseAppCheckTokens)
      }
      .continueWithTask(executor) { task: Task<HttpsCallableContext?> ->
        if (!task.isSuccessful) {
          return@continueWithTask Tasks.forException<HttpsCallableResult>(task.exception!!)
        }
        val context = task.result
        call(url, data, context, options)
      }
  }

  /**
   * Calls a Callable HTTPS trigger endpoint.
   *
   * @param url The name of the HTTPS trigger.
   * @param data Parameters to pass to the function. Can be anything encodable as JSON.
   * @param context Metadata to supply with the function call.
   * @return A Task that will be completed when the request is complete.
   */
  private fun call(
    url: URL,
    data: Any?,
    context: HttpsCallableContext?,
    options: HttpsCallOptions
  ): Task<HttpsCallableResult?> {
    Preconditions.checkNotNull(url, "url cannot be null")
    val body: MutableMap<String?, Any?> = HashMap()
    val encoded = serializer.encode(data)
    body["data"] = encoded
    val bodyJSON = JSONObject(body)
    val contentType = MediaType.parse("application/json")
    val requestBody = RequestBody.create(contentType, bodyJSON.toString())
    var request = Request.Builder().url(url).post(requestBody)
    if (context!!.authToken != null) {
      request = request.header("Authorization", "Bearer " + context.authToken)
    }
    if (context.instanceIdToken != null) {
      request = request.header("Firebase-Instance-ID-Token", context.instanceIdToken)
    }
    if (context.appCheckToken != null) {
      request = request.header("X-Firebase-AppCheck", context.appCheckToken)
    }
    val callClient = options.apply(client)
    val call = callClient.newCall(request.build())
    val tcs = TaskCompletionSource<HttpsCallableResult?>()
    call.enqueue(
      object : Callback {
        override fun onFailure(ignored: Call, e: IOException) {
          if (e is InterruptedIOException) {
            val exception =
              FirebaseFunctionsException(
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED.name,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                null,
                e
              )
            tcs.setException(exception)
          } else {
            val exception =
              FirebaseFunctionsException(
                FirebaseFunctionsException.Code.INTERNAL.name,
                FirebaseFunctionsException.Code.INTERNAL,
                null,
                e
              )
            tcs.setException(exception)
          }
        }

        @Throws(IOException::class)
        override fun onResponse(ignored: Call, response: Response) {
          val code = fromHttpStatus(response.code())
          val body = response.body()!!.string()
          val exception = fromResponse(code, body, serializer)
          if (exception != null) {
            tcs.setException(exception)
            return
          }
          val bodyJSON: JSONObject
          bodyJSON =
            try {
              JSONObject(body)
            } catch (je: JSONException) {
              val e: Exception =
                FirebaseFunctionsException(
                  "Response is not valid JSON object.",
                  FirebaseFunctionsException.Code.INTERNAL,
                  null,
                  je
                )
              tcs.setException(e)
              return
            }
          var dataJSON = bodyJSON.opt("data")
          // TODO: Allow "result" instead of "data" for now, for backwards compatibility.
          if (dataJSON == null) {
            dataJSON = bodyJSON.opt("result")
          }
          if (dataJSON == null) {
            val e: Exception =
              FirebaseFunctionsException(
                "Response is missing data field.",
                FirebaseFunctionsException.Code.INTERNAL,
                null
              )
            tcs.setException(e)
            return
          }
          val result = HttpsCallableResult(serializer.decode(dataJSON))
          tcs.setResult(result)
        }
      }
    )
    return tcs.task
  }

  companion object {
    /** A task that will be resolved once ProviderInstaller has installed what it needs to. */
    private val providerInstalled = TaskCompletionSource<Void>()

    /**
     * Whether the ProviderInstaller async task has been started. This is guarded by the
     * providerInstalled lock.
     */
    private var providerInstallStarted = false

    /**
     * Runs ProviderInstaller.installIfNeededAsync once per application instance.
     *
     * @param context The application context.
     * @param uiExecutor
     */
    private fun maybeInstallProviders(context: Context, uiExecutor: Executor) {
      // Make sure this only runs once.
      synchronized(providerInstalled) {
        if (providerInstallStarted) {
          return
        }
        providerInstallStarted = true
      }

      // Package installIfNeededAsync into a Runnable so it can be run on the main thread.
      // installIfNeededAsync checks to make sure it is on the main thread, and throws otherwise.
      uiExecutor.execute {
        ProviderInstaller.installIfNeededAsync(
          context,
          object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
              providerInstalled.setResult(null)
            }

            override fun onProviderInstallFailed(i: Int, intent: Intent?) {
              Log.d("FirebaseFunctions", "Failed to update ssl context")
              providerInstalled.setResult(null)
            }
          }
        )
      }
    }

    /**
     * Creates a Cloud Functions client with the given app and region or custom domain.
     *
     * @param app The app for the Firebase project.
     * @param regionOrCustomDomain The region or custom domain for the HTTPS trigger, such as
     * `"us-central1"` or `"https://mydomain.com"`.
     */
    @JvmStatic
    fun getInstance(app: FirebaseApp, regionOrCustomDomain: String): FirebaseFunctions {
      Preconditions.checkNotNull(app, "You must call FirebaseApp.initializeApp first.")
      Preconditions.checkNotNull(regionOrCustomDomain)
      val component = app.get(FunctionsMultiResourceComponent::class.java)
      Preconditions.checkNotNull(component, "Functions component does not exist.")
      return component[regionOrCustomDomain]!!
    }

    /**
     * Creates a Cloud Functions client with the given app.
     *
     * @param app The app for the Firebase project.
     */
    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseFunctions {
      return getInstance(app, "us-central1")
    }

    /**
     * Creates a Cloud Functions client with the default app and given region or custom domain.
     *
     * @param regionOrCustomDomain The region or custom domain for the HTTPS trigger, such as
     * `"us-central1"` or `"https://mydomain.com"`.
     */
    @JvmStatic
    fun getInstance(regionOrCustomDomain: String): FirebaseFunctions {
      return getInstance(FirebaseApp.getInstance(), regionOrCustomDomain)
    }

    /** Creates a Cloud Functions client with the default app. */
    @JvmStatic
    fun getInstance(): FirebaseFunctions {
      return getInstance(FirebaseApp.getInstance(), "us-central")
    }
  }
}
