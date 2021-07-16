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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import androidx.annotation.NonNull;
import com.google.developers.mobile.targeting.proto.ClientSignalsProto.ClientSignals;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.inappmessaging.internal.time.FakeClock;
import com.google.firebase.inappmessaging.internal.time.SystemClock;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpressionList;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.ClientAppInfo;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsRequest;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 22, qualifiers = "es")
public class ApiClientTest {

  public static final String TIME_ZONE = "Europe/London";
  private static final String TEST_PROJECT_NUMBER = "123";
  private static final String CAMPAIGN_ID = "campaign_id";
  private static final String INSTALLATION_ID = "instance_id";
  private static final String INSTALLATION_TOKEN = "instance_token";
  private static final InstallationTokenResult INSTALLATION_TOKEN_RESULT =
      new InstallationTokenResult() {
        @NonNull
        @Override
        public String getToken() {
          return INSTALLATION_TOKEN;
        }

        @Override
        public long getTokenExpirationTimestamp() {
          return 0;
        }

        @Override
        public long getTokenCreationTimestamp() {
          return 0;
        }

        @Override
        public Builder toBuilder() {
          return null;
        }
      };
  private static final InstallationIdResult FID_RESULT =
      InstallationIdResult.create(INSTALLATION_ID, INSTALLATION_TOKEN_RESULT);
  private static final CampaignImpressionList campaignImpressionList =
      CampaignImpressionList.newBuilder()
          .addAlreadySeenCampaigns(
              CampaignImpression.newBuilder().setCampaignId(CAMPAIGN_ID).build())
          .build();
  private FetchEligibleCampaignsResponse testFetchEligibleCampaignsResponse =
      FetchEligibleCampaignsResponse.getDefaultInstance();
  private static final String PACKAGE_NAME = "package_name";
  private static final String VERSION_NAME = "version_name";
  private static final String APPLICATION_ID = "application_id";
  // This can never be static because of the some validations in firebase options
  private final FirebaseOptions firebaseOptions =
      new FirebaseOptions.Builder()
          .setGcmSenderId(TEST_PROJECT_NUMBER)
          .setApiKey("api_key")
          .setApplicationId(APPLICATION_ID)
          .build();
  private final PackageInfo packageInfo = new PackageInfo();
  private ApiClient apiClient;

  @Captor
  private ArgumentCaptor<FetchEligibleCampaignsRequest> fetchEligibleCampaignsRequestArgcaptor;

  @Mock private GrpcClient mockGrpcClient;
  @Mock private FirebaseApp firebaseApp;
  @Mock private Application application;
  @Mock private PackageManager packageManager;
  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private DataCollectionHelper dataCollectionHelper;
  private ProviderInstaller providerInstaller;
  private FakeClock clock;
  private final long NOW = new SystemClock().now();

  @Before
  public void setup() throws NameNotFoundException {
    MockitoAnnotations.initMocks(this);

    providerInstaller = spy(new ProviderInstaller(application));
    packageInfo.versionName = VERSION_NAME;
    when(firebaseApp.getOptions()).thenReturn(firebaseOptions);
    when(application.getPackageManager()).thenReturn(packageManager);
    doNothing().when(providerInstaller).install();
    clock = new FakeClock(new SystemClock().now());
    testFetchEligibleCampaignsResponse =
        testFetchEligibleCampaignsResponse.toBuilder()
            .setExpirationEpochTimestampMillis(clock.now() + TimeUnit.MINUTES.toMillis(5))
            .build();

    apiClient =
        new ApiClient(() -> mockGrpcClient, firebaseApp, application, clock, providerInstaller);
    when(application.getPackageName()).thenReturn(PACKAGE_NAME);
    when(packageManager.getPackageInfo(PACKAGE_NAME, 0)).thenReturn(packageInfo);
    TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE));
  }

  @Test
  public void getFiams_proxiesRequestToGrpcClient() {
    when(mockGrpcClient.fetchEligibleCampaigns(any(FetchEligibleCampaignsRequest.class)))
        .thenReturn(testFetchEligibleCampaignsResponse);

    assertThat(apiClient.getFiams(FID_RESULT, campaignImpressionList))
        .isEqualTo(testFetchEligibleCampaignsResponse);
  }

  @Test
  public void getFiams_constructsCampaignsRequestWithProjectNumberFromGcmSenderId() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    assertThat(fetchEligibleCampaignsRequestArgcaptor.getValue().getProjectNumber())
        .isEqualTo(TEST_PROJECT_NUMBER);
  }

  @Test
  public void getFiams_constructsCampaignsRequestWithImpressedCampaigns() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    assertThat(fetchEligibleCampaignsRequestArgcaptor.getValue().getAlreadySeenCampaignsList())
        .containsExactlyElementsIn(campaignImpressionList.getAlreadySeenCampaignsList());
  }

  @Test
  public void getFiams_signalsContainsAppVersion() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientSignals clientSignals =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getClientSignals();

    assertThat(clientSignals.getAppVersion()).isEqualTo(VERSION_NAME);
  }

  @Test
  public void getFiams_whenPackageNotFound_setsSignalsAppVersionToNull()
      throws NameNotFoundException {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);
    when(packageManager.getPackageInfo(PACKAGE_NAME, 0)).thenThrow(new NameNotFoundException());

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientSignals clientSignals =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getClientSignals();

    assertThat(clientSignals.getAppVersion()).isEmpty();
  }

  @Test
  public void getFiams_signalsContainsPlatformVersion() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientSignals clientSignals =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getClientSignals();

    // sdk version set in roboelectric annotation above
    assertThat(clientSignals.getPlatformVersion()).isEqualTo("22");
  }

  @Test
  public void getFiams_signalsContainsLanguageCode() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientSignals clientSignals =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getClientSignals();

    // set via roboelectric annotation anove
    assertThat(clientSignals.getLanguageCode()).isEqualTo("es");
  }

  @Test
  public void getFiams_signalsContainsTimeZone() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientSignals clientSignals =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getClientSignals();

    assertThat(clientSignals.getTimeZone()).isEqualTo(TIME_ZONE);
  }

  @Test
  public void getFiams_installsProvider() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    verify(providerInstaller, times(0)).install();

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    verify(providerInstaller, times(1)).install();
  }

  @Test
  public void getFiams_clientAppInfoContainsInstanceId() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientAppInfo clientAppInfo =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getRequestingClientApp();

    assertThat(clientAppInfo.getAppInstanceId()).isEqualTo(INSTALLATION_ID);
  }

  @Test
  public void getFiams_clientAppInfoContainsInstanceIdToken() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientAppInfo clientAppInfo =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getRequestingClientApp();

    assertThat(clientAppInfo.getAppInstanceIdToken()).isEqualTo(INSTALLATION_TOKEN);
  }

  @Test
  public void getFiams_clientAppInfoContainsGmpAppId() {
    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(testFetchEligibleCampaignsResponse);

    apiClient.getFiams(FID_RESULT, campaignImpressionList);

    ClientAppInfo clientAppInfo =
        fetchEligibleCampaignsRequestArgcaptor.getValue().getRequestingClientApp();

    assertThat(clientAppInfo.getGmpAppId()).isEqualTo(APPLICATION_ID);
  }

  @Test
  public void getFiams_protectsFromBadPastCacheTimestamp() {
    // The expiration timestamp is set to duration of 1 day NOT the timestamp of now+1day
    FetchEligibleCampaignsResponse badCacheTimestamp =
        testFetchEligibleCampaignsResponse.toBuilder()
            .setExpirationEpochTimestampMillis(TimeUnit.DAYS.toMillis(1))
            .build();

    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(badCacheTimestamp);

    FetchEligibleCampaignsResponse fetchFiamsSafe =
        apiClient.getFiams(FID_RESULT, campaignImpressionList);

    // Now should be:
    assertThat(fetchFiamsSafe.getExpirationEpochTimestampMillis()).isGreaterThan(clock.now());
    assertThat(fetchFiamsSafe.getExpirationEpochTimestampMillis())
        .isEqualTo(clock.now() + TimeUnit.DAYS.toMillis(1));
  }

  @Test
  public void getFiams_protectsFromFutureBadCacheTimestamp() {
    // The expiration timestamp is set to duration of 1 day NOT the timestamp of now+1day
    FetchEligibleCampaignsResponse badCacheTimestamp =
        testFetchEligibleCampaignsResponse.toBuilder()
            .setExpirationEpochTimestampMillis(
                clock.now() + TimeUnit.DAYS.toMillis(3) + TimeUnit.SECONDS.toMillis(1))
            .build();

    when(mockGrpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequestArgcaptor.capture()))
        .thenReturn(badCacheTimestamp);

    FetchEligibleCampaignsResponse fetchFiamsSafe =
        apiClient.getFiams(FID_RESULT, campaignImpressionList);

    // Now should be:
    assertThat(fetchFiamsSafe.getExpirationEpochTimestampMillis()).isGreaterThan(clock.now());
    assertThat(fetchFiamsSafe.getExpirationEpochTimestampMillis())
        .isEqualTo(clock.now() + TimeUnit.DAYS.toMillis(1));
  }
}
