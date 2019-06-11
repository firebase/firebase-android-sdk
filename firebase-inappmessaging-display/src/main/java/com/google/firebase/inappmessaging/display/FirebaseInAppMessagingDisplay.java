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
import android.net.Uri;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.android.gms.common.annotation.KeepForSdk;
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
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import com.squareup.picasso.Callback;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * The entry point of the Firebase In App Messaging display SDK.
 *
 * <p>Firebase In-App Messaging Display will automatically initialize, start listening for events,
 * and display eligible in-app messages.
 *
 * <p>This feature uses a Firebase Instance ID token to:
 *
 * <ul>
 *   <li>identify the app instance
 *   <li>fetch messages from the Firebase backend
 *   <li>send usage metrics to the Firebase backend.
 * </ul>
 *
 * To delete the Instance ID and the data associated with it, see {@link
 * com.google.firebase.iid.FirebaseInstanceId#deleteInstanceId}.
 */
@Keep
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
  private com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplay
      firebaseInAppMessagingDisplay;

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
  @Keep
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
  @Keep
  @KeepForSdk
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
  @Keep
  @KeepForSdk
  public void setFiamListener(FiamListener listener) {
    this.fiamListener = listener;
  }

  /**
   * Clears fiam listener
   *
   * @hide
   */
  @Keep
  @KeepForSdk
  public void clearFiamListener() {
    this.fiamListener = null;
  }

  /**
   * Clears fiam listener
   *
   * @hide
   */
  @Keep
  @KeepForSdk
  @Override
  public void onActivityStarted(final Activity activity) {
    // Register FIAM listener with the headless sdk.
    firebaseInAppMessagingDisplay =
        new com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplay() {
          @Override
          public void displayMessage(InAppMessage iam, FirebaseInAppMessagingDisplayCallbacks cb) {
            // When we are in the middle of showing a message, we ignore other notifications these
            // messages will be fired when the corresponding events happen the next time.
            if (inAppMessage != null || headlessInAppMessaging.areMessagesSuppressed()) {
              Logging.logd("Active FIAM exists. Skipping trigger");
              return;
            }
            inAppMessage = iam;
            callbacks = cb;
            showActiveFiam(activity);
          }
        };

    headlessInAppMessaging.setMessageDisplayComponent(firebaseInAppMessagingDisplay);
  }

  /**
   * Clear fiam listener on activity paused
   *
   * @hide
   */
  @Keep
  @KeepForSdk
  @Override
  public void onActivityPaused(Activity activity) {
    super.onActivityPaused(activity);
    // clear all state scoped to activity and dismiss fiam
    headlessInAppMessaging.clearDisplayListener();
    imageLoader.cancelTag(activity.getClass());
    removeDisplayedFiam(activity);
  }
  /**
   * Clear fiam listener on activity resumed
   *
   * @hide
   */
  @Keep
  @KeepForSdk
  @Override
  public void onActivityResumed(Activity activity) {
    super.onActivityResumed(activity);
    if (inAppMessage != null) {
      showActiveFiam(activity);
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
    notifyFiamTrigger();

    InAppMessageLayoutConfig config =
        layoutConfigs
            .get(
                InflaterConfigModule.configFor(
                    inAppMessage.getMessageType(), getScreenOrientation(application)))
            .get();

    final BindingWrapper bindingWrapper;

    if (inAppMessage.getMessageType() == MessageType.MODAL) {
      bindingWrapper = bindingWrapperFactory.createModalBindingWrapper(config, inAppMessage);
    } else if (inAppMessage.getMessageType() == MessageType.BANNER) {
      bindingWrapper = bindingWrapperFactory.createBannerBindingWrapper(config, inAppMessage);
    } else {
      bindingWrapper = bindingWrapperFactory.createImageBindingWrapper(config, inAppMessage);
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

    // If the message has an action, but not an action url, we dismiss when the action
    // button is
    // clicked;
    final View.OnClickListener actionListener;
    if (inAppMessage.getAction() != null
        && !TextUtils.isEmpty(inAppMessage.getAction().getActionUrl())) {
      actionListener =
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              if (callbacks != null) {
                callbacks.messageClicked();
              }
              final CustomTabsIntent i = new CustomTabsIntent.Builder().setShowTitle(true).build();

              i.launchUrl(activity, Uri.parse(inAppMessage.getAction().getActionUrl()));
              notifyFiamClick();
              // Ensure that we remove the displayed FIAM, and ensure that on re-load, the message
              // isn't re-displayed
              removeDisplayedFiam(activity);
              inAppMessage = null;
              callbacks = null;
            }
          };
    } else {
      Logging.loge("No action url found for action.");
      actionListener = dismissListener;
    }

    final OnGlobalLayoutListener layoutListener =
        bindingWrapper.inflate(actionListener, dismissListener);
    if (layoutListener != null) {
      bindingWrapper.getImageView().getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    // Show fiam after image successfully loads
    loadNullableImage(
        activity,
        bindingWrapper,
        inAppMessage.getImageUrl(),
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
                          "Impression timer onFinish for: " + inAppMessage.getCampaignId());

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

            windowManager.show(bindingWrapper, activity);

            if (bindingWrapper.getConfig().animate()) {
              // Animate entry
              animator.slideIntoView(application, bindingWrapper.getRootView(), TOP);
            }
          }

          @Override
          public void onError() {
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

  private void loadNullableImage(
      Activity activity, BindingWrapper fiam, String imageUrl, Callback callback) {
    if (!TextUtils.isEmpty(imageUrl)) {
      imageLoader
          .load(imageUrl)
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
}
