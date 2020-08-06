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

import static com.google.firebase.inappmessaging.display.internal.FiamAnimator.Position.TOP;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingDismissType;
import com.google.firebase.inappmessaging.display.internal.BindingWrapperFactory;
import com.google.firebase.inappmessaging.display.internal.FiamAnimator;
import com.google.firebase.inappmessaging.display.internal.FiamImageLoader;
import com.google.firebase.inappmessaging.display.internal.FiamWindowManager;
import com.google.firebase.inappmessaging.display.internal.FirebaseInAppMessagingDisplayImpl;
import com.google.firebase.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.google.firebase.inappmessaging.display.internal.Logging;
import com.google.firebase.inappmessaging.display.internal.RenewableTimer;
import com.google.firebase.inappmessaging.display.internal.bindingwrappers.BindingWrapper;
import com.google.firebase.inappmessaging.display.internal.injection.modules.InflaterConfigModule;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.CardMessage;
import com.google.firebase.inappmessaging.model.ImageData;
import com.google.firebase.inappmessaging.model.ImageOnlyMessage;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import com.google.firebase.inappmessaging.model.ModalMessage;
import com.squareup.picasso.Callback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * The entry point of the Firebase In App Messaging display SDK.
 *
 * <p>Firebase In-App Messaging Display will automatically initialize, start listening for events,
 * and display eligible in-app messages.
 *
 * <p>This feature uses a Firebase Installation ID token to:
 *
 * <ul>
 *   <li>identify the app instance
 *   <li>fetch messages from the Firebase backend
 *   <li>send usage metrics to the Firebase backend.
 * </ul>
 */
@FirebaseAppScope
public class FirebaseInAppMessagingDisplay extends FirebaseInAppMessagingDisplayImpl {
  static final long IMPRESSION_THRESHOLD_MILLIS = 5 * 1000; // 5 seconds is a valid impression
  static final long DISMISS_THRESHOLD_MILLIS =
      20 * 1000; // auto dismiss after 20 seconds for banner
  static final long INTERVAL_MILLIS = 1000;

  private final FirebaseInAppMessaging headlessInAppMessaging;

  private final Map<String, Provider<InAppMessageLayoutConfig>> layoutConfigs;
  private final FiamImageLoader imageLoader;
  private final RenewableTimer impressionTimer;
  private final RenewableTimer autoDismissTimer;
  private final FiamWindowManager windowManager;
  private final BindingWrapperFactory bindingWrapperFactory;
  private final Application application;
  private final FiamAnimator animator;

  private FiamListener fiamListener;
  private InAppMessage inAppMessage;
  private FirebaseInAppMessagingDisplayCallbacks callbacks;

  @VisibleForTesting @Nullable String currentlyBoundActivityName;

  @Inject
  FirebaseInAppMessagingDisplay(
      FirebaseInAppMessaging headlessInAppMessaging,
      Map<String, Provider<InAppMessageLayoutConfig>> layoutConfigs,
      FiamImageLoader imageLoader,
      RenewableTimer impressionTimer,
      RenewableTimer autoDismissTimer,
      FiamWindowManager windowManager,
      Application application,
      BindingWrapperFactory bindingWrapperFactory,
      FiamAnimator animator) {
    super();
    this.headlessInAppMessaging = headlessInAppMessaging;
    this.layoutConfigs = layoutConfigs;
    this.imageLoader = imageLoader;
    this.impressionTimer = impressionTimer;
    this.autoDismissTimer = autoDismissTimer;
    this.windowManager = windowManager;
    this.application = application;
    this.bindingWrapperFactory = bindingWrapperFactory;
    this.animator = animator;
  }

  /**
   * Get FirebaseInAppMessagingDisplay instance using the default firebase app, returned by {@link
   * FirebaseApp#getInstance()}
   */
  @NonNull
  public static FirebaseInAppMessagingDisplay getInstance() {
    return FirebaseApp.getInstance().get(FirebaseInAppMessagingDisplay.class);
  }

  private static int getScreenOrientation(Application app) {
    return app.getResources().getConfiguration().orientation;
  }

  /**
   * Method that can be used to test the appearance of an in app message
   *
   * @hide
   */
  public void testMessage(
      Activity activity,
      InAppMessage inAppMessage,
      FirebaseInAppMessagingDisplayCallbacks callbacks) {
    this.inAppMessage = inAppMessage;
    this.callbacks = callbacks;
    showActiveFiam(activity);
  }

  /**
   * Sets fiam listener to receive in app message callbacks
   *
   * @hide
   */
  public void setFiamListener(FiamListener listener) {
    this.fiamListener = listener;
  }

  /**
   * Clears fiam listener
   *
   * @hide
   */
  public void clearFiamListener() {
    this.fiamListener = null;
  }

  /**
   * Bind FIAM listener on Activity resume.
   *
   * @hide
   */
  @Override
  public void onActivityResumed(Activity activity) {
    super.onActivityResumed(activity);
    bindFiamToActivity(activity);
  }

  /**
   * Clear FIAM listener on activity paused
   *
   * @hide
   */
  @Override
  public void onActivityPaused(Activity activity) {
    unbindFiamFromActivity(activity);
    headlessInAppMessaging.removeAllListeners();
    super.onActivityPaused(activity);
  }

  private void bindFiamToActivity(Activity activity) {
    // If we have no currently bound activity or are currently bound to a different activity then
    // bind to this new activity.
    if (currentlyBoundActivityName == null
        || !currentlyBoundActivityName.equals(activity.getLocalClassName())) {
      Logging.logi("Binding to activity: " + activity.getLocalClassName());
      headlessInAppMessaging.setMessageDisplayComponent(
          (iam, cb) -> {
            // When we are in the middle of showing a message, we ignore other notifications these
            // messages will be fired when the corresponding events happen the next time.
            if (inAppMessage != null || headlessInAppMessaging.areMessagesSuppressed()) {
              Logging.logd("Active FIAM exists. Skipping trigger");
              return;
            }
            inAppMessage = iam;
            callbacks = cb;
            showActiveFiam(activity);
          });
      // set the current activity to be the one passed in so that we know not to bind again to the
      // same activity
      currentlyBoundActivityName = activity.getLocalClassName();
    }
    if (inAppMessage != null) {
      showActiveFiam(activity);
    }
  }

  private void unbindFiamFromActivity(Activity activity) {
    // If we are attempting to unbind from an activity, first check to see that we are currently
    // bound to it
    if (currentlyBoundActivityName != null
        && currentlyBoundActivityName.equals(activity.getLocalClassName())) {
      Logging.logi("Unbinding from activity: " + activity.getLocalClassName());
      headlessInAppMessaging.clearDisplayListener();
      imageLoader.cancelTag(activity.getClass());
      removeDisplayedFiam(activity);
      currentlyBoundActivityName = null;
    }
  }

  // The current FIAM might be null
  @VisibleForTesting
  InAppMessage getCurrentInAppMessage() {
    return inAppMessage;
  }

  private void showActiveFiam(@NonNull final Activity activity) {
    if (inAppMessage == null || headlessInAppMessaging.areMessagesSuppressed()) {
      Logging.loge("No active message found to render");
      return;
    }

    if (inAppMessage.getMessageType().equals(MessageType.UNSUPPORTED)) {
      Logging.loge("The message being triggered is not supported by this version of the sdk.");
      return;
    }
    notifyFiamTrigger();

    InAppMessageLayoutConfig config =
        layoutConfigs
            .get(
                InflaterConfigModule.configFor(
                    inAppMessage.getMessageType(), getScreenOrientation(application)))
            .get();

    final BindingWrapper bindingWrapper;

    switch (inAppMessage.getMessageType()) {
      case BANNER:
        bindingWrapper = bindingWrapperFactory.createBannerBindingWrapper(config, inAppMessage);
        break;
      case MODAL:
        bindingWrapper = bindingWrapperFactory.createModalBindingWrapper(config, inAppMessage);
        break;
      case IMAGE_ONLY:
        bindingWrapper = bindingWrapperFactory.createImageBindingWrapper(config, inAppMessage);
        break;
      case CARD:
        bindingWrapper = bindingWrapperFactory.createCardBindingWrapper(config, inAppMessage);
        break;
      default:
        Logging.loge("No bindings found for this message type");
        // so we should break out completely and not attempt to show anything
        return;
    }

    // The WindowManager LayoutParams.TYPE_APPLICATION_PANEL requires tokens from the activity
    // which does not become available until after all lifecycle methods are complete.
    activity
        .findViewById(android.R.id.content)
        .post(
            new Runnable() {
              @Override
              public void run() {
                inflateBinding(activity, bindingWrapper);
              }
            });
  }

  // Since we handle only touch outside events and let the underlying views handle all other events,
  // it is safe to ignore this warning
  @SuppressLint("ClickableViewAccessibility")
  private void inflateBinding(final Activity activity, final BindingWrapper bindingWrapper) {
    // On click listener when X button or collapse button is clicked
    final View.OnClickListener dismissListener =
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (callbacks != null) {
              callbacks.messageDismissed(InAppMessagingDismissType.CLICK);
            }
            dismissFiam(activity);
          }
        };

    Map<Action, View.OnClickListener> actionListeners = new HashMap<>();
    for (Action action : extractActions(inAppMessage)) {
      final View.OnClickListener actionListener;
      // TODO: need an onclick listener per action
      // If the message has an action and an action url, set up an intent to handle the url
      if (action != null && !TextUtils.isEmpty(action.getActionUrl())) {
        actionListener =
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                if (callbacks != null) {
                  Logging.logi("Calling callback for click action");
                  callbacks.messageClicked(action);
                }
                launchUriIntent(activity, Uri.parse(action.getActionUrl()));
                notifyFiamClick();
                // Ensure that we remove the displayed FIAM, and ensure that on re-load, the message
                // isn't re-displayed
                removeDisplayedFiam(activity);
                inAppMessage = null;
                callbacks = null;
              }
            };
      } else {
        Logging.logi("No action url found for action. Treating as dismiss.");
        actionListener = dismissListener;
      }
      actionListeners.put(action, actionListener);
    }

    final OnGlobalLayoutListener layoutListener =
        bindingWrapper.inflate(actionListeners, dismissListener);
    if (layoutListener != null) {
      bindingWrapper.getImageView().getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    // Show fiam after image successfully loads
    loadNullableImage(
        activity,
        bindingWrapper,
        extractImageData(inAppMessage),
        new Callback() {
          @Override
          public void onSuccess() {
            // Setup dismiss on touch outside
            if (!bindingWrapper.getConfig().backgroundEnabled()) {
              bindingWrapper
                  .getRootView()
                  .setOnTouchListener(
                      new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                          if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                            if (callbacks != null) {
                              callbacks.messageDismissed(
                                  InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE);
                            }
                            dismissFiam(activity);
                            return true;
                          }
                          return false;
                        }
                      });
            }

            // Setup impression timer
            impressionTimer.start(
                new RenewableTimer.Callback() {
                  @Override
                  public void onFinish() {
                    if (inAppMessage != null && callbacks != null) {
                      Logging.logi(
                          "Impression timer onFinish for: "
                              + inAppMessage.getCampaignMetadata().getCampaignId());

                      callbacks.impressionDetected();
                    }
                  }
                },
                IMPRESSION_THRESHOLD_MILLIS,
                INTERVAL_MILLIS);

            // Setup auto dismiss timer
            if (bindingWrapper.getConfig().autoDismiss()) {
              autoDismissTimer.start(
                  new RenewableTimer.Callback() {
                    @Override
                    public void onFinish() {
                      if (inAppMessage != null && callbacks != null) {
                        callbacks.messageDismissed(InAppMessagingDismissType.AUTO);
                      }

                      dismissFiam(activity);
                    }
                  },
                  DISMISS_THRESHOLD_MILLIS,
                  INTERVAL_MILLIS);
            }

            activity.runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    windowManager.show(bindingWrapper, activity);
                    if (bindingWrapper.getConfig().animate()) {
                      // Animate entry
                      animator.slideIntoView(application, bindingWrapper.getRootView(), TOP);
                    }
                  }
                });
          }

          @Override
          public void onError(Exception e) {
            Logging.loge("Image download failure ");
            if (layoutListener != null) {
              bindingWrapper
                  .getImageView()
                  .getViewTreeObserver()
                  .removeGlobalOnLayoutListener(layoutListener);
            }
            cancelTimers(); // Not strictly necessary.
            inAppMessage = null;
            callbacks = null;
          }
        });
  }

  private List<Action> extractActions(InAppMessage message) {
    List<Action> actions = new ArrayList<>();
    switch (message.getMessageType()) {
      case BANNER:
        actions.add(((BannerMessage) message).getAction());
        break;
      case CARD:
        actions.add(((CardMessage) message).getPrimaryAction());
        actions.add(((CardMessage) message).getSecondaryAction());
        break;
      case IMAGE_ONLY:
        actions.add(((ImageOnlyMessage) message).getAction());
        break;
      case MODAL:
        actions.add(((ModalMessage) message).getAction());
        break;
      default:
        // An empty action is treated like a dismiss
        actions.add(Action.builder().build());
    }
    return actions;
  }

  // TODO: Factor this into the InAppMessage API.
  private ImageData extractImageData(InAppMessage message) {
    // Handle getting image data for card type
    if (message.getMessageType() == MessageType.CARD) {
      ImageData portraitImageData = ((CardMessage) message).getPortraitImageData();
      ImageData landscapeImageData = ((CardMessage) message).getLandscapeImageData();

      // If we're in portrait try to use portrait image data, fallback to landscape
      if (getScreenOrientation(application) == Configuration.ORIENTATION_PORTRAIT) {
        return isValidImageData(portraitImageData) ? portraitImageData : landscapeImageData;
      }
      // If we're in landscape try to use landscape image data, fallback to portrait
      return isValidImageData(landscapeImageData) ? landscapeImageData : portraitImageData;
    }
    // For now this is how we get all other fiam types image data.
    return message.getImageData();
  }

  // TODO: Factor this into the InAppMessage API
  private boolean isValidImageData(@Nullable ImageData imageData) {
    return imageData != null && !TextUtils.isEmpty(imageData.getImageUrl());
  }

  private void loadNullableImage(
      Activity activity, BindingWrapper fiam, ImageData imageData, Callback callback) {
    if (isValidImageData(imageData)) {
      imageLoader
          .load(imageData.getImageUrl())
          .tag(activity.getClass())
          .placeholder(R.drawable.image_placeholder)
          .into(fiam.getImageView(), callback);
    } else {
      callback.onSuccess();
    }
  }

  // This action needs to be idempotent since multiple callbacks compete to dismiss.
  // For example, a swipe and a click on the banner compete.
  private void dismissFiam(Activity activity) {
    Logging.logd("Dismissing fiam");
    notifyFiamDismiss();
    removeDisplayedFiam(activity);
    inAppMessage = null;
    callbacks = null;
  }

  private void removeDisplayedFiam(Activity activity) {
    if (windowManager.isFiamDisplayed()) {
      windowManager.destroy(activity);
      cancelTimers();
    }
  }

  private void cancelTimers() {
    impressionTimer.cancel();
    autoDismissTimer.cancel();
  }

  private void notifyFiamTrigger() {
    if (fiamListener != null) {
      fiamListener.onFiamTrigger();
    }
  }

  private void notifyFiamClick() {
    if (fiamListener != null) {
      fiamListener.onFiamClick();
    }
  }

  private void notifyFiamDismiss() {
    if (fiamListener != null) {
      fiamListener.onFiamDismiss();
    }
  }

  private void launchUriIntent(Activity activity, Uri uri) {
    if (supportsCustomTabs(activity)) {
      // If we can launch a chrome view, try that.
      CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
      Intent intent = customTabsIntent.intent;
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      customTabsIntent.launchUrl(activity, uri);
    } else {
      // If we can't launch a chrome view try to launch anything that can handle a URL.
      Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
      ResolveInfo info = activity.getPackageManager().resolveActivity(browserIntent, 0);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (info != null) {
        activity.startActivity(browserIntent);
      } else {
        // If the device can't resolve a url then log, but don't crash.
        Logging.loge("Device cannot resolve intent for: " + Intent.ACTION_VIEW);
      }
    }
  }

  private boolean supportsCustomTabs(Activity activity) {
    Intent customTabIntent = new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    List<ResolveInfo> resolveInfos =
        activity.getPackageManager().queryIntentServices(customTabIntent, 0);
    return !resolveInfos.isEmpty();
  }
}
