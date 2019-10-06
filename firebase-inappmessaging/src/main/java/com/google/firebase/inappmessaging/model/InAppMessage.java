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

import androidx.annotation.Nullable;

/** Encapsulates a Firebase In App Message. */
public abstract class InAppMessage {

  @Deprecated Text title;
  @Deprecated Text body;
  @Deprecated String imageUrl;
  @Deprecated ImageData imageData;
  @Deprecated Button actionButton;
  @Deprecated String backgroundHexColor;
  @Deprecated String campaignId;
  @Deprecated String campaignName;
  @Deprecated Boolean isTestMessage;
  MessageType messageType;
  CampaignMetadata campaignMetadata;

  /** @hide */
  @Deprecated
  public InAppMessage(
      Text title,
      Text body,
      String imageUrl,
      ImageData imageData,
      Button actionButton,
      Action action,
      String backgroundHexColor,
      String campaignId,
      String campaignName,
      Boolean isTestMessage,
      MessageType messageType) {
    this.title = title;
    this.body = body;
    this.imageUrl = imageUrl;
    this.imageData = imageData;
    this.actionButton = actionButton;
    this.backgroundHexColor = backgroundHexColor;
    this.campaignId = campaignId;
    this.campaignName = campaignName;
    this.isTestMessage = isTestMessage;
    this.messageType = messageType;
    this.campaignMetadata = new CampaignMetadata(campaignId, campaignName, isTestMessage);
  }

  /** @hide */
  public InAppMessage(CampaignMetadata campaignMetadata, MessageType messageType) {
    this.campaignMetadata = campaignMetadata;
    this.messageType = messageType;
  }

  /** Deprecated - Use the message specific methods instead. */
  @Nullable
  @Deprecated
  public Text getTitle() {
    return title;
  }

  /** Deprecated - Use the message specific methods (see {@link CardMessage}) instead. */
  @Nullable
  @Deprecated
  public Text getBody() {
    return body;
  }

  /** Deprecated - Use the message specific methods (see {@link CardMessage}) instead. */
  @Nullable
  @Deprecated
  public String getImageUrl() {
    return imageUrl;
  }

  /** Deprecated - Use the message specific methods (see {@link CardMessage}) instead. */
  @Nullable
  @Deprecated
  public ImageData getImageData() {
    return imageData;
  }

  /** Deprecated - Use the message specific methods (see {@link CardMessage}) */
  @Nullable
  @Deprecated
  public Button getActionButton() {
    if (getAction() != null) {
      return getAction().getButton();
    }
    return actionButton;
  }

  /** Deprecated - Use the message specific methods (see {@link CardMessage}) instead. */
  @Deprecated
  @Nullable
  public abstract Action getAction();

  /** Deprecated - Use the message specific methods (see {@link CardMessage}) instead. */
  @Nullable
  @Deprecated
  public String getBackgroundHexColor() {
    return backgroundHexColor;
  }

  /** Deprecated - Use getCampaignMetadata().getCampaignId() instead. */
  @Nullable
  @Deprecated
  public String getCampaignId() {
    return campaignMetadata.getCampaignId();
  }

  /** Deprecated - Use getCampaignMetadata().getCampaignName() instead. */
  @Nullable
  @Deprecated
  public String getCampaignName() {
    return campaignMetadata.getCampaignName();
  }

  /** Deprecated - Use getCampaignMetadata().getIsTestMessage() instead. */
  @Nullable
  @Deprecated
  public Boolean getIsTestMessage() {
    return campaignMetadata.getIsTestMessage();
  }

  /** Gets the {@link MessageType} of the message */
  @Nullable
  public MessageType getMessageType() {
    return messageType;
  }
  /** Gets the {@link CampaignMetadata} of the message */
  @Nullable
  public CampaignMetadata getCampaignMetadata() {
    return campaignMetadata;
  }
}
