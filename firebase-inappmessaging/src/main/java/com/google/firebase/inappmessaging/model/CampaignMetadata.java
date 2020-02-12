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

package com.google.firebase.inappmessaging.model;

import androidx.annotation.NonNull;

/** Provides the following about any message, */
public class CampaignMetadata {
  private final String campaignId;
  private final String campaignName;
  private final boolean isTestMessage;

  /**
   * This is only used by the FIAM internal SDK
   *
   * @hide
   */
  public CampaignMetadata(String campaignId, String campaignName, boolean isTestMessage) {
    this.campaignId = campaignId;
    this.campaignName = campaignName;
    this.isTestMessage = isTestMessage;
  }

  @NonNull
  public String getCampaignId() {
    return campaignId;
  }

  @NonNull
  public String getCampaignName() {
    return campaignName;
  }

  public boolean getIsTestMessage() {
    return isTestMessage;
  }
}
