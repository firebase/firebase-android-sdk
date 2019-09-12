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

package com.example.firebase.fiamui;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay;
import com.google.firebase.inappmessaging.model.*;
import me.priyesh.chroma.ChromaDialog;
import me.priyesh.chroma.ColorMode;
import me.priyesh.chroma.ColorSelectListener;

public class MainActivity extends AppCompatActivity {
  @BindView(R.id.start)
  Button mStart;

  @BindView(R.id.modal_fiam)
  RadioButton useModalFiam;

  @BindView(R.id.banner_fiam)
  RadioButton useBannerFiam;

  @BindView(R.id.image_fiam)
  RadioButton useImageFiam;

  @BindView(R.id.card_fiam)
  RadioButton useCardFiam;

  @BindView(R.id.long_body_text)
  RadioButton useLongBodyText;

  @BindView(R.id.normal_body_text)
  RadioButton useNormalBodyText;

  @BindView(R.id.no_body_text)
  RadioButton useNoBodyText;

  @BindView(R.id.message_title)
  TextInputEditText messageTitle;

  @BindView(R.id.image_width)
  TextInputEditText imageWidth;

  @BindView(R.id.image_height)
  TextInputEditText imageHeight;

  @BindView(R.id.landscape_image_width)
  TextInputEditText landscapeImageWidth;

  @BindView(R.id.landscape_image_height)
  TextInputEditText landscapeImageHeight;

  @BindView(R.id.action_button_text)
  TextInputEditText actionButtonText;

  @BindView(R.id.action_button_url)
  TextInputEditText actionButtonUrl;

  @BindView(R.id.secondary_action_button_text)
  TextInputEditText secondaryActionButtonText;

  @BindView(R.id.fiam_ttl)
  TextInputEditText fiamTTL;

  @BindView(R.id.color_body_bg_container)
  View colorBodyBgContainer;

  @BindView(R.id.color_body_bg_preview)
  View colorBodyBgPreview;

  @BindView(R.id.color_body_text_container)
  View colorBodyTextContainer;

  @BindView(R.id.color_body_text_preview)
  View colorBodyTextPreview;

  @BindView(R.id.color_button_bg_container)
  View colorButtonBgContainer;

  @BindView(R.id.color_button_bg_preview)
  View colorButtonBgPreview;

  @BindView(R.id.color_button_text_container)
  View colorButtonTextContainer;

  @BindView(R.id.color_button_text_preview)
  View colorButtonTextPreview;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    ButterKnife.bind(this);

    FirebaseAnalytics analytics = FirebaseAnalytics.getInstance(this);
    Bundle bundle = new Bundle();
    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "id");
    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "name");
    bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "image");
    analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

    bindColorPicker(colorBodyBgContainer, colorBodyBgPreview);
    bindColorPicker(colorBodyTextContainer, colorBodyTextPreview);
    bindColorPicker(colorButtonBgContainer, colorButtonBgPreview);
    bindColorPicker(colorButtonTextContainer, colorButtonTextPreview);
  }

  @OnClick(R.id.start)
  public void onStartClick(View v) {
    String buttonBackgroundColorString = getBackgroundColorString(colorButtonBgPreview);
    String buttonTextColorString = getBackgroundColorString(colorButtonTextPreview);
    String bodyTextColorString = getBackgroundColorString(colorBodyTextPreview);
    String bodyBackgroundColorString = getBackgroundColorString(colorBodyBgPreview);

    String bodyText = getString(getSelectedBodyText());

    String imageUrlString = makeImageUrl(imageWidth, imageHeight);
    ImageData imageData =
        imageUrlString != null ? ImageData.builder().setImageUrl(imageUrlString).build() : null;

    String landscapeUrlString = makeImageUrl(landscapeImageWidth, landscapeImageHeight);
    ImageData landscapeImageData =
        landscapeUrlString != null
            ? ImageData.builder().setImageUrl(landscapeUrlString).build()
            : null;

    String actionButtonTextString = actionButtonText.getText().toString();
    String actionButtonUrlString = actionButtonUrl.getText().toString();
    String secondaryActionButtonTextString = secondaryActionButtonText.getText().toString();

    CampaignMetadata campaignMetadata = new CampaignMetadata("test_campaign", "name", true);

    if (useImageFiam.isChecked()) {
      ImageOnlyMessage.Builder builder = ImageOnlyMessage.builder();
      Action action = Action.builder().setActionUrl(actionButtonUrlString).build();

      ImageOnlyMessage message =
          builder.setImageData(imageData).setAction(action).build(campaignMetadata);

      FirebaseInAppMessagingDisplay.getInstance()
          .testMessage(this, message, new NoOpDisplayCallbacks());
    } else if (useBannerFiam.isChecked()) {
      BannerMessage.Builder builder = BannerMessage.builder();
      Text body = Text.builder().setText(bodyText).setHexColor(bodyTextColorString).build();
      Action action = Action.builder().setActionUrl(actionButtonUrlString).build();
      Text title =
          Text.builder()
              .setText(messageTitle.getText().toString())
              .setHexColor(bodyTextColorString)
              .build();

      BannerMessage message =
          builder
              .setBackgroundHexColor(bodyBackgroundColorString)
              .setTitle(title)
              .setBody(body)
              .setImageData(imageData)
              .setAction(action)
              .build(campaignMetadata);

      FirebaseInAppMessagingDisplay.getInstance()
          .testMessage(this, message, new NoOpDisplayCallbacks());
    } else if (useCardFiam.isChecked()) {
      // Setup builder
      CardMessage.Builder builder = CardMessage.builder();

      // Main text
      Text title =
          Text.builder()
              .setText(messageTitle.getText().toString())
              .setHexColor(bodyTextColorString)
              .build();
      Text body = Text.builder().setText(bodyText).setHexColor(bodyTextColorString).build();

      // Primary button text
      Action primaryAction;
      if (TextUtils.isEmpty(actionButtonTextString)) {
        primaryAction = null;
      } else {
        Text primaryButtonText =
            Text.builder()
                .setText(actionButtonTextString)
                .setHexColor(buttonTextColorString)
                .build();
        com.google.firebase.inappmessaging.model.Button primaryActionButton =
            com.google.firebase.inappmessaging.model.Button.builder()
                .setText(primaryButtonText)
                .setButtonHexColor(buttonBackgroundColorString)
                .build();
        primaryAction =
            Action.builder()
                .setActionUrl(actionButtonUrlString)
                .setButton(primaryActionButton)
                .build();
      }

      // Secondary button text
      Action secondaryAction;
      if (TextUtils.isEmpty(secondaryActionButtonTextString)) {
        secondaryAction = null;
      } else {
        Text secondaryButtonText =
            Text.builder()
                .setText(secondaryActionButtonTextString)
                .setHexColor(buttonTextColorString)
                .build();
        com.google.firebase.inappmessaging.model.Button secondaryActionButton =
            com.google.firebase.inappmessaging.model.Button.builder()
                .setText(secondaryButtonText)
                .setButtonHexColor(buttonBackgroundColorString)
                .build();
        secondaryAction =
            Action.builder()
                .setActionUrl(actionButtonUrlString)
                .setButton(secondaryActionButton)
                .build();
      }

      CardMessage message =
          builder
              .setBackgroundHexColor(bodyBackgroundColorString)
              .setTitle(title)
              .setBody(body)
              .setPortraitImageData(imageData)
              .setLandscapeImageData(landscapeImageData)
              .setPrimaryAction(primaryAction)
              .setSecondaryAction(secondaryAction)
              .build(campaignMetadata);

      FirebaseInAppMessagingDisplay.getInstance()
          .testMessage(this, message, new NoOpDisplayCallbacks());

    } else {
      ModalMessage.Builder builder = ModalMessage.builder();

      Text title =
          Text.builder()
              .setText(messageTitle.getText().toString())
              .setHexColor(bodyTextColorString)
              .build();
      Text body = Text.builder().setText(bodyText).setHexColor(bodyTextColorString).build();

      Action modalAction;
      if (TextUtils.isEmpty(actionButtonTextString)) {
        modalAction = null;
      } else {
        Text buttonText =
            Text.builder()
                .setText(actionButtonTextString)
                .setHexColor(buttonTextColorString)
                .build();
        com.google.firebase.inappmessaging.model.Button actionButton =
            com.google.firebase.inappmessaging.model.Button.builder()
                .setText(buttonText)
                .setButtonHexColor(buttonBackgroundColorString)
                .build();
        modalAction =
            Action.builder().setActionUrl(actionButtonUrlString).setButton(actionButton).build();
      }

      ModalMessage message =
          builder
              .setBackgroundHexColor(bodyBackgroundColorString)
              .setTitle(title)
              .setBody(body)
              .setImageData(imageData)
              .setAction(modalAction)
              .build(campaignMetadata);

      FirebaseInAppMessagingDisplay.getInstance()
          .testMessage(this, message, new NoOpDisplayCallbacks());
    }
  }

  @StringRes
  private int getSelectedBodyText() {
    if (useLongBodyText.isChecked()) {
      return R.string.body_text_long;
    }

    if (useNoBodyText.isChecked()) {
      return R.string.no_body_text;
    }

    return R.string.body_text_normal;
  }

  private int getBackgroundColor(View view) {
    ColorDrawable drawable = (ColorDrawable) view.getBackground();
    return drawable.getColor();
  }

  private String getBackgroundColorString(View view) {
    return String.format("#%06X", (0xFFFFFF & getBackgroundColor(view)));
  }

  @Nullable
  private String makeImageUrl(TextInputEditText imageWidth, TextInputEditText imageHeight) {
    String w = imageWidth.getText().toString();
    String h = imageHeight.getText().toString();

    return "0".equals(w) && "0".equals(h) ? null : "https://unsplash.it/" + w + "/" + h;
  }

  private void bindColorPicker(View container, final View preview) {
    container.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            new ChromaDialog.Builder()
                .initialColor(getBackgroundColor(preview))
                .colorMode(ColorMode.RGB)
                .onColorSelected(
                    new ColorSelectListener() {
                      @Override
                      public void onColorSelected(int i) {
                        preview.setBackgroundColor(i);
                      }
                    })
                .create()
                .show(getSupportFragmentManager(), "ChromaDialog");
          }
        });
  }
}
