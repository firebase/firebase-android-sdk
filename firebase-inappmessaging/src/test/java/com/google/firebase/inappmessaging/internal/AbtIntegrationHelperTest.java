// Copyright 2020 Google LLC
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.inappmessaging.MessagesProto;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AbtIntegrationHelperTest {
  private static final FetchEligibleCampaignsResponse noExperimentResponse =
      FetchEligibleCampaignsResponse.newBuilder()
          .addMessages(
              CampaignProto.ThickContent.newBuilder()
                  .setContent(MessagesProto.Content.getDefaultInstance())
                  .setVanillaPayload(CampaignProto.VanillaCampaignPayload.getDefaultInstance()))
          .build();

  private static final FetchEligibleCampaignsResponse yesExperimentResponse =
      FetchEligibleCampaignsResponse.newBuilder()
          .addMessages(
              CampaignProto.ThickContent.newBuilder()
                  .setContent(MessagesProto.Content.getDefaultInstance())
                  .setExperimentalPayload(
                      CampaignProto.ExperimentalCampaignPayload.getDefaultInstance()))
          .build();

  private static final FetchEligibleCampaignsResponse testExperimentResponse =
      FetchEligibleCampaignsResponse.newBuilder()
          .addMessages(
              CampaignProto.ThickContent.newBuilder()
                  .setContent(MessagesProto.Content.getDefaultInstance())
                  .setIsTestCampaign(true)
                  .setExperimentalPayload(
                      CampaignProto.ExperimentalCampaignPayload.getDefaultInstance()))
          .build();

  @Mock private FirebaseABTesting abTesting;
  private AbtIntegrationHelper abtIntegrationHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    abtIntegrationHelper = new AbtIntegrationHelper(abTesting);
    // make executor immediately execute
    abtIntegrationHelper.executor = Runnable::run;
  }

  @Test
  public void updateRunningExperiments_noExperiments_doesNotCallAbt() {
    abtIntegrationHelper.updateRunningExperiments(noExperimentResponse);
    verifyZeroInteractions(abTesting);
  }

  @Test
  public void updateRunningExperiments_yesExperiments_callsAbt() throws Exception {
    abtIntegrationHelper.updateRunningExperiments(yesExperimentResponse);
    verify(abTesting).validateRunningExperiments(Mockito.any());
  }

  @Test
  public void updateRunningExperiments_testExperiments_doesNotCallAbt() throws Exception {
    abtIntegrationHelper.updateRunningExperiments(testExperimentResponse);
    verifyZeroInteractions(abTesting);
  }
}
