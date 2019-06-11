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
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
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

  @BindView(R.id.action_button_text)
  TextInputEditText actionButtonText;

  @BindView(R.id.action_button_url)
  TextInputEditText actionButtonUrl;

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

    String w = imageWidth.getText().toString();
    String h = imageHeight.getText().toString();

    String imageUrlString;
    if ("0".equals(w) && "0".equals(h)) {
      imageUrlString = null;
    } else {
      imageUrlString = "https://unsplash.it/" + w + "/" + h;
    }

    String actionButtonTextString = actionButtonText.getText().toString();
    String actionButtonUrlString = actionButtonUrl.getText().toString();

    InAppMessage.Builder builder = InAppMessage.builder();

    if (!bodyText.equals("")) {
      InAppMessage.Text body =
          InAppMessage.Text.builder().setText(bodyText).setHexColor(bodyTextColorString).build();
      builder = builder.setBody(body);
    }

    if (!actionButtonTextString.equals("")) {
      InAppMessage.Text buttonText =
          InAppMessage.Text.builder()
              .setHexColor(buttonTextColorString)
              .setText(actionButtonTextString)
              .build();
      InAppMessage.Action action =
          InAppMessage.Action.builder().setActionUrl(actionButtonUrlString).build();
      com.google.firebase.inappmessaging.model.InAppMessage.Button actionButton =
          com.google.firebase.inappmessaging.model.InAppMessage.Button.builder()
              .setText(buttonText)
              .setButtonHexColor(buttonBackgroundColorString)
              .build();
      builder = builder.setAction(action).setActionButton(actionButton);
    }

    InAppMessage.Text title =
        InAppMessage.Text.builder()
            .setHexColor(bodyTextColorString)
            .setText(messageTitle.getText().toString())
            .build();

    builder =
        builder
            .setCampaignId("test_campaign")
            .setBackgroundHexColor(bodyBackgroundColorString)
            .setCampaignName("name")
            .setIsTestMessage(true)
            .setTitle(title);

    if (imageUrlString != null) {
      builder = builder.setImageUrl(imageUrlString);
    }

    if (useImageFiam.isChecked()) {
      builder.setMessageType(MessageType.IMAGE_ONLY);
    } else if (useBannerFiam.isChecked()) {
      builder.setMessageType(MessageType.BANNER);
    } else {
      builder.setMessageType(MessageType.MODAL);
    }

    FirebaseInAppMessagingDisplay.getInstance()
        .testMessage(this, builder.build(), new NoOpDisplayCallbacks());
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
