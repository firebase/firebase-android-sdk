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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.inappmessaging.testutil.Assert.expectThrows;
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_MODEL_WITHOUT_URL;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_METADATA_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_NAME_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CARD_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_DATA;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.IS_NOT_TEST_MESSAGE;
import static com.google.firebase.inappmessaging.testutil.TestData.MESSAGE_BACKGROUND_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.MODAL_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.TITLE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestProtos.ACTION_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.BANNER_MESSAGE_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.BODY_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.BUTTON_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.CARD_MESSAGE_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.IMAGE_MESSAGE_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.MODAL_MESSAGE_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.SECONDARY_ACTION_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.TITLE_PROTO;

import com.google.firebase.inappmessaging.MessagesProto;
import com.google.firebase.inappmessaging.MessagesProto.Content;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ProtoMarshallerClientTest {

  @Before
  public void setup() {}

  // ************************* BANNER *************************
  @Test
  public void banner_completeMessage_createsInAppMessage() {
    InAppMessage decodedModel = decode(BANNER_MESSAGE_PROTO);
    assertThat(decodedModel).isEqualTo(BANNER_MESSAGE_MODEL);
  }

  @Test
  public void banner_emptyeMessage_throwsException() {
    MessagesProto.Content incompleteBannerProto =
        MessagesProto.Content.newBuilder()
            .setBanner(MessagesProto.BannerMessage.getDefaultInstance())
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteBannerProto));
    assertThat(e).hasMessageThat().contains("Banner model must have a title");
  }

  @Test
  public void banner_missingTitle_throwsException() {
    MessagesProto.Content incompleteBannerProto =
        MessagesProto.Content.newBuilder()
            .setBanner(
                MessagesProto.BannerMessage.newBuilder()
                    .setAction(ACTION_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO)
                    .setImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteBannerProto));
    assertThat(e).hasMessageThat().contains("Banner model must have a title");
  }

  @Test
  public void banner_withMinimumAttributes_createsInAppMessage() {
    MessagesProto.Content minimumBanner =
        MessagesProto.Content.newBuilder()
            .setBanner(
                MessagesProto.BannerMessage.newBuilder()
                    .setTitle(TITLE_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING))
            .build();

    BannerMessage expected =
        BannerMessage.builder()
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(TITLE_MODEL)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    InAppMessage actual = decode(minimumBanner);
    assertThat(actual).isEqualTo(expected);
  }

  // ************************* MODAL *************************
  @Test
  public void modal_completeMessage_createsInAppMessage() {
    InAppMessage decodedModel = decode(MODAL_MESSAGE_PROTO);
    assertThat(decodedModel).isEqualTo(MODAL_MESSAGE_MODEL);
  }

  @Test
  public void modal_emptyMessage_throwsException() {
    MessagesProto.Content incompleteModalProto =
        MessagesProto.Content.newBuilder()
            .setModal(MessagesProto.ModalMessage.getDefaultInstance())
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteModalProto));
    assertThat(e).hasMessageThat().contains("Modal model must have a title");
  }

  @Test
  public void modal_missingTitle_throwsException() {
    MessagesProto.Content incompleteModalProto =
        MessagesProto.Content.newBuilder()
            .setModal(
                MessagesProto.ModalMessage.newBuilder()
                    .setAction(ACTION_PROTO)
                    .setActionButton(BUTTON_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO)
                    .setImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteModalProto));
    assertThat(e).hasMessageThat().contains("Modal model must have a title");
  }

  @Test
  public void modal_withActionWithoutButton_throwsException() {
    MessagesProto.Content incompleteModalProto =
        MessagesProto.Content.newBuilder()
            .setModal(
                MessagesProto.ModalMessage.newBuilder()
                    .setAction(ACTION_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteModalProto));
    assertThat(e).hasMessageThat().contains("Modal model action must be null or have a button");
  }

  @Test
  public void modal_withMinimumAttributes_createsInAppMessage() {
    MessagesProto.Content minimumModal =
        MessagesProto.Content.newBuilder()
            .setModal(
                MessagesProto.ModalMessage.newBuilder()
                    .setTitle(TITLE_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING))
            .build();

    ModalMessage expected =
        ModalMessage.builder()
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setTitle(TITLE_MODEL)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    InAppMessage actual = decode(minimumModal);
    assertThat(actual).isEqualTo(expected);
  }

  // ************************* IMAGE *************************
  @Test
  public void imageOnly_completeMessage_createsInAppMessage() {
    InAppMessage decodedModel = decode(IMAGE_MESSAGE_PROTO);
    assertThat(decodedModel).isEqualTo(IMAGE_MESSAGE_MODEL);
  }

  @Test
  public void imageOnly_emptyMessage_throwsException() {
    MessagesProto.Content incompleteImageProto =
        MessagesProto.Content.newBuilder()
            .setImageOnly(MessagesProto.ImageOnlyMessage.getDefaultInstance())
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteImageProto));
    assertThat(e).hasMessageThat().contains("ImageOnly model must have image data");
  }

  @Test
  public void imageOnly_missingUrl_throwsException() {
    MessagesProto.Content incompleteImageProto =
        MessagesProto.Content.newBuilder()
            .setImageOnly(
                MessagesProto.ImageOnlyMessage.newBuilder().setAction(ACTION_PROTO).setImageUrl(""))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteImageProto));
    assertThat(e).hasMessageThat().contains("ImageOnly model must have image data");
  }

  @Test
  public void imageOnly_withMinimumAttributes_createsInAppMessage() {
    MessagesProto.Content minimumImageOnly =
        MessagesProto.Content.newBuilder()
            .setImageOnly(MessagesProto.ImageOnlyMessage.newBuilder().setImageUrl(IMAGE_URL_STRING))
            .build();

    ImageOnlyMessage expected =
        ImageOnlyMessage.builder().setImageData(IMAGE_DATA).build(CAMPAIGN_METADATA_MODEL, DATA);
    InAppMessage actual = decode(minimumImageOnly);
    assertThat(actual).isEqualTo(expected);
  }

  // ************************* CARD *************************
  @Test
  public void card_completeMessage_createsInAppMessage() {
    InAppMessage decodedModel = decode(CARD_MESSAGE_PROTO);
    assertThat(decodedModel).isEqualTo(CARD_MESSAGE_MODEL);
  }

  @Test
  public void card_emptyMessage_throwsException() {
    MessagesProto.Content incompleteCardProto =
        MessagesProto.Content.newBuilder()
            .setCard(MessagesProto.CardMessage.getDefaultInstance())
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteCardProto));
    assertThat(e).hasMessageThat().contains("Card model must have a primary action");
  }

  @Test
  public void card_missingPrimaryActionButton_throwsException() {
    MessagesProto.Content incompleteCardProto =
        MessagesProto.Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryAction(ACTION_PROTO)
                    .setSecondaryAction(SECONDARY_ACTION_PROTO)
                    .setSecondaryActionButton(BUTTON_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteCardProto));
    assertThat(e).hasMessageThat().contains("Card model must have a primary action button");
  }

  @Test
  public void card_missingSecondaryActionButton_throwsException() {
    MessagesProto.Content incompleteCardProto =
        MessagesProto.Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryAction(ACTION_PROTO)
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setSecondaryAction(SECONDARY_ACTION_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteCardProto));
    assertThat(e)
        .hasMessageThat()
        .contains("Card model secondary action must be null or have a button");
  }

  @Test
  public void card_missingTitle_throwsException() {
    MessagesProto.Content incompleteCardProto =
        MessagesProto.Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryAction(ACTION_PROTO)
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteCardProto));
    assertThat(e).hasMessageThat().contains("Card model must have a title");
  }

  @Test
  public void card_missingImageData_throwsException() {
    MessagesProto.Content incompleteCardProto =
        MessagesProto.Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryAction(ACTION_PROTO)
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setBody(BODY_PROTO))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteCardProto));
    assertThat(e).hasMessageThat().contains("Card model must have at least one image");
  }

  @Test
  public void card_missingBackgroundColor_throwsException() {
    MessagesProto.Content incompleteCardProto =
        MessagesProto.Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryAction(ACTION_PROTO)
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setBody(BODY_PROTO)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> decode(incompleteCardProto));
    assertThat(e).hasMessageThat().contains("Card model must have a background color");
  }

  @Test
  public void card_withMinimumAttributes_createsInAppMessage() {
    MessagesProto.Content minimumCard =
        Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    CardMessage expected =
        CardMessage.builder()
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setPrimaryAction(ACTION_MODEL_WITHOUT_URL)
            .setPortraitImageData(IMAGE_DATA)
            .setTitle(TITLE_MODEL)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    InAppMessage actual = decode(minimumCard);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void cardPropagatesDataBundle() {
    MessagesProto.Content minimumCard =
        Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    CardMessage expected =
        CardMessage.builder()
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setPrimaryAction(ACTION_MODEL_WITHOUT_URL)
            .setPortraitImageData(IMAGE_DATA)
            .setTitle(TITLE_MODEL)
            .build(CAMPAIGN_METADATA_MODEL, DATA);
    InAppMessage actual = decode(minimumCard, DATA);
    assertThat(actual.getData()).isEqualTo(DATA);
    assertThat(expected.getData()).isEqualTo(DATA);
  }

  @Test
  public void cardPropagatesNullDataBundle() {
    MessagesProto.Content minimumCard =
        Content.newBuilder()
            .setCard(
                MessagesProto.CardMessage.newBuilder()
                    .setPrimaryActionButton(BUTTON_PROTO)
                    .setTitle(TITLE_PROTO)
                    .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                    .setPortraitImageUrl(IMAGE_URL_STRING))
            .build();

    CardMessage expected =
        CardMessage.builder()
            .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
            .setPrimaryAction(ACTION_MODEL_WITHOUT_URL)
            .setPortraitImageData(IMAGE_DATA)
            .setTitle(TITLE_MODEL)
            .build(CAMPAIGN_METADATA_MODEL, null);
    InAppMessage actual = decode(minimumCard, null);
    Assert.assertNull(actual.getData());
    Assert.assertNull(expected.getData());
  }

  // ************************* DECODING *************************
  @Test
  public void decode_nullContent_failsPreconditionCheck() {
    NullPointerException e = expectThrows(NullPointerException.class, () -> decode(null));
    assertThat(e).hasMessageThat().contains("FirebaseInAppMessaging content cannot be null.");
  }

  @Test
  public void decode_withNoMessageDetails_createsInAppMessageWithUnsupportedType() {
    MessagesProto.Content unsupportedProto =
        MessagesProto.Content.newBuilder()
            .setModal(MessagesProto.ModalMessage.getDefaultInstance())
            .clearMessageDetails()
            .build();

    InAppMessage actual = decode(unsupportedProto);
    assertThat(actual.messageType).isEqualTo(MessageType.UNSUPPORTED);
  }

  private static InAppMessage decode(MessagesProto.Content message) {
    return ProtoMarshallerClient.decode(
        message, CAMPAIGN_ID_STRING, CAMPAIGN_NAME_STRING, IS_NOT_TEST_MESSAGE, DATA);
  }

  private static InAppMessage decode(MessagesProto.Content message, Map<String, String> data) {
    return ProtoMarshallerClient.decode(
        message, CAMPAIGN_ID_STRING, CAMPAIGN_NAME_STRING, IS_NOT_TEST_MESSAGE, data);
  }
}
