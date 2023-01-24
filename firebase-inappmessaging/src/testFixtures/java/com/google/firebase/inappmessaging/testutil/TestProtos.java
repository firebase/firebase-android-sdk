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

import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BODY_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BODY_TEXT_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BUTTON_BG_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BUTTON_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BUTTON_TEXT_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.LANDSCAPE_IMAGE_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.MESSAGE_BACKGROUND_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.SECONDARY_ACTION_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.TITLE_HEX_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.TITLE_TEXT_STRING;

import com.google.firebase.inappmessaging.MessagesProto;

public class TestProtos {
  public static final MessagesProto.Text TITLE_PROTO =
      MessagesProto.Text.newBuilder()
          .setText(TITLE_TEXT_STRING)
          .setHexColor(TITLE_HEX_STRING)
          .build();

  public static final MessagesProto.Text BODY_PROTO =
      MessagesProto.Text.newBuilder()
          .setText(BODY_TEXT_STRING)
          .setHexColor(BODY_HEX_STRING)
          .build();

  public static final MessagesProto.Text BUTTON_TEXT_PROTO =
      MessagesProto.Text.newBuilder()
          .setText(BUTTON_TEXT_STRING)
          .setHexColor(BUTTON_HEX_STRING)
          .build();

  public static final MessagesProto.Button BUTTON_PROTO =
      MessagesProto.Button.newBuilder()
          .setText(BUTTON_TEXT_PROTO)
          .setButtonHexColor(BUTTON_BG_STRING)
          .build();

  public static final MessagesProto.Action ACTION_PROTO =
      MessagesProto.Action.newBuilder().setActionUrl(ACTION_URL_STRING).build();
  public static final MessagesProto.Action SECONDARY_ACTION_PROTO =
      MessagesProto.Action.newBuilder().setActionUrl(SECONDARY_ACTION_URL_STRING).build();

  public static final MessagesProto.Content BANNER_MESSAGE_PROTO =
      MessagesProto.Content.newBuilder()
          .setBanner(
              MessagesProto.BannerMessage.newBuilder()
                  .setAction(ACTION_PROTO)
                  .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                  .setBody(BODY_PROTO)
                  .setTitle(TITLE_PROTO)
                  .setImageUrl(IMAGE_URL_STRING))
          .build();

  public static final MessagesProto.Content MODAL_MESSAGE_PROTO =
      MessagesProto.Content.newBuilder()
          .setModal(
              MessagesProto.ModalMessage.newBuilder()
                  .setAction(ACTION_PROTO)
                  .setActionButton(BUTTON_PROTO)
                  .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                  .setBody(BODY_PROTO)
                  .setTitle(TITLE_PROTO)
                  .setImageUrl(IMAGE_URL_STRING))
          .build();

  public static final MessagesProto.Content CARD_MESSAGE_PROTO =
      MessagesProto.Content.newBuilder()
          .setCard(
              MessagesProto.CardMessage.newBuilder()
                  .setPrimaryActionButton(BUTTON_PROTO)
                  .setPrimaryAction(ACTION_PROTO)
                  .setSecondaryActionButton(BUTTON_PROTO)
                  .setBackgroundHexColor(MESSAGE_BACKGROUND_HEX_STRING)
                  .setBody(BODY_PROTO)
                  .setTitle(TITLE_PROTO)
                  .setPortraitImageUrl(IMAGE_URL_STRING)
                  .setLandscapeImageUrl(LANDSCAPE_IMAGE_URL_STRING))
          .build();

  public static final MessagesProto.Content IMAGE_MESSAGE_PROTO =
      MessagesProto.Content.newBuilder()
          .setImageOnly(
              MessagesProto.ImageOnlyMessage.newBuilder()
                  .setAction(ACTION_PROTO)
                  .setImageUrl(IMAGE_URL_STRING))
          .build();
}
