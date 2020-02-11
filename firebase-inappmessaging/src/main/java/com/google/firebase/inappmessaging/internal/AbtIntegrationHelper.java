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

package com.google.firebase.inappmessaging.internal;

import com.google.firebase.abt.AbtException;
import com.google.firebase.abt.AbtExperimentInfo;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import developers.mobile.abt.FirebaseAbt;
import java.util.ArrayList;
import java.util.Date;
import javax.inject.Inject;

/** @hide */
public class AbtIntegrationHelper {
  private static FirebaseABTesting abTesting;

  @Inject
  public AbtIntegrationHelper(FirebaseABTesting abTesting) {
    this.abTesting = abTesting;
  }

  /**
   * Takes a {@link FetchEligibleCampaignsResponse} and updates ABT with the currently running
   * experiments based on the content of the response.
   *
   * @param response the {@link FetchEligibleCampaignsResponse} containing an up to date experiment
   *     list.
   */
  public void updateRunningExperiments(FetchEligibleCampaignsResponse response) {
    ArrayList<AbtExperimentInfo> runningExperiments = new ArrayList<>();
    for (CampaignProto.ThickContent content : response.getMessagesList()) {
      if (!content.getIsTestCampaign()
          && content
              .getPayloadCase()
              .equals(CampaignProto.ThickContent.PayloadCase.EXPERIMENTAL_PAYLOAD)) {
        FirebaseAbt.ExperimentPayload payload =
            content.getExperimentalPayload().getExperimentPayload();
        runningExperiments.add(
            new AbtExperimentInfo(
                payload.getExperimentId(),
                payload.getVariantId(),
                payload.getTriggerEvent(),
                new Date(payload.getExperimentStartTimeMillis()),
                payload.getTriggerTimeoutMillis(),
                payload.getTimeToLiveMillis()));
      }
    }
    if (runningExperiments.isEmpty()) {
      return;
    }
    try {
      Logging.logd(
          "Updating running experiments with: " + runningExperiments.size() + " experiments");
      abTesting.validateRunningExperiments(runningExperiments);
    } catch (AbtException e) {
      Logging.loge(
          "Unable to register experiments with ABT, missing analytics?\n" + e.getMessage());
    }
  }

  /**
   * Takes a {@link FirebaseAbt.ExperimentPayload} and tells ABT to set it as an active experiment.
   * This is meant to be called on an experimental FIAM that is getting displayed to a user, because
   * that would indicate that the experiment is now active for that user.
   *
   * @param payload the {@link FirebaseAbt.ExperimentPayload} that should be set as active.
   */
  public void setExperimentActive(FirebaseAbt.ExperimentPayload payload) {
    try {
      Logging.logd("Updating active experiment: " + payload.toString());
      abTesting.reportActiveExperiment(
          new AbtExperimentInfo(
              payload.getExperimentId(),
              payload.getVariantId(),
              payload.getTriggerEvent(),
              new Date(payload.getExperimentStartTimeMillis()),
              payload.getTriggerTimeoutMillis(),
              payload.getTimeToLiveMillis()));
    } catch (AbtException e) {
      Logging.loge(
          "Unable to set experiment as active with ABT, missing analytics?\n" + e.getMessage());
    }
  }
}
