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

package com.google.firebase.inappmessaging;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.inappmessaging.internal.DataCollectionHelper;
import com.google.firebase.inappmessaging.internal.DeveloperListenerManager;
import com.google.firebase.inappmessaging.internal.DisplayCallbacksFactory;
import com.google.firebase.inappmessaging.internal.InAppMessageStreamManager;
import com.google.firebase.inappmessaging.internal.Logging;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.ProgrammaticTrigger;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import io.reactivex.Maybe;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/**
 * The entry point of the Firebase In App Messaging headless SDK.
 *
 * <p>Firebase In-App Messaging will automatically initialize, and start listening for events.
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
@FirebaseAppScope
public class FirebaseInAppMessaging {

  private final InAppMessageStreamManager inAppMessageStreamManager;
  private final DataCollectionHelper dataCollectionHelper;
  private final DisplayCallbacksFactory displayCallbacksFactory;
  private final DeveloperListenerManager developerListenerManager;
  private final ProgramaticContextualTriggers programaticContextualTriggers;

  private boolean areMessagesSuppressed;

  private Maybe<FirebaseInAppMessagingDisplay> listener = Maybe.empty();

  @VisibleForTesting
  @Inject
  FirebaseInAppMessaging(
      InAppMessageStreamManager inAppMessageStreamManager,
      @ProgrammaticTrigger ProgramaticContextualTriggers programaticContextualTriggers,
      DataCollectionHelper dataCollectionHelper,
      DisplayCallbacksFactory displayCallbacksFactory,
      DeveloperListenerManager developerListenerManager) {

    this.inAppMessageStreamManager = inAppMessageStreamManager;
    this.programaticContextualTriggers = programaticContextualTriggers;

    this.dataCollectionHelper = dataCollectionHelper;
    this.areMessagesSuppressed = false;
    this.displayCallbacksFactory = displayCallbacksFactory;
    Logging.logi(
        "Starting InAppMessaging runtime with Instance ID "
            + FirebaseInstanceId.getInstance().getId());
    this.developerListenerManager = developerListenerManager;
    initializeFiam();
  }

  /**
   * Get FirebaseInAppMessaging instance using the firebase app returned by {@link
   * FirebaseApp#getInstance()}
   *
   * @param
   * @return
   */
  @NonNull
  @Keep
  public static FirebaseInAppMessaging getInstance() {
    return FirebaseApp.getInstance().get(FirebaseInAppMessaging.class);
  }

  /**
   * Determine whether automatic data collection is enabled or not
   *
   * @return true if auto initialization is required
   */
  @Keep
  public boolean isAutomaticDataCollectionEnabled() {
    return dataCollectionHelper.isAutomaticDataCollectionEnabled();
  }

  /**
   * Enable or disable automatic data collection for Firebase In-App Messaging.
   *
   * <p>When enabled, generates a registration token on app startup if there is no valid one and
   * generates a new token when it is deleted (which prevents {@link
   * com.google.firebase.iid.FirebaseInstanceId#deleteInstanceId} from stopping the periodic sending
   * of data). This setting is persisted across app restarts and overrides the setting specified in
   * your manifest.
   *
   * <p>By default, auto-initialization is enabled. If you need to change the default, (for example,
   * because you want to prompt the user before generates/refreshes a registration token on app
   * startup), add to your application’s manifest:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_inapp_messaging_auto_init_enabled" android:value="false" />
   * }</pre>
   *
   * Note, this will require you to manually initialize Firebase In-App Messaging, via:
   *
   * <pre>{@code FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(true)}</pre>
   *
   * @param isAutomaticCollectionEnabled Whether isEnabled
   */
  @Keep
  public void setAutomaticDataCollectionEnabled(boolean isAutomaticCollectionEnabled) {
    dataCollectionHelper.setAutomaticDataCollectionEnabled(isAutomaticCollectionEnabled);
  }

  /**
   * Enable or disable suppression of Firebase In App Messaging messages
   *
   * <p>When enabled, no in app messages will be rendered until either you either disable
   * suppression, or the app restarts, as this state is not preserved over app restarts.
   *
   * <p>By default, messages are not suppressed.
   *
   * @param areMessagesSuppressed Whether messages should be suppressed
   */
  @Keep
  public void setMessagesSuppressed(@NonNull Boolean areMessagesSuppressed) {
    this.areMessagesSuppressed = areMessagesSuppressed;
  }

  /**
   * Determine whether messages are suppressed or not. This is honored by the UI sdk, which handles
   * rendering the in app message.
   *
   * @return true if messages should be suppressed
   */
  @Keep
  public boolean areMessagesSuppressed() {
    return areMessagesSuppressed;
  }

  private void initializeFiam() {
    Disposable unusedSubscription =
        inAppMessageStreamManager
            .createFirebaseInAppMessageStream()
            .subscribe(
                triggeredInAppMessage ->
                    listener
                        .doOnSuccess(
                            listener -> {
                              listener.displayMessage(
                                  triggeredInAppMessage.getInAppMessage(),
                                  displayCallbacksFactory.generateDisplayCallback(
                                      triggeredInAppMessage.getInAppMessage(),
                                      triggeredInAppMessage.getTriggeringEvent()));
                            })
                        .subscribe());
  }

  /*
   * Called to set a new message display component for FIAM SDK. This is the method used
   * by both the default FIAM display SDK or any app wanting to customize the message
   * display.
   */
  @Keep
  public void setMessageDisplayComponent(@NonNull FirebaseInAppMessagingDisplay messageDisplay) {
    Logging.logi("Setting display event listener");
    this.listener = Maybe.just(messageDisplay);
  }

  /**
   * Unregisters a listener to in app message display events.
   *
   * @hide
   */
  @Keep
  @KeepForSdk
  public void clearDisplayListener() {
    Logging.logi("Removing display event listener");
    this.listener = Maybe.empty();
  }

  /*
   * Adds/Removes the event listeners. These listeners are triggered after FIAM's internal metrics reporting, but regardless of success/failure of the FIAM-internal callbacks.
   */

  // executed on worker thread

  /**
   * Registers an impression listener with FIAM, which will be notified on every FIAM impression
   *
   * @param impressionListener
   */
  public void addImpressionListener(
      @NonNull FirebaseInAppMessagingImpressionListener impressionListener) {
    developerListenerManager.addImpressionListener(impressionListener);
  }

  /**
   * Registers a click listener with FIAM, which will be notified on every FIAM click
   *
   * @param clickListener
   */
  public void addClickListener(@NonNull FirebaseInAppMessagingClickListener clickListener) {
    developerListenerManager.addClickListener(clickListener);
  }

  /**
   * Registers a display error listener with FIAM, which will be notified on every FIAM display
   * error
   *
   * @param displayErrorListener
   */
  public void addDisplayErrorListener(
      @NonNull FirebaseInAppMessagingDisplayErrorListener displayErrorListener) {
    developerListenerManager.addDisplayErrorListener(displayErrorListener);
  }

  // Executed with provided executor
  /**
   * Registers an impression listener with FIAM, which will be notified on every FIAM impression,
   * and triggered on the provided executor
   *
   * @param impressionListener
   * @param executor
   */
  public void addImpressionListener(
      @NonNull FirebaseInAppMessagingImpressionListener impressionListener,
      @NonNull Executor executor) {
    developerListenerManager.addImpressionListener(impressionListener, executor);
  }

  /**
   * Registers a click listener with FIAM, which will be notified on every FIAM click, and triggered
   * on the provided executor
   *
   * @param clickListener
   * @param executor
   */
  public void addClickListener(
      @NonNull FirebaseInAppMessagingClickListener clickListener, @NonNull Executor executor) {
    developerListenerManager.addClickListener(clickListener, executor);
  }

  /**
   * Registers a display error listener with FIAM, which will be notified on every FIAM display
   * error, and triggered on the provided executor
   *
   * @param displayErrorListener
   * @param executor
   */
  public void addDisplayErrorListener(
      @NonNull FirebaseInAppMessagingDisplayErrorListener displayErrorListener,
      @NonNull Executor executor) {
    developerListenerManager.addDisplayErrorListener(displayErrorListener, executor);
  }

  // Removing individual listeners:
  /**
   * Unregisters an impression listener
   *
   * @param impressionListener
   */
  public void removeImpressionListener(
      @NonNull FirebaseInAppMessagingImpressionListener impressionListener) {
    developerListenerManager.removeImpressionListener(impressionListener);
  }

  /**
   * Unregisters a click listener
   *
   * @param clickListener
   */
  public void removeClickListener(@NonNull FirebaseInAppMessagingClickListener clickListener) {
    developerListenerManager.removeClickListener(clickListener);
  }

  /**
   * Unregisters a display error listener
   *
   * @param displayErrorListener
   */
  public void removeDisplayErrorListener(
      @NonNull FirebaseInAppMessagingDisplayErrorListener displayErrorListener) {
    developerListenerManager.removeDisplayErrorListener(displayErrorListener);
  }

  /**
   * Programmatically trigger a contextual trigger. This will display any eligible in-app messages
   * that are triggered by this event
   *
   * @param eventName
   * @hide // hiding until api is finalized
   */
  public void triggerEvent(String eventName) {
    programaticContextualTriggers.triggerEvent(eventName);
  }
}
