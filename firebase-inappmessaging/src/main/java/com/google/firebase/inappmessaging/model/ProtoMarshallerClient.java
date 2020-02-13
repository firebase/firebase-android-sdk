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

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.firebase.inappmessaging.MessagesProto;
import com.google.firebase.inappmessaging.internal.Logging;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Class to transform internal proto representation to externalized parcelable objects. See {@link
 * InAppMessage}.
 *
 * <p>Note that an object is inflated only if it is defined in the proto and is null otherwise.
 *
 * @hide
 */
@Singleton
public class ProtoMarshallerClient {
  @Inject
  ProtoMarshallerClient() {}

  @Nonnull
  private static ModalMessage.Builder from(MessagesProto.ModalMessage in) {
    ModalMessage.Builder builder = ModalMessage.builder();

    if (!TextUtils.isEmpty(in.getBackgroundHexColor())) {
      builder.setBackgroundHexColor(in.getBackgroundHexColor());
    }

    if (!TextUtils.isEmpty(in.getImageUrl())) {
      builder.setImageData(ImageData.builder().setImageUrl(in.getImageUrl()).build());
    }

    if (in.hasAction()) {
      builder.setAction(decode(in.getAction(), in.getActionButton()));
    }

    if (in.hasBody()) {
      builder.setBody(decode(in.getBody()));
    }

    if (in.hasTitle()) {
      builder.setTitle(decode(in.getTitle()));
    }

    return builder;
  }

  @Nonnull
  private static ImageOnlyMessage.Builder from(MessagesProto.ImageOnlyMessage in) {
    ImageOnlyMessage.Builder builder = ImageOnlyMessage.builder();

    if (!TextUtils.isEmpty(in.getImageUrl())) {
      builder.setImageData(ImageData.builder().setImageUrl(in.getImageUrl()).build());
    }

    if (in.hasAction()) {
      builder.setAction(decode(in.getAction()).build());
    }

    return builder;
  }

  @Nonnull
  private static BannerMessage.Builder from(MessagesProto.BannerMessage in) {
    BannerMessage.Builder builder = BannerMessage.builder();

    if (!TextUtils.isEmpty(in.getBackgroundHexColor())) {
      builder.setBackgroundHexColor(in.getBackgroundHexColor());
    }

    if (!TextUtils.isEmpty(in.getImageUrl())) {
      builder.setImageData(ImageData.builder().setImageUrl(in.getImageUrl()).build());
    }

    if (in.hasAction()) {
      builder.setAction(decode(in.getAction()).build());
    }

    if (in.hasBody()) {
      builder.setBody(decode(in.getBody()));
    }

    if (in.hasTitle()) {
      builder.setTitle(decode(in.getTitle()));
    }

    return builder;
  }

  @Nonnull
  private static CardMessage.Builder from(MessagesProto.CardMessage in) {
    CardMessage.Builder builder = CardMessage.builder();

    if (in.hasTitle()) {
      builder.setTitle(decode(in.getTitle()));
    }

    if (in.hasBody()) {
      builder.setBody(decode(in.getBody()));
    }

    if (!TextUtils.isEmpty(in.getBackgroundHexColor())) {
      builder.setBackgroundHexColor(in.getBackgroundHexColor());
    }

    if (in.hasPrimaryAction() || in.hasPrimaryActionButton()) {
      builder.setPrimaryAction(decode(in.getPrimaryAction(), in.getPrimaryActionButton()));
    }

    if (in.hasSecondaryAction() || in.hasSecondaryActionButton()) {
      builder.setSecondaryAction(decode(in.getSecondaryAction(), in.getSecondaryActionButton()));
    }

    if (!TextUtils.isEmpty(in.getPortraitImageUrl())) {
      builder.setPortraitImageData(
          ImageData.builder().setImageUrl(in.getPortraitImageUrl()).build());
    }

    if (!TextUtils.isEmpty(in.getLandscapeImageUrl())) {
      builder.setLandscapeImageData(
          ImageData.builder().setImageUrl(in.getLandscapeImageUrl()).build());
    }

    return builder;
  }

  private static Button decode(MessagesProto.Button in) {
    Button.Builder builder = Button.builder();

    if (!TextUtils.isEmpty(in.getButtonHexColor())) {
      builder.setButtonHexColor(in.getButtonHexColor());
    }

    if (in.hasText()) {
      builder.setText(decode(in.getText()));
    }
    return builder.build();
  }

  private static Action decode(MessagesProto.Action protoAction, MessagesProto.Button protoButton) {

    Action.Builder builder = decode(protoAction);
    if (!protoButton.equals(MessagesProto.Button.getDefaultInstance())) {
      Button.Builder buttonBuilder = Button.builder();
      if (!TextUtils.isEmpty(protoButton.getButtonHexColor())) {
        buttonBuilder.setButtonHexColor(protoButton.getButtonHexColor());
      }
      if (protoButton.hasText()) {
        Text.Builder buttonText = Text.builder();
        MessagesProto.Text text = protoButton.getText();
        if (!TextUtils.isEmpty(text.getText())) {
          buttonText.setText(text.getText());
        }
        if (!TextUtils.isEmpty(text.getHexColor())) {
          buttonText.setHexColor(text.getHexColor());
        }
        buttonBuilder.setText(buttonText.build());
      }
      builder.setButton(buttonBuilder.build());
    }
    return builder.build();
  }

  private static Action.Builder decode(MessagesProto.Action protoAction) {

    Action.Builder builder = Action.builder();
    if (!TextUtils.isEmpty(protoAction.getActionUrl())) {
      builder.setActionUrl(protoAction.getActionUrl());
    }

    return builder;
  }

  private static Text decode(MessagesProto.Text in) {
    Text.Builder builder = Text.builder();

    if (!TextUtils.isEmpty(in.getHexColor())) {
      builder.setHexColor(in.getHexColor());
    }

    if (!TextUtils.isEmpty(in.getText())) {
      builder.setText(in.getText());
    }

    return builder.build();
  }

  /** Tranform {@link MessagesProto.Content} proto to an {@link InAppMessage} value object */
  public static InAppMessage decode(
      @Nonnull MessagesProto.Content in,
      @NonNull String campaignId,
      @NonNull String campaignName,
      boolean isTestMessage,
      @Nullable Map<String, String> data) {
    Preconditions.checkNotNull(in, "FirebaseInAppMessaging content cannot be null.");
    Preconditions.checkNotNull(campaignId, "FirebaseInAppMessaging campaign id cannot be null.");
    Preconditions.checkNotNull(
        campaignName, "FirebaseInAppMessaging campaign name cannot be null.");
    Logging.logd("Decoding message: " + in.toString());
    CampaignMetadata campaignMetadata =
        new CampaignMetadata(campaignId, campaignName, isTestMessage);

    switch (in.getMessageDetailsCase()) {
      case BANNER:
        return from(in.getBanner()).build(campaignMetadata, data);
      case IMAGE_ONLY:
        return from(in.getImageOnly()).build(campaignMetadata, data);
      case MODAL:
        return from(in.getModal()).build(campaignMetadata, data);
      case CARD:
        return from(in.getCard()).build(campaignMetadata, data);

      default:
        // If the template is unsupported, then we return an unsupported message
        return new InAppMessage(
            new CampaignMetadata(campaignId, campaignName, isTestMessage),
            MessageType.UNSUPPORTED,
            data) {
          @Override
          public Action getAction() {
            return null;
          }
        };
    }
  }
}
