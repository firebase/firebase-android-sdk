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

package com.google.firebase.inappmessaging.internal;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build.VERSION;
import android.text.TextUtils;
import com.google.developers.mobile.targeting.proto.ClientSignalsProto.ClientSignals;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpressionList;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.ClientAppInfo;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsRequest;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import dagger.Lazy;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Interface to speak to the fiam backend
 *
 * @hide
 */
@FirebaseAppScope
public class ApiClient {

  private static final String FETCHING_CAMPAIGN_MESSAGE = "Fetching campaigns from service.";

  private final Lazy<GrpcClient> grpcClient;
  private final FirebaseApp firebaseApp;
  private final Application application;
  private final Clock clock;
  private final ProviderInstaller providerInstaller;

  public ApiClient(
      Lazy<GrpcClient> grpcClient,
      FirebaseApp firebaseApp,
      Application application,
      Clock clock,
      ProviderInstaller providerInstaller) {
    this.grpcClient = grpcClient;
    this.firebaseApp = firebaseApp;
    this.application = application;
    this.clock = clock;
    this.providerInstaller = providerInstaller;
  }

  FetchEligibleCampaignsResponse getFiams(
      InstallationIdResult installationIdResult, CampaignImpressionList impressionList) {
    Logging.logi(FETCHING_CAMPAIGN_MESSAGE);
    providerInstaller.install();

    return withCacheExpirationSafeguards(
        grpcClient
            .get()
            .fetchEligibleCampaigns(
                FetchEligibleCampaignsRequest.newBuilder()
                    // The project Id we expect is the gcm sender id
                    .setProjectNumber(firebaseApp.getOptions().getGcmSenderId())
                    .addAllAlreadySeenCampaigns(impressionList.getAlreadySeenCampaignsList())
                    .setClientSignals(getClientSignals())
                    .setRequestingClientApp(getClientAppInfo(installationIdResult))
                    .build()));
  }

  private FetchEligibleCampaignsResponse withCacheExpirationSafeguards(
      FetchEligibleCampaignsResponse resp) {
    if (resp.getExpirationEpochTimestampMillis() < clock.now() + TimeUnit.MINUTES.toMillis(1)
        || resp.getExpirationEpochTimestampMillis() > clock.now() + TimeUnit.DAYS.toMillis(3)) {
      // we default to minimum 1 day if the expiration passed from the service is less than 1 minute
      return resp.toBuilder()
          .setExpirationEpochTimestampMillis(clock.now() + TimeUnit.DAYS.toMillis(1))
          .build();
    }

    return resp;
  }

  private ClientSignals getClientSignals() {
    ClientSignals.Builder clientSignals =
        ClientSignals.newBuilder()
            .setPlatformVersion(String.valueOf(VERSION.SDK_INT))
            // toString is needed here to support API versions lower than 21.
            .setLanguageCode(Locale.getDefault().toString())
            .setTimeZone(TimeZone.getDefault().getID());

    String versionName = getVersionName();
    if (!TextUtils.isEmpty(versionName)) {
      clientSignals.setAppVersion(versionName);
    }

    return clientSignals.build();
  }

  private ClientAppInfo getClientAppInfo(InstallationIdResult installationIdResult) {
    return ClientAppInfo.newBuilder()
        .setGmpAppId(firebaseApp.getOptions().getApplicationId())
        .setAppInstanceId(installationIdResult.installationId())
        .setAppInstanceIdToken(installationIdResult.installationTokenResult().getToken())
        .build();
  }

  @Nullable
  private String getVersionName() {
    try {
      PackageInfo pInfo =
          application.getPackageManager().getPackageInfo(application.getPackageName(), 0);
      return pInfo.versionName;
    } catch (NameNotFoundException e) {
      Logging.loge("Error finding versionName : " + e.getMessage());
    }
    return null;
  }
}
