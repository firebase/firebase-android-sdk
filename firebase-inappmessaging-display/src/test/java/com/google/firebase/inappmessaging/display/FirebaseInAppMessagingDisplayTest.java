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
import static com.google.firebase.inappmessaging.testutil.TestData.ACTION_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CARD_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_MESSAGE_MODEL_WITHOUT_ACTION;
import static com.google.firebase.inappmessaging.testutil.TestData.IMAGE_URL_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.MODAL_MESSAGE_MODEL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.RuntimeEnvironment.application;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
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
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.CardBindingWrapper;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.ImageBindingWrapper;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.ModalBindingWrapper;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterConfigModule;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import com.squareup.picasso.Callback;
import com.squareup.picasso.RequestCreator;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Ignore;
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
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, qualifiers = "port")
public class FirebaseInAppMessagingDisplayTest {

  private com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay fiamUI;
  private Map<String, Provider<InAppMessageLayoutConfig>> layoutConfigs;
  private DisplayMetrics testDisplayMetrics = new DisplayMetrics();
  private InflaterConfigModule inflaterConfigModule = new InflaterConfigModule();

  @Mock private FirebaseInAppMessaging headless;
  @Mock private FiamImageLoader imageLoader;
  @Mock private RenewableTimer impressionTimer;
  @Mock private RenewableTimer autoDismissTimer;
  @Mock private FiamWindowManager windowManager;
  @Captor private ArgumentCaptor<FirebaseInAppMessagingDisplay> inAppMessageTriggerListenerCaptor;
  @Captor private ArgumentCaptor<Map<Action, OnClickListener>> onClickListenerArgCaptor;
  @Captor private ArgumentCaptor<OnClickListener> onDismissListenerArgCaptor;
  @Captor private ArgumentCaptor<Callback> callbackArgCaptor;
  @Captor private ArgumentCaptor<RenewableTimer.Callback> timerArgCaptor;

  @Captor
  private ArgumentCaptor<FiamAnimator.AnimationCompleteListener>
      animationCompleteListenerArgumentCaptor;

  @Mock private BindingWrapperFactory bindingClient;
  @Mock private FiamListener fiamUIListener;
  @Mock private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
  @Mock private FiamAnimator animator;
  @Mock private FirebaseInAppMessagingDisplayCallbacks callbacks;

  private ImageBindingWrapper imageBindingWrapper;
  private InAppMessageLayoutConfig imageLayoutConfig;
  private ModalBindingWrapper modalBindingWrapper;
  private InAppMessageLayoutConfig modalLayoutConfig;
  private BannerBindingWrapper bannerBindingWrapper;
  private InAppMessageLayoutConfig bannerLayoutConfig;
  private CardBindingWrapper cardBindingWrapper;
  private InAppMessageLayoutConfig cardLayoutConfig;

  private TestActivity activity;
  private TestSecondActivity activityTwo;
  private ShadowActivity shadowActivity;
  private ShadowPackageManager shadowPackageManager;
  private FirebaseInAppMessagingDisplay listener;
  private FiamImageRequestCreator fakeRequestCreator = spy(new FakeRequestCreater(null));

  @Before
  public void setup() {
    testDisplayMetrics.widthPixels = 1000;
    testDisplayMetrics.widthPixels = 2000;
    modalLayoutConfig = inflaterConfigModule.providesModalPortraitConfig(testDisplayMetrics);
    imageLayoutConfig = inflaterConfigModule.providesPortraitImageLayoutConfig(testDisplayMetrics);
    bannerLayoutConfig =
        inflaterConfigModule.providesBannerPortraitLayoutConfig(testDisplayMetrics);
    cardLayoutConfig = inflaterConfigModule.providesCardPortraitConfig(testDisplayMetrics);

    MockitoAnnotations.initMocks(this);
    layoutConfigs = new HashMap<>();
    layoutConfigs.put(
        InflaterConfigModule.configFor(MessageType.MODAL, Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return modalLayoutConfig;
          }
        });
    layoutConfigs.put(
        InflaterConfigModule.configFor(MessageType.IMAGE_ONLY, Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return imageLayoutConfig;
          }
        });
    layoutConfigs.put(
        InflaterConfigModule.configFor(MessageType.BANNER, Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return bannerLayoutConfig;
          }
        });
    layoutConfigs.put(
        InflaterConfigModule.configFor(MessageType.CARD, Configuration.ORIENTATION_PORTRAIT),
        new Provider<InAppMessageLayoutConfig>() {
          @Override
          public InAppMessageLayoutConfig get() {
            return cardLayoutConfig;
          }
        });

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    activityTwo = Robolectric.buildActivity(TestSecondActivity.class).create().get();
    shadowActivity = shadowOf(activity);

    LayoutInflater inflater = LayoutInflater.from(application);
    imageBindingWrapper =
        spy(new ImageBindingWrapper(imageLayoutConfig, inflater, IMAGE_MESSAGE_MODEL));
    modalBindingWrapper =
        spy(new ModalBindingWrapper(modalLayoutConfig, inflater, MODAL_MESSAGE_MODEL));
    bannerBindingWrapper =
        spy(new BannerBindingWrapper(bannerLayoutConfig, inflater, BANNER_MESSAGE_MODEL));
    cardBindingWrapper =
        spy(new CardBindingWrapper(cardLayoutConfig, inflater, CARD_MESSAGE_MODEL));

    when(bindingClient.createImageBindingWrapper(eq(imageLayoutConfig), any(InAppMessage.class)))
        .thenReturn(imageBindingWrapper);
    when(bindingClient.createModalBindingWrapper(eq(modalLayoutConfig), any(InAppMessage.class)))
        .thenReturn(modalBindingWrapper);
    when(bindingClient.createBannerBindingWrapper(eq(bannerLayoutConfig), any(InAppMessage.class)))
        .thenReturn(bannerBindingWrapper);

    when(imageLoader.load(IMAGE_URL_STRING)).thenReturn(fakeRequestCreator);
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
    resumeActivity(activity);

    verify(headless).setMessageDisplayComponent(any(FirebaseInAppMessagingDisplay.class));
  }

  @Test
  public void onActivitPaused_clearsListeners() {
    resumeActivity(activity);
    pauseActivity(activity);

    verify(headless).removeAllListeners();
  }

  @Test
  public void onActivityPaused_clearsDisplayListener() {
    resumeActivity(activity);
    pauseActivity(activity);

    verify(headless).clearDisplayListener();
  }

  @Test
  public void onActivityPaused_clearsImageDownload() {
    resumeActivity(activity);
    pauseActivity(activity);

    verify(imageLoader).cancelTag(activity.getClass());
  }

  @Test
  public void onActivityPaused_destroysInAppMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    pauseActivity(activity);

    verify(windowManager).destroy(activity);
  }

  @Test
  public void onActivityPaused_cancelsImpressionTimer() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    pauseActivity(activity);

    verify(impressionTimer).cancel();
  }

  @Test
  public void onActivityPaused_cancelsAutoDismissTImer() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    pauseActivity(activity);

    verify(autoDismissTimer).cancel();
  }

  @Test
  public void onActivityNewActivityStarted_displaysFiamInNewActivity() {
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    // fiam should be displayed with reference to the first activity
    verify(windowManager).show(imageBindingWrapper, activity);

    pauseActivity(activity);
    resumeActivity(activityTwo);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator, times(2)).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    // fiam should be displayed with reference to the second activity
    verify(windowManager).show(imageBindingWrapper, activityTwo);
  }

  @Test
  public void onActivityOldActivityDestroyed_displaysFiamInNewActivity() {
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    // fiam should be displayed with reference to the first activity
    verify(windowManager).show(imageBindingWrapper, activity);

    pauseActivity(activity);
    resumeActivity(activityTwo);
    // android lifecycle destroys first activity after second is created
    fiamUI.onActivityDestroyed(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator, times(2)).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    // fiam should be displayed with reference to the second activity
    verify(windowManager).show(imageBindingWrapper, activityTwo);
  }

  @Test
  public void onActivityResumed_whenFiamActive_showsFiam() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    pauseActivity(activity);
    resumeActivity(activity);

    verify(fakeRequestCreator, times(2)).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    // assert that fiam was shown once originally and once after resuming
    verify(windowManager, times(2)).show(imageBindingWrapper, activity);
  }

  @Test
  public void streamListener_onNotifiedImage_showsImageMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(windowManager).show(imageBindingWrapper, activity);
  }

  @Test
  public void streamListener_onNotifiedModal_showsModalMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(MODAL_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(windowManager).show(modalBindingWrapper, activity);
  }

  @Test
  @Ignore
  public void streamListener_onNotifiedCard_showsCardMessage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(CARD_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(windowManager).show(cardBindingWrapper, activity);
  }

  @Test
  public void streamListener_onNotified_InflatesBinding() {
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);

    verify(imageBindingWrapper).inflate(any(Map.class), any(OnClickListener.class));
  }

  @Test
  public void streamListener_onNotifiedModalMessage_setsLayoutListener() {
    resumeActivity(activity);

    ViewTreeObserver.OnGlobalLayoutListener mockListener =
        mock(ViewTreeObserver.OnGlobalLayoutListener.class);
    modalBindingWrapper.setLayoutListener(mockListener);

    listener.displayMessage(MODAL_MESSAGE_MODEL, callbacks);

    modalBindingWrapper.getImageView().getViewTreeObserver().dispatchOnGlobalLayout();
    verify(mockListener).onGlobalLayout();
  }

  @Test
  public void streamListener_whenNoActionUrlIsSet_dismissesFiam() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL_WITHOUT_ACTION, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor
        .getValue()
        .get(IMAGE_MESSAGE_MODEL_WITHOUT_ACTION.getAction())
        .onClick(null);

    verify(windowManager).destroy(activity);
  }

  @Test
  public void streamListener_whenImageUrlExists_loadsImage() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);

    verify(fakeRequestCreator).tag(TestActivity.class);
    verify(fakeRequestCreator).placeholder(R.drawable.image_placeholder);
    verify(fakeRequestCreator).into(any(ImageView.class), any(Callback.class));
  }

  @Test
  public void streamListener_whenNoImageUrlExists_doesNotLoadImage() {
    resumeActivity(activity);
    listener.displayMessage(null, callbacks);
    verify(fakeRequestCreator, times(0)).tag(TestActivity.class);
    verify(fakeRequestCreator, times(0)).placeholder(R.drawable.image_placeholder);
    verify(fakeRequestCreator, times(0)).into(any(ImageView.class), any(Callback.class));
  }

  @Test
  public void streamListener_whenImageLoadSucceeds_showsWindow() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator)
        .into(eq(imageBindingWrapper.getImageView()), callbackArgCaptor.capture());

    callbackArgCaptor.getValue().onSuccess();
    verify(windowManager).show(imageBindingWrapper, activity);
  }

  @Test
  public void streamListener_whenImageLoadSucceeds_startsImpressionTimer() {
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
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
    resumeActivity(activity);
    listener.displayMessage(BANNER_MESSAGE_MODEL, callbacks);
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
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());

    Exception e = new IOException();
    callbackArgCaptor.getValue().onError(e);
    verify(impressionTimer).cancel();
  }

  // Not strictly necessary since in practice, the timer should not have been started
  @Test
  public void streamListener_whenImageLoadFails_stopsDismissTimer() {
    resumeActivity(activity);
    listener.displayMessage(BANNER_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    Exception e = new IOException();
    callbackArgCaptor.getValue().onError(e);
    verify(autoDismissTimer).cancel();
  }

  @Test
  public void streamListener_whenImageLoadFailsForModal_removesLayoutListener() {
    resumeActivity(activity);
    listener.displayMessage(MODAL_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator)
        .into(eq(modalBindingWrapper.getImageView()), callbackArgCaptor.capture());

    ViewTreeObserver.OnGlobalLayoutListener mockListener =
        mock(ViewTreeObserver.OnGlobalLayoutListener.class);

    modalBindingWrapper.setLayoutListener(mockListener);
    Exception e = new IOException();
    callbackArgCaptor.getValue().onError(e);

    // Verify that the listener is no longer called
    modalBindingWrapper.getImageView().getViewTreeObserver().dispatchOnGlobalLayout();
    verify(mockListener, never()).onGlobalLayout();
  }

  @Test
  public void streamListener_forBackgroundDisabledFiams_dismissesFiamOnClickOutside() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    imageBindingWrapper
        .getRootView()
        .dispatchTouchEvent(MotionEvent.obtain(1, 2, MotionEvent.ACTION_OUTSIDE, 1, 2, 1));
    verify(windowManager).destroy(activity);
  }

  @Test
  public void streamListener_forBackgroundDisabledFiams_returnsTrueOnTouchEvents() {
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
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
    resumeActivity(activity);
    listener.displayMessage(BANNER_MESSAGE_MODEL, callbacks);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();

    verify(animator).slideIntoView(eq(application), any(View.class), eq(FiamAnimator.Position.TOP));
  }

  @Test
  public void impressionTimer_onComplete_firesImpressionLogAction() {
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    verify(impressionTimer).start(timerArgCaptor.capture(), anyLong(), anyLong());

    timerArgCaptor.getValue().onFinish();

    verify(callbacks, times(1)).impressionDetected();
  }

  @Test
  public void dismissTimer_onComplete_dismissesFiam() {
    resumeActivity(activity);
    listener.displayMessage(BANNER_MESSAGE_MODEL, callbacks);
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    verify(fakeRequestCreator).into(any(ImageView.class), callbackArgCaptor.capture());
    callbackArgCaptor.getValue().onSuccess();
    verify(autoDismissTimer).start(timerArgCaptor.capture(), anyLong(), anyLong());

    timerArgCaptor.getValue().onFinish();

    verify(windowManager).destroy(activity);
  }

  @Test
  public void fiamClickListener_whenActionUrlProvided_andChromeAvailable_opensCustomTab() {
    final ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    final Intent customTabIntent =
        new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    shadowPackageManager.addResolveInfoForIntent(customTabIntent, resolveInfo);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor.getValue().get(IMAGE_MESSAGE_MODEL.getAction()).onClick(null);
    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(ACTION_URL_STRING));
  }

  @Test
  public void fiamClickListener_whenActionUrlProvided_andBrowserAvailable_opensBrowserIntent() {
    final ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ACTION_URL_STRING));
    shadowPackageManager.addResolveInfoForIntent(browserIntent, resolveInfo);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor.getValue().get(IMAGE_MESSAGE_MODEL.getAction()).onClick(null);
    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(ACTION_URL_STRING));
  }

  @Test
  public void dismissClickListener_dismissesFiam() {
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    resumeActivity(activity);
    listener.displayMessage(IMAGE_MESSAGE_MODEL_WITHOUT_ACTION, callbacks);
    verify(imageBindingWrapper).inflate(any(Map.class), onDismissListenerArgCaptor.capture());
    onDismissListenerArgCaptor.getValue().onClick(null);

    verify(windowManager).destroy(activity);
  }

  @Test
  public void firebaseInAppMessagingUIListener_whenFiamRendered_receivesOnFiamTrigger() {
    resumeActivity(activity);
    fiamUI.setFiamListener(fiamUIListener);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);

    verify(fiamUIListener).onFiamTrigger();
  }

  @Test
  public void fiamUIListener_whenFiamClicked_receivesOnFiamClick() {
    resumeActivity(activity);
    fiamUI.setFiamListener(fiamUIListener);
    listener.displayMessage(IMAGE_MESSAGE_MODEL, callbacks);
    verify(imageBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    onClickListenerArgCaptor.getValue().get(IMAGE_MESSAGE_MODEL.getAction()).onClick(null);

    verify(fiamUIListener).onFiamTrigger();
  }

  @Test
  public void inflate_setsActionListenerToDismissFiamOnClick() throws Exception {
    resumeActivity(activity);
    fiamUI.setFiamListener(fiamUIListener);
    listener.displayMessage(MODAL_MESSAGE_MODEL, callbacks);
    verify(modalBindingWrapper)
        .inflate(onClickListenerArgCaptor.capture(), any(OnClickListener.class));
    Button button = modalBindingWrapper.getActionButton();

    assertThat(fiamUI.getCurrentInAppMessage()).isNotNull();
    assertThat(fiamUI.getCurrentInAppMessage()).isEqualTo(MODAL_MESSAGE_MODEL);
    when(windowManager.isFiamDisplayed()).thenReturn(true);
    button.performClick();
    // Assert that after the messageClick, we now have a null FIAM
    assertThat(fiamUI.getCurrentInAppMessage()).isNull();
    verify(windowManager, times(1)).destroy(any(Activity.class));
  }

  private void resumeActivity(Activity activity) {
    fiamUI.onActivityResumed(activity);
    verify(headless, atLeastOnce())
        .setMessageDisplayComponent(inAppMessageTriggerListenerCaptor.capture());
    assertThat(fiamUI.currentlyBoundActivityName.equals(activity.getLocalClassName())).isTrue();
    listener = inAppMessageTriggerListenerCaptor.getValue();
  }

  private void pauseActivity(Activity activity) {
    fiamUI.onActivityPaused(activity);
    assertThat(fiamUI.currentlyBoundActivityName).isNull();
  }

  static class TestActivity extends Activity {}

  static class TestSecondActivity extends Activity {}

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
