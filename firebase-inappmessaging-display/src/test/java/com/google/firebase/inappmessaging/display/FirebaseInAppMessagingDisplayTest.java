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

package com.google.firebase.inappmessaging.display;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.inappmessaging.display.internal.FiamImageLoader.FiamImageRequestCreator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.RuntimeEnvironment.application;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.res.Configuration;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplay;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.display.internal.BindingWrapperFactory;
import com.google.firebase.inappmessaging.display.internal.FiamAnimator;
import com.google.firebase.inappmessaging.display.internal.FiamImageLoader;
import com.google.firebase.inappmessaging.display.internal.FiamWindowManager;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.RenewableTimer;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.BannerBindingWrapper;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.ImageBindingWrapper;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.ModalBindingWrapper;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterConfigModule;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import com.squareup.picasso.Callback;
import com.squareup.picasso.RequestCreator;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, qualifiers = "port")
public class FirebaseInAppMessagingDisplayTest {

  private com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay fiamUI;
  private Map<String, Provider<InAppMessageLayoutConfig>> layoutConfigs;

  @Mock private FirebaseInAppMessaging headless;
  @Mock private FiamImageLoader imageLoader;
  @Mock private RenewableTimer impressionTimer;
  @Mock private RenewableTimer autoDismissTimer;
  @Mock private FiamWindowManager windowManager;
  @Captor private ArgumentCaptor<FirebaseInAppMessagingDisplay> inAppMessageTriggerListenerCaptor;
  @Captor private ArgumentCaptor<OnClickListener> onClickListenerArgCaptor;
  @Captor private ArgumentCaptor<View.OnTouchListener> onTouchListenerArgumentCaptor;
  @Captor private ArgumentCaptor<Callback> callbackArgCaptor;
  @Captor private ArgumentCaptor<RenewableTimer.Callback> timerArgCaptor;

  @Captor
  private ArgumentCaptor<FiamAnimator.AnimationCompleteListener>
      animationCompleteListenerArgumentCaptor;

  private static final String IMAGE_URL = "https://www.imgur.com";
  private static final String CAMPAIGN_ID = "campaign_id";
  private static final String CAMPAIGN_NAME = "campaign_name";
  private static final String ACTION_URL = "https://www.google.com";
  private static final InAppMessage.Action ACTION =
      InAppMessage.Action.builder().setActionUrl(ACTION_URL).build();
  private static final InAppMessage IMAGE_ONLY_MESSAGE =
      InAppMessage.builder()
          .setCampaignId(CAMPAIGN_ID)
          .setIsTestMessage(false)
          .setCampaignName(CAMPAIGN_NAME)
          .setAction(ACTION)
          .setMessageType(MessageType.IMAGE_ONLY)
          .setImageUrl(IMAGE_URL)
          .build();
  private static final InAppMessage MODAL_MESSAGE =
      InAppMessage.builder()
          .setCampaignId(CAMPAIGN_ID)
          .setIsTestMessage(false)
          .setCampaignName(CAMPAIGN_NAME)
          .setAction(ACTION)
          .setMessageType(MessageType.MODAL)
          .setImageUrl(IMAGE_URL)
          .build();
  private static final InAppMessage BANNER_MESSAGE =
      InAppMessage.builder()
          .setCampaignId(CAMPAIGN_ID)
          .setIsTestMessage(false)
          .setCampaignName(CAMPAIGN_NAME)
          .setAction(ACTION)
          .setMessageType(MessageType.BANNER)
          .setImageUrl(IMAGE_URL)
          .build();
  private static final InAppMessageLayoutConfig inappMessageLayoutConfig =
      InAppMessageLayoutConfig.builder()
          .setMaxDialogHeightPx((int) (0.9f * 1000))
          .setMaxDialogWidthPx((int) (0.9f * 1000))
          .setMaxImageWidthWeight(0.8f)
          .setMaxImageHeightWeight(0.8f)
          .setViewWindowGravity(Gravity.CENTER)
          .setWindowFlag(1)
          .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setBackgroundEnabled(false)
          .setAnimate(false)
          .setAutoDismiss(false)
          .build();
  private static final InAppMessageLayoutConfig bannerConfig =
      InAppMessageLayoutConfig.builder()
          .setMaxDialogHeightPx((int) (0.9f * 1000))
          .setMaxDialogWidthPx((int) (0.9f * 1000))
          .setMaxImageWidthWeight(0.8f)
          .setMaxImageHeightWeight(0.8f)
          .setViewWindowGravity(Gravity.CENTER)
          .setWindowFlag(1)
          .setWindowWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setWindowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
          .setBackgroundEnabled(true)
          .setAnimate(true)
          .setAutoDismiss(true)
          .build();

  @Mock private BindingWrapperFactory bindingClient;
  @Mock private FiamListener fiamUIListener;
  @Mock private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
  @Mock private FiamAnimator animator;
  @Mock private FirebaseInAppMessagingDisplayCallbacks callbacks;

  private ImageBindingWrapper imageBindingWrapper;
  private ModalBindingWrapper modalBindingWrapper;
  private BannerBindingWrapper bannerBindingWrapper;

  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private FirebaseInAppMessagingDisplay listener;
  private FiamImageRequestCreator fakeRequestCreator = spy(new FakeRequestCreater(null));

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    layoutConfigs = new HashMap<>();
    layoutConfigs.put(
        InflaterConfigModule.configFor(
            IMAGE_ONLY_MESSAGE.getMessageType(), Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return inappMessageLayoutConfig;
          }
        });
    layoutConfigs.put(
        InflaterConfigModule.configFor(
            MODAL_MESSAGE.getMessageType(), Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return inappMessageLayoutConfig;
          }
        });
    layoutConfigs.put(
        InflaterConfigModule.configFor(
            BANNER_MESSAGE.getMessageType(), Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return bannerConfig;
          }
        });

    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);

    LayoutInflater inflater = LayoutInflater.from(application);
    imageBindingWrapper =
        spy(new ImageBindingWrapper(inappMessageLayoutConfig, inflater, IMAGE_ONLY_MESSAGE));
    modalBindingWrapper =
        spy(new ModalBindingWrapper(inappMessageLayoutConfig, inflater, MODAL_MESSAGE));
    bannerBindingWrapper = spy(new BannerBindingWrapper(BANNER_MESSAGE, inflater, bannerConfig));

    when(bindingClient.createImageBindingWrapper(
            eq(inappMessageLayoutConfig), any(InAppMessage.class)))
        .thenReturn(imageBindingWrapper);
    when(bindingClient.createModalBindingWrapper(
            eq(inappMessageLayoutConfig), any(InAppMessage.class)))
        .thenReturn(modalBindingWrapper);
    when(bindingClient.createBannerBindingWrapper(eq(bannerConfig), any(InAppMessage.class)))
        .thenReturn(bannerBindingWrapper);

    when(imageLoader.load(IMAGE_URL)).thenReturn(fakeRequestCreator);
    fiamUI =
        new com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay(
            headless,
            layoutConfigs,
            imageLoader,
            impressionTimer,
            autoDismissTimer,
            windowManager,
            application,
            bindingClient,
            animator);
  }

  @Test
  public void onActivityStarted_listensToFiamStream() {
    fiamUI.onActivityStarted(activity);

    verify(headless).setMessageDisplayComponent(any(FirebaseInAppMessagingDisplay.class));
  }

  @Test
  public void onActivityPaused_clearsDisplayListener() {
    fiamUI.onActivityPaused(activity);

    verify(headless).clearDisplayListener();
  }

  @Test
  public void onActivityPaused_clearsImageDownload() {
    fiamUI.onActivityPaused(activity);

    verify(imageLoader).cancelTag(activity.getClass());
  }

  @Test
  public void onActivityPaused_destroysInAppMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    fiamUI.onActivityPaused(activity);

    verify(windowManager).destroy(activity);
  }

  @Test
  public void onActivityPaused_cancelsImpressionTimer() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    fiamUI.onActivityPaused(activity);

    verify(impressionTimer).cancel();
  }

  @Test
  public void onActivityPaused_cancelsAutoDismissTImer() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    fiamUI.onActivityPaused(activity);

    verify(autoDismissTimer).cancel();
  }

  @Test
  public void onActivityResumed_whenFiamActive_showsFiam() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    fiamUI.onActivityResumed(activity);
    verify(fakeRequestCreator, times(2)).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    // assert that fiam was shown once originally and once after resuming
    verify(windowManager, times(2)).show(imageBindingWrapper, activity);
  }

  @Test
  public void streamListener_onNotifiedImage_showsImageMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(windowManager).show(imageBindingWrapper, activity);
  }

  @Test
  public void streamListener_onNotifiedModal_showsModalMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(MODAL_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(windowManager).show(modalBindingWrapper, activity);
  }

  @Test
  public void streamListener_onNotified_InflatesBinding() {
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);

    verify(imageBindingWrapper).inflate(any(OnClickListener.class), any(OnClickListener.class));
  }

  @Test
  public void streamListener_onNotifiedModalMessage_setsLayoutListener() {
    startActivity();

    ViewTreeObserver.OnGlobalLayoutListener mockListener =
        mock(ViewTreeObserver.OnGlobalLayoutListener.class);
    modalBindingWrapper.setLayoutListener(mockListener);

    listener.displayMessage(MODAL_MESSAGE, callbacks);

    modalBindingWrapper.getImageView().getViewTreeObserver().dispatchOnGlobalLayout();
    verify(mockListener).onGlobalLayout();
  }

  @Test
  public void streamListener_whenNoActionUrlIsSet_dismissesFiam() {
    InAppMessage inAppMessage =
        InAppMessage.builder()
            .setCampaignId(CAMPAIGN_ID)
            .setIsTestMessage(false)
            .setCampaignName(CAMPAIGN_NAME)
            .setAction(InAppMessage.Action.builder().setActionUrl("").build())
            .setMessageType(MessageType.IMAGE_ONLY)
            .setImageUrl(IMAGE_URL)
            .build();
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(inAppMessage, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor.getValue().onClick(null);

    verify(windowManager).destroy(activity);
  }

  @Test
  public void streamListener_whenImageUrlExists_loadsImage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);

    verify(fakeRequestCreator).tag(TestActivity.class);
    verify(fakeRequestCreator).placeholder(R.drawable.image_placeholder);
    verify(fakeRequestCreator).into(any(ImageView.class), any(Callback.class));
  }

  @Test
  public void streamListener_whenNoImageUrlExists_doesNotLoadImage() {
    InAppMessage inAppMessage =
        InAppMessage.builder()
            .setCampaignId(CAMPAIGN_ID)
            .setIsTestMessage(false)
            .setCampaignName(CAMPAIGN_NAME)
            .setAction(InAppMessage.Action.builder().setActionUrl("").build())
            .setMessageType(MessageType.IMAGE_ONLY)
            .build();
    startActivity();
    listener.displayMessage(inAppMessage, callbacks);

    verify(fakeRequestCreator, times(0)).tag(TestActivity.class);
    verify(fakeRequestCreator, times(0)).placeholder(R.drawable.image_placeholder);
    verify(fakeRequestCreator, times(0)).into(any(ImageView.class), any(Callback.class));
  }

  @Test
  public void streamListener_whenImageLoadSucceeds_showsWindow() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator)
        .into(eq(imageBindingWrapper.getImageView()), callbackArgCaptor.capture());

    callbackArgCaptor.getValue().onSuccess();

    verify(windowManager).show(imageBindingWrapper, activity);
  }

  @Test
  public void streamListener_whenImageLoadSucceeds_startsImpressionTimer() {
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());

    callbackArgCaptor.getValue().onSuccess();

    verify(impressionTimer)
        .start(
            any(RenewableTimer.Callback.class),
            ArgumentMatchers.eq(
                com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay
                    .IMPRESSION_THRESHOLD_MILLIS),
            ArgumentMatchers.eq(
                com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay
                    .INTERVAL_MILLIS));
  }

  @Test
  public void streamListener_whenImageLoadSucceedsForAutoDismissFiam_startsDismissTimer() {
    startActivity();
    listener.displayMessage(BANNER_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());

    callbackArgCaptor.getValue().onSuccess();

    verify(autoDismissTimer)
        .start(
            any(RenewableTimer.Callback.class),
            ArgumentMatchers.eq(
                com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay
                    .DISMISS_THRESHOLD_MILLIS),
            ArgumentMatchers.eq(
                com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay
                    .INTERVAL_MILLIS));
  }

  // Not strictly necessary since in practice, the timer should not have been started
  @Test
  public void streamListener_whenImageLoadFails_stopsImpressionTimer() {
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());

    callbackArgCaptor.getValue().onError();

    verify(impressionTimer).cancel();
  }

  // Not strictly necessary since in practice, the timer should not have been started
  @Test
  public void streamListener_whenImageLoadFails_stopsDismissTimer() {
    startActivity();
    listener.displayMessage(BANNER_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());

    callbackArgCaptor.getValue().onError();

    verify(autoDismissTimer).cancel();
  }

  @Test
  public void streamListener_whenImageLoadFailsForModal_removesLayoutListener() {
    startActivity();
    listener.displayMessage(MODAL_MESSAGE, callbacks);
    verify(fakeRequestCreator)
        .into(eq(modalBindingWrapper.getImageView()), callbackArgCaptor.capture());

    ViewTreeObserver.OnGlobalLayoutListener mockListener =
        mock(ViewTreeObserver.OnGlobalLayoutListener.class);

    modalBindingWrapper.setLayoutListener(mockListener);
    callbackArgCaptor.getValue().onError();

    // Verify that the listener is no longer called
    modalBindingWrapper.getImageView().getViewTreeObserver().dispatchOnGlobalLayout();
    verify(mockListener, never()).onGlobalLayout();
  }

  @Test
  public void streamListener_forBackgroundDisabledFiams_dismissesFiamOnClickOutside() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    imageBindingWrapper
        .getRootView()
        .dispatchTouchEvent(MotionEvent.obtain(1, 2, MotionEvent.ACTION_OUTSIDE, 1, 2, 1));

    verify(windowManager).destroy(activity);
  }

  @Test
  public void streamListener_forBackgroundDisabledFiams_returnsTrueOnTouchEvents() {
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    boolean ret =
        imageBindingWrapper
            .getRootView()
            .dispatchTouchEvent(MotionEvent.obtain(1, 2, MotionEvent.ACTION_OUTSIDE, 1, 2, 1));

    assertThat(ret).isTrue();
  }

  @Test
  public void streamListener_onNotifiedAnimatableMessage_animatesEntry() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(BANNER_MESSAGE, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(animator).slideIntoView(eq(application), any(View.class), eq(FiamAnimator.Position.TOP));
  }

  @Test
  public void impressionTimer_onComplete_firesImpressionLogAction() {
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    verify(impressionTimer).start(timerArgCaptor.capture(), anyLong(), anyLong());

    timerArgCaptor.getValue().onFinish();

    verify(callbacks, times(1)).impressionDetected();
  }

  @Test
  public void dismissTimer_onComplete_dismissesFiam() {
    startActivity();
    listener.displayMessage(BANNER_MESSAGE, callbacks);
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    verify(autoDismissTimer).start(timerArgCaptor.capture(), anyLong(), anyLong());

    timerArgCaptor.getValue().onFinish();

    verify(windowManager).destroy(activity);
  }

  @Test
  public void fiamClickListener_whenActionUrlProvided_opensCustomTab() {
    startActivity();
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor.getValue().onClick(null);

    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(ACTION_URL));
  }

  @Test
  public void dismissClickListener_dismissesFiam() {
    InAppMessage inAppMessage =
        InAppMessage.builder()
            .setCampaignId(CAMPAIGN_ID)
            .setIsTestMessage(false)
            .setCampaignName(CAMPAIGN_NAME)
            .setAction(InAppMessage.Action.builder().setActionUrl("").build())
            .setMessageType(MessageType.IMAGE_ONLY)
            .setImageUrl(IMAGE_URL)
            .build();
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    startActivity();
    listener.displayMessage(inAppMessage, callbacks);
    verify(imageBindingWrapper)
        .inflate(any(OnClickListener.class), onClickListenerArgCaptor.capture());
    onClickListenerArgCaptor.getValue().onClick(null);

    verify(windowManager).destroy(activity);
  }

  @Test
  public void firebaseInAppMessagingUIListener_whenFiamRendered_receivesOnFiamTrigger() {
    startActivity();
    fiamUI.setFiamListener(fiamUIListener);
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);

    verify(fiamUIListener).onFiamTrigger();
  }

  @Test
  public void fiamUIListener_whenFiamClicked_receivesOnFiamClick() {
    startActivity();
    fiamUI.setFiamListener(fiamUIListener);
    listener.displayMessage(IMAGE_ONLY_MESSAGE, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor.getValue().onClick(null);

    verify(fiamUIListener).onFiamTrigger();
  }

  @Test
  public void inflate_setsActionListenerToDismissFiamOnClick() throws Exception {
    startActivity();
    fiamUI.setFiamListener(fiamUIListener);
    listener.displayMessage(MODAL_MESSAGE, callbacks);
    verify(modalBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    Button button = modalBindingWrapper.getActionButton();

    assertThat(fiamUI.getCurrentInAppMessage()).isNotNull();
    assertThat(fiamUI.getCurrentInAppMessage()).isEqualTo(MODAL_MESSAGE);
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    button.performClick();
    // Assert that after the messageClick, we now have a null FIAM
    assertThat(fiamUI.getCurrentInAppMessage()).isNull();
    verify(windowManager, times(1)).destroy(any(Activity.class));
  }

  private void startActivity() {
    fiamUI.onActivityStarted(activity);
    verify(headless).setMessageDisplayComponent(inAppMessageTriggerListenerCaptor.capture());
    listener = inAppMessageTriggerListenerCaptor.getValue();
  }

  static class TestActivity extends Activity {}

  static class FakeRequestCreater extends FiamImageRequestCreator {
    public FakeRequestCreater(RequestCreator requestCreator) {
      super(requestCreator);
    }

    @Override
    public FiamImageRequestCreator placeholder(int placeholderResId) {
      return this;
    }

    @Override
    public FiamImageRequestCreator tag(Class c) {
      return this;
    }

    @Override
    public void into(ImageView imageView, Callback callback) {
      // do nothing
    }
  }
}
