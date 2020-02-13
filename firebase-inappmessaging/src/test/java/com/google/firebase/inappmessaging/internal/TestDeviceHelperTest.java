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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.inappmessaging.MessagesProto;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TestDeviceHelperTest {

  private static final CampaignProto.ThickContent.Builder thickContentBuilder =
      CampaignProto.ThickContent.newBuilder()
          .setContent(MessagesProto.Content.getDefaultInstance());
  private static final CampaignProto.ThickContent thickContent = thickContentBuilder.build();
  private static final FetchEligibleCampaignsResponse.Builder campaignsResponseBuilder =
      FetchEligibleCampaignsResponse.newBuilder();
  private static final FetchEligibleCampaignsResponse campaignsResponse =
      campaignsResponseBuilder.build();

  @Mock private SharedPreferencesUtils sharedPreferencesUtils;
  private TestDeviceHelper testDeviceHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    testDeviceHelper = new TestDeviceHelper(sharedPreferencesUtils);
  }

  @Test
  public void instantiation_attemptsToGetCorrectPreferencesWithDefaults() throws Exception {
    verify(sharedPreferencesUtils)
        .getAndSetBooleanPreference(TestDeviceHelper.TEST_DEVICE_PREFERENCES, false);
    verify(sharedPreferencesUtils)
        .getAndSetBooleanPreference(TestDeviceHelper.FRESH_INSTALL_PREFERENCES, true);
  }

  @Test
  public void processCampaignFetch_properlyHandlesFreshnessState() throws Exception {
    when(sharedPreferencesUtils.getAndSetBooleanPreference(
            TestDeviceHelper.FRESH_INSTALL_PREFERENCES, true))
        .thenReturn(true);
    testDeviceHelper = new TestDeviceHelper(sharedPreferencesUtils);

    CampaignProto.ThickContent randomContent = ThickContent.newBuilder(thickContent).build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(campaignsResponse)
            .addMessages(randomContent)
            .build();

    assertThat(testDeviceHelper.isAppInstallFresh()).isTrue();
    for (int i = 0; i < TestDeviceHelper.MAX_FETCH_COUNT - 1; i++) {
      testDeviceHelper.processCampaignFetch(response);
    }
    assertThat(testDeviceHelper.isAppInstallFresh()).isTrue();
    testDeviceHelper.processCampaignFetch(response);
    assertThat(testDeviceHelper.isAppInstallFresh()).isFalse();
    verify(sharedPreferencesUtils)
        .setBooleanPreference(TestDeviceHelper.FRESH_INSTALL_PREFERENCES, false);
  }

  @Test
  public void processCampaignFetch_properlyHandlesTestDeviceState() throws Exception {
    when(sharedPreferencesUtils.getAndSetBooleanPreference(
            TestDeviceHelper.TEST_DEVICE_PREFERENCES, false))
        .thenReturn(false);
    testDeviceHelper = new TestDeviceHelper(sharedPreferencesUtils);

    CampaignProto.ThickContent testContent =
        ThickContent.newBuilder(thickContent).setIsTestCampaign(true).build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(campaignsResponse)
            .addMessages(testContent)
            .build();

    assertThat(testDeviceHelper.isDeviceInTestMode()).isFalse();
    testDeviceHelper.processCampaignFetch(response);
    assertThat(testDeviceHelper.isDeviceInTestMode()).isTrue();
    verify(sharedPreferencesUtils)
        .setBooleanPreference(TestDeviceHelper.TEST_DEVICE_PREFERENCES, true);
  }
}
