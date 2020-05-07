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

package com.google.firebase.inappmessaging.testutil;

import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.Button;
import com.google.firebase.inappmessaging.model.CampaignMetadata;
import com.google.firebase.inappmessaging.model.CardMessage;
import com.google.firebase.inappmessaging.model.ImageData;
import com.google.firebase.inappmessaging.model.ImageOnlyMessage;
import com.google.firebase.inappmessaging.model.ModalMessage;
import com.google.firebase.inappmessaging.model.Text;
import java.util.HashMap;
import java.util.Map;

public class TestData {
  // ************************* METADATA *************************
  public static final String ANALYTICS_EVENT_NAME = "event1";
  public static final String ON_FOREGROUND_EVENT_NAME = "ON_FOREGROUND";
  public static final String INSTALLATION_ID = "instance_id";
  public static final String INSTALLATION_TOKEN = "instance_token";
  public static final boolean IS_NOT_TEST_MESSAGE = false;
  public static final boolean IS_TEST_MESSAGE = true;
  public static final String MESSAGE_BACKGROUND_HEX_STRING = "#FFFFFF";
  public static final String CAMPAIGN_ID_STRING = "campaign_id";
  public static final String CAMPAIGN_NAME_STRING = "campaign_name";
  public static final CampaignMetadata CAMPAIGN_METADATA_MODEL =
      new CampaignMetadata(CAMPAIGN_ID_STRING, CAMPAIGN_NAME_STRING, IS_NOT_TEST_MESSAGE);
  public static final CampaignMetadata TEST_CAMPAIGN_METADATA_MODEL =
      new CampaignMetadata(CAMPAIGN_ID_STRING, CAMPAIGN_NAME_STRING, IS_TEST_MESSAGE);
  public static final Map<String, String> DATA = new HashMap<>();

  static {
    DATA.put("up", "dog");
  }

  // ************************* TITLE *************************
  public static final String TITLE_TEXT_STRING = "title";
  public static final String TITLE_HEX_STRING = "#CCCCCC";
  public static final Text TITLE_MODEL =
      Text.builder().setText(TITLE_TEXT_STRING).setHexColor(TITLE_HEX_STRING).build();

  // ************************* BODY *************************
  public static final String BODY_TEXT_STRING = "body";
  public static final String BODY_HEX_STRING = "#000000";
  public static final Text BODY_MODEL =
      Text.builder().setText(BODY_TEXT_STRING).setHexColor(BODY_HEX_STRING).build();

  // ************************* IMAGE *************************
  public static final String IMAGE_URL_STRING = "image_url";
  public static final String LANDSCAPE_IMAGE_URL_STRING = "landscape_image";
  public static final ImageData IMAGE_DATA =
      ImageData.builder().setImageUrl(IMAGE_URL_STRING).build();
  public static final ImageData LANDSCAPE_IMAGE_DATA =
      ImageData.builder().setImageUrl(LANDSCAPE_IMAGE_URL_STRING).build();

  // ************************* BUTTON *************************
  public static final String BUTTON_TEXT_STRING = "button";
  public static final String BUTTON_HEX_STRING = "#FFFCCC";
  public static final String BUTTON_BG_STRING = "button_bg";
  public static final Text BUTTON_TEXT_MODEL =
      Text.builder().setText(BUTTON_TEXT_STRING).setHexColor(BUTTON_HEX_STRING).build();
  public static final Button BUTTON_MODEL =
      Button.builder().setText(BUTTON_TEXT_MODEL).setButtonHexColor(BUTTON_BG_STRING).build();

  // ************************* ACTION *************************
  public static final String ACTION_URL_STRING = "action_url";
  public static final String SECONDARY_ACTION_URL_STRING = "secondary_action";
  public static final Action ACTION_MODEL_WITHOUT_BUTTON =
      Action.builder().setActionUrl(ACTION_URL_STRING).build();
  public static final Action ACTION_MODEL_WITHOUT_URL =
      Action.builder().setButton(BUTTON_MODEL).build();
  public static final Action SECONDARY_ACTION_MODEL_WITHOUT_BUTTON =
      Action.builder().setActionUrl(SECONDARY_ACTION_URL_STRING).build();
  public static final Action ACTION_MODEL_WITH_BUTTON =
      Action.builder().setActionUrl(ACTION_URL_STRING).setButton(BUTTON_MODEL).build();
  public static final Action SECONDARY_ACTION_MODEL_WITH_BUTTON =
      Action.builder().setActionUrl(SECONDARY_ACTION_URL_STRING).setButton(BUTTON_MODEL).build();

  // ************************* BANNER *************************
  public static final BannerMessage BANNER_MESSAGE_MODEL =
      BannerMessage.builder()
          .setAction(ACTION_MODEL_WITHOUT_BUTTON)
          .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
          .setBody(BODY_MODEL)
          .setTitle(TITLE_MODEL)
          .setImageData(IMAGE_DATA)
          .build(CAMPAIGN_METADATA_MODEL, DATA);

  public static final BannerMessage BANNER_MESSAGE_NO_ACTION_MODEL =
      BannerMessage.builder()
          .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
          .setBody(BODY_MODEL)
          .setTitle(TITLE_MODEL)
          .setImageData(IMAGE_DATA)
          .build(CAMPAIGN_METADATA_MODEL, DATA);

  public static final BannerMessage BANNER_TEST_MESSAGE_MODEL =
      BannerMessage.builder()
          .setAction(ACTION_MODEL_WITHOUT_BUTTON)
          .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
          .setBody(BODY_MODEL)
          .setTitle(TITLE_MODEL)
          .setImageData(IMAGE_DATA)
          .build(TEST_CAMPAIGN_METADATA_MODEL, DATA);

  // ************************* MODAL *************************
  public static final ModalMessage MODAL_MESSAGE_MODEL =
      ModalMessage.builder()
          .setAction(ACTION_MODEL_WITH_BUTTON)
          .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
          .setBody(BODY_MODEL)
          .setTitle(TITLE_MODEL)
          .setImageData(IMAGE_DATA)
          .build(CAMPAIGN_METADATA_MODEL, DATA);

  // ************************* CARD *************************
  public static final CardMessage CARD_MESSAGE_MODEL =
      CardMessage.builder()
          .setTitle(TITLE_MODEL)
          .setBody(BODY_MODEL)
          .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
          .setPrimaryAction(ACTION_MODEL_WITH_BUTTON)
          .setSecondaryAction(ACTION_MODEL_WITHOUT_URL)
          .setPortraitImageData(IMAGE_DATA)
          .setLandscapeImageData(LANDSCAPE_IMAGE_DATA)
          .build(CAMPAIGN_METADATA_MODEL, DATA);

  public static final CardMessage CARD_MESSAGE_WITHOUT_ACTIONS =
      CardMessage.builder()
          .setTitle(TITLE_MODEL)
          .setBody(BODY_MODEL)
          .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
          .setPrimaryAction(ACTION_MODEL_WITHOUT_URL)
          .setPortraitImageData(IMAGE_DATA)
          .setLandscapeImageData(LANDSCAPE_IMAGE_DATA)
          .build(CAMPAIGN_METADATA_MODEL, DATA);

  // ************************* IMAGE *************************
  public static final ImageOnlyMessage IMAGE_MESSAGE_MODEL =
      ImageOnlyMessage.builder()
          .setAction(ACTION_MODEL_WITHOUT_BUTTON)
          .setImageData(IMAGE_DATA)
          .build(CAMPAIGN_METADATA_MODEL, DATA);

  public static final ImageOnlyMessage IMAGE_MESSAGE_MODEL_WITHOUT_ACTION =
      ImageOnlyMessage.builder().setImageData(IMAGE_DATA).build(CAMPAIGN_METADATA_MODEL, DATA);

  // ************************* HELPERS *************************
  public static BannerMessage createBannerMessageCustomMetadata(CampaignMetadata metadata) {
    return BannerMessage.builder()
        .setAction(ACTION_MODEL_WITHOUT_BUTTON)
        .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
        .setBody(BODY_MODEL)
        .setTitle(TITLE_MODEL)
        .setImageData(IMAGE_DATA)
        .build(metadata, DATA);
  }
}
