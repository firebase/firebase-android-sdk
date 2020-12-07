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

package com.google.firebase.functions;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.gms.security.ProviderInstaller.ProviderInstallListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.emulators.EmulatedServiceSettings;
import com.google.firebase.functions.FirebaseFunctionsException.Code;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

/** FirebaseFunctions lets you call Cloud Functions for Firebase. */
public class FirebaseFunctions {

  /** A task that will be resolved once ProviderInstaller has installed what it needs to. */
  private static final TaskCompletionSource<Void> providerInstalled = new TaskCompletionSource<>();

  /**
   * Whether the ProviderInstaller async task has been started. This is guarded by the
   * providerInstalled lock.
   */
  private static boolean providerInstallStarted = false;

  // The FirebaseApp instance
  private final FirebaseApp app;

  // The network client to use for HTTPS requests.
  private final OkHttpClient client;

  // A serializer to encode/decode parameters and return values.
  private final Serializer serializer;

  // A provider of client metadata to include with calls.
  private final ContextProvider contextProvider;

  // The projectId to use for all functions references.
  private final String projectId;

  // The region to use for all function references.
  private final String region;

  // A custom domain for the http trigger, such as "https://mydomain.com"
  @Nullable private final String customDomain;

  // The format to use for constructing urls from region, projectId, and name.
  private String urlFormat = "https://%1$s-%2$s.cloudfunctions.net/%3$s";

  // Emulator settings
  @Nullable private EmulatedServiceSettings emulatorSettings;

  FirebaseFunctions(
      FirebaseApp app,
      Context context,
      String projectId,
      String regionOrCustomDomain,
      ContextProvider contextProvider) {
    this.app = app;
    this.client = new OkHttpClient();
    this.serializer = new Serializer();
    this.contextProvider = Preconditions.checkNotNull(contextProvider);
    this.projectId = Preconditions.checkNotNull(projectId);

    boolean isRegion;
    try {
      new URL(regionOrCustomDomain);
      isRegion = false;
    } catch (MalformedURLException malformedURLException) {
      isRegion = true;
    }

    if (isRegion) {
      this.region = regionOrCustomDomain;
      this.customDomain = null;
    } else {
      this.region = "us-central1";
      this.customDomain = regionOrCustomDomain;
    }

    maybeInstallProviders(context);
  }

  /**
   * Runs ProviderInstaller.installIfNeededAsync once per application instance.
   *
   * @param context The application context.
   */
  private static void maybeInstallProviders(Context context) {
    // Make sure this only runs once.
    synchronized (providerInstalled) {
      if (providerInstallStarted) {
        return;
      }
      providerInstallStarted = true;
    }

    // Package installIfNeededAsync into a Runnable so it can be run on the main thread.
    // installIfNeededAsync checks to make sure it is on the main thread, and throws otherwise.
    Runnable runnable =
        () ->
            ProviderInstaller.installIfNeededAsync(
                context,
                new ProviderInstallListener() {
                  @Override
                  public void onProviderInstalled() {
                    providerInstalled.setResult(null);
                  }

                  @Override
                  public void onProviderInstallFailed(int i, android.content.Intent intent) {
                    Log.d("FirebaseFunctions", "Failed to update ssl context");
                    providerInstalled.setResult(null);
                  }
                });

    Handler handler = new Handler(context.getMainLooper());
    handler.post(runnable);
  }

  /**
   * Creates a Cloud Functions client with the given app and region or custom domain.
   *
   * @param app The app for the Firebase project.
   * @param regionOrCustomDomain The region or custom domain for the HTTPS trigger, such as
   *     "us-central1" or "https://mydomain.com".
   */
  @NonNull
  public static FirebaseFunctions getInstance(
      @NonNull FirebaseApp app, @NonNull String regionOrCustomDomain) {
    Preconditions.checkNotNull(app, "You must call FirebaseApp.initializeApp first.");
    Preconditions.checkNotNull(regionOrCustomDomain);

    FunctionsMultiResourceComponent component = app.get(FunctionsMultiResourceComponent.class);
    Preconditions.checkNotNull(component, "Functions component does not exist.");

    return component.get(regionOrCustomDomain);
  }

  /**
   * Creates a Cloud Functions client with the given app.
   *
   * @param app The app for the Firebase project.
   */
  @NonNull
  public static FirebaseFunctions getInstance(@NonNull FirebaseApp app) {
    return getInstance(app, "us-central1");
  }

  /**
   * Creates a Cloud Functions client with the default app and given region or custom domain.
   *
   * @param regionOrCustomDomain The regionOrCustomDomain for the HTTPS trigger, such as
   *     "us-central1" or "https://mydomain.com".
   */
  @NonNull
  public static FirebaseFunctions getInstance(@NonNull String regionOrCustomDomain) {
    return getInstance(FirebaseApp.getInstance(), regionOrCustomDomain);
  }

  /** Creates a Cloud Functions client with the default app. */
  @NonNull
  public static FirebaseFunctions getInstance() {
    return getInstance(FirebaseApp.getInstance(), "us-central1");
  }

  /** Returns a reference to the Callable HTTPS trigger with the given name. */
  @NonNull
  public HttpsCallableReference getHttpsCallable(@NonNull String name) {
    return new HttpsCallableReference(this, name);
  }

  /**
   * Returns the URL for a particular function.
   *
   * @param function The name of the function.
   * @return The URL.
   */
  @VisibleForTesting
  URL getURL(String function) {
    EmulatedServiceSettings emulatorSettings = this.emulatorSettings;
    if (emulatorSettings != null) {
      urlFormat =
          "http://"
              + emulatorSettings.getHost()
              + ":"
              + emulatorSettings.getPort()
              + "/%2$s/%1$s/%3$s";
    }

    String str = String.format(urlFormat, region, projectId, function);

    if (customDomain != null && emulatorSettings == null) {
      str = customDomain + "/" + function;
    }

    try {
      return new URL(str);
    } catch (MalformedURLException mfe) {
      throw new IllegalStateException(mfe);
    }
  }

  /** @deprecated Use {@link #useEmulator(String, int)} to connect to the emulator. */
  public void useFunctionsEmulator(@NonNull String origin) {
    Preconditions.checkNotNull(origin, "origin cannot be null");
    urlFormat = origin + "/%2$s/%1$s/%3$s";
  }

  /**
   * Modifies this FirebaseFunctions instance to communicate with the Cloud Functions emulator.
   *
   * <p>Note: Call this method before using the instance to do any functions operations.
   *
   * @param host the emulator host (for example, 10.0.2.2)
   * @param port the emulator port (for example, 5001)
   */
  public void useEmulator(@NonNull String host, int port) {
    this.emulatorSettings = new EmulatedServiceSettings(host, port);
  }

  /**
   * Calls a Callable HTTPS trigger endpoint.
   *
   * @param name The name of the HTTPS trigger.
   * @param data Parameters to pass to the function. Can be anything encodable as JSON.
   * @return A Task that will be completed when the request is complete.
   */
  Task<HttpsCallableResult> call(String name, @Nullable Object data, HttpsCallOptions options) {
    return providerInstalled
        .getTask()
        .continueWithTask(task -> contextProvider.getContext())
        .continueWithTask(
            task -> {
              if (!task.isSuccessful()) {
                return Tasks.forException(task.getException());
              }
              HttpsCallableContext context = task.getResult();
              return call(name, data, context, options);
            });
  }

  /**
   * Calls a Callable HTTPS trigger endpoint.
   *
   * @param name The name of the HTTPS trigger.
   * @param data Parameters to pass to the function. Can be anything encodable as JSON.
   * @param context Metadata to supply with the function call.
   * @return A Task that will be completed when the request is complete.
   */
  private Task<HttpsCallableResult> call(
      @NonNull String name,
      @Nullable Object data,
      HttpsCallableContext context,
      HttpsCallOptions options) {
    Preconditions.checkNotNull(name, "name cannot be null");
    URL url = getURL(name);

    Map<String, Object> body = new HashMap<>();

    Object encoded = serializer.encode(data);
    body.put("data", encoded);

    JSONObject bodyJSON = new JSONObject(body);
    MediaType contentType = MediaType.parse("application/json");
    RequestBody requestBody = RequestBody.create(contentType, bodyJSON.toString());

    Request.Builder request = new Request.Builder().url(url).post(requestBody);
    if (context.getAuthToken() != null) {
      request = request.header("Authorization", "Bearer " + context.getAuthToken());
    }
    if (context.getInstanceIdToken() != null) {
      request = request.header("Firebase-Instance-ID-Token", context.getInstanceIdToken());
    }

    OkHttpClient callClient = options.apply(client);
    Call call = callClient.newCall(request.build());

    TaskCompletionSource<HttpsCallableResult> tcs = new TaskCompletionSource<>();
    call.enqueue(
        new Callback() {
          @Override
          public void onFailure(Call ignored, IOException e) {
            if (e instanceof InterruptedIOException) {
              FirebaseFunctionsException exception =
                  new FirebaseFunctionsException(
                      Code.DEADLINE_EXCEEDED.name(), Code.DEADLINE_EXCEEDED, null, e);
              tcs.setException(exception);
            } else {
              FirebaseFunctionsException exception =
                  new FirebaseFunctionsException(Code.INTERNAL.name(), Code.INTERNAL, null, e);
              tcs.setException(exception);
            }
          }

          @Override
          public void onResponse(Call ignored, Response response) throws IOException {
            Code code = Code.fromHttpStatus(response.code());
            String body = response.body().string();

            FirebaseFunctionsException exception =
                FirebaseFunctionsException.fromResponse(code, body, serializer);
            if (exception != null) {
              tcs.setException(exception);
              return;
            }

            JSONObject bodyJSON;
            try {
              bodyJSON = new JSONObject(body);
            } catch (JSONException je) {
              Exception e =
                  new FirebaseFunctionsException(
                      "Response is not valid JSON object.", Code.INTERNAL, null, je);
              tcs.setException(e);
              return;
            }

            Object dataJSON = bodyJSON.opt("data");
            // TODO: Allow "result" instead of "data" for now, for backwards compatibility.
            if (dataJSON == null) {
              dataJSON = bodyJSON.opt("result");
            }
            if (dataJSON == null) {
              Exception e =
                  new FirebaseFunctionsException(
                      "Response is missing data field.", Code.INTERNAL, null);
              tcs.setException(e);
              return;
            }

            HttpsCallableResult result = new HttpsCallableResult(serializer.decode(dataJSON));
            tcs.setResult(result);
          }
        });
    return tcs.getTask();
  }
}
