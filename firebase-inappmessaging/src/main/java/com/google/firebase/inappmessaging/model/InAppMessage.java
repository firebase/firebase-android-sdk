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
import java.util.Map;

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
  @Nullable private Map<String, String> data;

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
      MessageType messageType,
      Map<String, String> data) {
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
    this.data = data;
  }

  /** @hide */
  public InAppMessage(
      CampaignMetadata campaignMetadata, MessageType messageType, Map<String, String> data) {
    this.campaignMetadata = campaignMetadata;
    this.messageType = messageType;
    this.data = data;
  }

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Nullable
  @Deprecated
  public Text getTitle() {
    return title;
  }

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Nullable
  @Deprecated
  public Text getBody() {
    return body;
  }

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Nullable
  @Deprecated
  public String getImageUrl() {
    return imageUrl;
  }

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Nullable
  @Deprecated
  public ImageData getImageData() {
    return imageData;
  }

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Nullable
  @Deprecated
  public Button getActionButton() {
    if (getAction() != null) {
      return getAction().getButton();
    }
    return actionButton;
  }

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Deprecated
  @Nullable
  public abstract Action getAction();

  /**
   * @deprecated Use the message specific methods (see {@link CardMessage}, {@link ModalMessage},
   *     {@link BannerMessage}, {@link ImageOnlyMessage}) instead.
   */
  @Nullable
  @Deprecated
  public String getBackgroundHexColor() {
    return backgroundHexColor;
  }

  /** @deprecated Use {@link #getCampaignMetadata()#getCampaignId()} instead. */
  @Nullable
  @Deprecated
  public String getCampaignId() {
    return campaignMetadata.getCampaignId();
  }

  /** @deprecated Use {@link #getCampaignMetadata()#getCampaignName()} instead. */
  @Nullable
  @Deprecated
  public String getCampaignName() {
    return campaignMetadata.getCampaignName();
  }

  /** @deprecated Use {@link #getCampaignMetadata()#getIsTestMessage()} instead. */
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

  /**
   * Gets the extra data map of the message. This is defined in the Firebase Console for each
   * campaign.
   */
  @Nullable
  public Map<String, String> getData() {
    return data;
  }
}
