// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.DeliveryMechanism;
import com.google.firebase.crashlytics.internal.common.IdManager;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.settings.SettingsCacheBehavior;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.crashlytics.internal.settings.model.AppRequestData;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.network.CreateAppSpiCall;
import com.google.firebase.crashlytics.internal.settings.network.UpdateAppSpiCall;
import java.util.concurrent.Executor;

public class Onboarding {
  static final String CRASHLYTICS_API_ENDPOINT = "com.crashlytics.ApiEndpoint";
  private final HttpRequestFactory requestFactory;

  private final FirebaseApp app;
  private final Context context;
  private PackageManager packageManager;
  private String packageName;
  private PackageInfo packageInfo;
  private String versionCode;
  private String versionName;
  private String installerPackageName;
  private String applicationLabel;
  private String targetAndroidSdkVersion;

  private IdManager idManager;
  private DataCollectionArbiter dataCollectionArbiter;

  public Onboarding(
      FirebaseApp app,
      Context context,
      IdManager idManager,
      DataCollectionArbiter dataCollectionArbiter) {
    requestFactory = new HttpRequestFactory();
    this.app = app;
    this.context = context;
    this.idManager = idManager;
    this.dataCollectionArbiter = dataCollectionArbiter;
  }

  public Context getContext() {
    return context;
  }

  private static String getVersion() {
    return CrashlyticsCore.getVersion();
  }

  public boolean onPreExecute() {
    try {
      installerPackageName = idManager.getInstallerPackageName();
      packageManager = context.getPackageManager();
      packageName = context.getPackageName();
      packageInfo = packageManager.getPackageInfo(packageName, 0);
      versionCode = Integer.toString(packageInfo.versionCode);
      versionName =
          packageInfo.versionName == null
              ? IdManager.DEFAULT_VERSION_NAME
              : packageInfo.versionName;
      applicationLabel =
          packageManager.getApplicationLabel(context.getApplicationInfo()).toString();
      targetAndroidSdkVersion = Integer.toString(context.getApplicationInfo().targetSdkVersion);

      return true;
    } catch (PackageManager.NameNotFoundException e) {
      Logger.getLogger().e("Failed init", e);
    }
    return false;
  }

  public void doOnboarding(Executor backgroundExecutor, SettingsController settingsDataController) {
    final String googleAppId = app.getOptions().getApplicationId();

    dataCollectionArbiter
        .waitForDataCollectionPermission()
        .onSuccessTask(
            backgroundExecutor,
            new SuccessContinuation<Void, AppSettingsData>() {
              @NonNull
              @Override
              public Task<AppSettingsData> then(@Nullable Void ignored) throws Exception {
                // Now wait on app data settings to be available.
                return settingsDataController.getAppSettings();
              }
            })
        .onSuccessTask(
            backgroundExecutor,
            new SuccessContinuation<AppSettingsData, Void>() {
              @NonNull
              @Override
              public Task<Void> then(@Nullable AppSettingsData appSettingsData) throws Exception {
                try {
                  // We waited for data collection permission, so it's safe to assert this here.
                  final boolean dataCollectionToken = true;
                  performAutoConfigure(
                      appSettingsData,
                      googleAppId,
                      settingsDataController,
                      backgroundExecutor,
                      dataCollectionToken);
                } catch (Exception e) {
                  Logger.getLogger().e("Error performing auto configuration.", e);
                  throw e;
                }
                return null;
              }
            });
  }

  // TODO: Move this outside of Onboarding.
  public SettingsController retrieveSettingsData(
      Context context, FirebaseApp app, Executor backgroundExecutor) {
    final String googleAppId = app.getOptions().getApplicationId();

    SettingsController controller =
        SettingsController.create(
            context,
            googleAppId,
            idManager,
            requestFactory,
            versionCode,
            versionName,
            getOverridenSpiEndpoint(),
            dataCollectionArbiter);

    // Kick off actually fetching the settings.
    controller
        .loadSettingsData(backgroundExecutor)
        .continueWith(
            backgroundExecutor,
            new Continuation<Void, Object>() {
              @Override
              public Object then(@NonNull Task<Void> task) throws Exception {
                if (!task.isSuccessful()) {
                  Logger.getLogger().e("Error fetching settings.", task.getException());
                }
                return null;
              }
            });

    return controller;
  }

  private void performAutoConfigure(
      AppSettingsData appSettings,
      String googleAppId,
      SettingsController settingsController,
      Executor backgroundExecutor,
      boolean dataCollectionToken) {
    if (AppSettingsData.STATUS_NEW.equals(appSettings.status)) {
      // Make the call to create the App on the back end
      if (performCreateApp(appSettings, googleAppId, dataCollectionToken)) {
        // If that's successful, we need one more Settings request to reach the back end to
        // get things running
        settingsController.loadSettingsData(
            SettingsCacheBehavior.SKIP_CACHE_LOOKUP, backgroundExecutor);
      } else {
        // Failed to create the app. We're not configured!
        Logger.getLogger().e("Failed to create app with Crashlytics service.", null);
      }
    } else if (AppSettingsData.STATUS_CONFIGURED.equals(appSettings.status)) {
      // Shouldn't be possible to see this status, but included for completeness.
      settingsController.loadSettingsData(
          SettingsCacheBehavior.SKIP_CACHE_LOOKUP, backgroundExecutor);
    } else if (appSettings.updateRequired) {
      // A non-critical update. We'll attempt it, but won't halt things if we fail, since
      // we're already well configured.
      Logger.getLogger().d("Server says an update is required - forcing a full App update.");
      performUpdateApp(appSettings, googleAppId, dataCollectionToken);
    }
  }

  private boolean performCreateApp(
      AppSettingsData appSettings, String googleAppId, boolean dataCollectionToken) {
    // TODO: Handle the case that org ID is null
    final AppRequestData requestData = buildAppRequest(appSettings.organizationId, googleAppId);
    return new CreateAppSpiCall(
            getOverridenSpiEndpoint(), appSettings.url, requestFactory, getVersion())
        .invoke(requestData, dataCollectionToken);
  }

  private boolean performUpdateApp(
      AppSettingsData appSettings, String googleAppId, boolean dataCollectionToken) {
    // TODO: Handle the case that org ID is null
    final AppRequestData requestData = buildAppRequest(appSettings.organizationId, googleAppId);
    return new UpdateAppSpiCall(
            getOverridenSpiEndpoint(), appSettings.url, requestFactory, getVersion())
        .invoke(requestData, dataCollectionToken);
  }

  private AppRequestData buildAppRequest(String organizationId, String googleAppId) {
    final Context context = getContext();
    final String mappingFileId = CommonUtils.getMappingFileId(context);
    final String instanceId =
        CommonUtils.createInstanceIdFrom(mappingFileId, googleAppId, versionName, versionCode);
    final int source = DeliveryMechanism.determineFrom(installerPackageName).getId();
    final String appIdentifier = getIdManager().getAppIdentifier();

    return new AppRequestData(
        organizationId,
        googleAppId,
        appIdentifier,
        versionName,
        versionCode,
        instanceId,
        applicationLabel,
        source,
        targetAndroidSdkVersion,
        "0");
  }

  String getOverridenSpiEndpoint() {
    return CommonUtils.getStringsFileValue(context, CRASHLYTICS_API_ENDPOINT);
  }

  private IdManager getIdManager() {
    return idManager;
  }
}
