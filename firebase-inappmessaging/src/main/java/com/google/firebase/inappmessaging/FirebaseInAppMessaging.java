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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.inappmessaging.internal.DataCollectionHelper;
import com.google.firebase.inappmessaging.internal.DeveloperListenerManager;
import com.google.firebase.inappmessaging.internal.DisplayCallbacksFactory;
import com.google.firebase.inappmessaging.internal.InAppMessageStreamManager;
import com.google.firebase.inappmessaging.internal.Logging;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.ProgrammaticTrigger;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.model.TriggeredInAppMessage;
import com.google.firebase.installations.FirebaseInstallationsApi;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/**
 * The entry point of the Firebase In App Messaging headless SDK.
 *
 * <p>Firebase In-App Messaging will automatically initialize, and start listening for events.
 *
 * <p>This feature uses a Firebase Installation ID token to:
 *
 * <ul>
 *   <li>identify the app instance
 *   <li>fetch messages from the Firebase backend
 *   <li>send usage metrics to the Firebase backend.
 * </ul>
 *
 * <p>To delete the Installation ID and the data associated with it, see {@link
 * FirebaseInstallationsApi#delete()}.
 */
@FirebaseAppScope
public class FirebaseInAppMessaging {

  private final InAppMessageStreamManager inAppMessageStreamManager;
  private final DataCollectionHelper dataCollectionHelper;
  private final DisplayCallbacksFactory displayCallbacksFactory;
  private final DeveloperListenerManager developerListenerManager;
  private final ProgramaticContextualTriggers programaticContextualTriggers;
  private final FirebaseInstallationsApi firebaseInstallations;

  private boolean areMessagesSuppressed;
  private FirebaseInAppMessagingDisplay fiamDisplay;
  @Lightweight private Executor lightWeightExecutor;

  @VisibleForTesting
  @Inject
  FirebaseInAppMessaging(
      InAppMessageStreamManager inAppMessageStreamManager,
      @ProgrammaticTrigger ProgramaticContextualTriggers programaticContextualTriggers,
      DataCollectionHelper dataCollectionHelper,
      FirebaseInstallationsApi firebaseInstallations,
      DisplayCallbacksFactory displayCallbacksFactory,
      DeveloperListenerManager developerListenerManager,
      @Lightweight Executor lightWeightExecutor) {
    this.inAppMessageStreamManager = inAppMessageStreamManager;
    this.programaticContextualTriggers = programaticContextualTriggers;
    this.dataCollectionHelper = dataCollectionHelper;
    this.firebaseInstallations = firebaseInstallations;
    this.areMessagesSuppressed = false;
    this.displayCallbacksFactory = displayCallbacksFactory;
    this.developerListenerManager = developerListenerManager;
    this.lightWeightExecutor = lightWeightExecutor;

    firebaseInstallations
        .getId()
        .addOnSuccessListener(
            lightWeightExecutor,
            id -> {
              Logging.logi("Starting InAppMessaging runtime with Installation ID " + id);
            });

    Disposable unused =
        inAppMessageStreamManager
            .createFirebaseInAppMessageStream()
            .subscribe(FirebaseInAppMessaging.this::triggerInAppMessage);
  }

  /**
   * Gets FirebaseInAppMessaging instance using the firebase app returned by {@link
   * FirebaseApp#getInstance()}
   */
  @NonNull
  public static FirebaseInAppMessaging getInstance() {
    return FirebaseApp.getInstance().get(FirebaseInAppMessaging.class);
  }

  /**
   * Determines whether automatic data collection is enabled or not.
   *
   * @return true if auto initialization is required
   */
  public boolean isAutomaticDataCollectionEnabled() {
    return dataCollectionHelper.isAutomaticDataCollectionEnabled();
  }

  /**
   * Enables, disables, or clears automatic data collection for Firebase In-App Messaging.
   *
   * <p>When enabled, generates a registration token on app startup if there is no valid one and
   * generates a new token when it is deleted (which prevents {@link
   * FirebaseInstallationsApi#delete} from stopping the periodic sending of data). This setting is
   * persisted across app restarts and overrides the setting specified in your manifest.
   *
   * <p>When null, the enablement of the auto-initialization depends on the manifest and then on the
   * global enablement setting in this order. If none of these settings are present then it is
   * enabled by default.
   *
   * <p>If you need to change the default, (for example, because you want to prompt the user before
   * generates/refreshes a registration token on app startup), add the following to your
   * application’s manifest:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_inapp_messaging_auto_init_enabled" android:value="false" />
   * }</pre>
   *
   * <p>Note, this will require you to manually initialize Firebase In-App Messaging, via:
   *
   * <pre>{@code FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(true)}</pre>
   *
   * <p>Manual initialization will also be required in order to clear these settings and fall back
   * on other settings, via:
   *
   * <pre>{@code FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(null)}</pre>
   *
   * @param isAutomaticCollectionEnabled Whether isEnabled
   */
  public void setAutomaticDataCollectionEnabled(@Nullable Boolean isAutomaticCollectionEnabled) {
    dataCollectionHelper.setAutomaticDataCollectionEnabled(isAutomaticCollectionEnabled);
  }

  /**
   * Enables, disables, or clears automatic data collection for Firebase In-App Messaging.
   *
   * <p>When enabled, generates a registration token on app startup if there is no valid one and
   * generates a new token when it is deleted (which prevents {@link
   * FirebaseInstallationsApi#delete} from stopping the periodic sending of data). This setting is
   * persisted across app restarts and overrides the setting specified in your manifest.
   *
   * <p>By default, auto-initialization is enabled. If you need to change the default, (for example,
   * because you want to prompt the user before generates/refreshes a registration token on app
   * startup), add to your application’s manifest:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_inapp_messaging_auto_init_enabled" android:value="false" />
   * }</pre>
   *
   * <p>Note, this will require you to manually initialize Firebase In-App Messaging, via:
   *
   * <pre>{@code FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(true)}</pre>
   *
   * @param isAutomaticCollectionEnabled Whether isEnabled
   */
  public void setAutomaticDataCollectionEnabled(boolean isAutomaticCollectionEnabled) {
    dataCollectionHelper.setAutomaticDataCollectionEnabled(isAutomaticCollectionEnabled);
  }

  /**
   * Enables or disables suppression of Firebase In App Messaging messages.
   *
   * <p>When enabled, no in app messages will be rendered until either you either disable
   * suppression, or the app restarts, as this state is not preserved over app restarts.
   *
   * <p>By default, messages are not suppressed.
   *
   * @param areMessagesSuppressed Whether messages should be suppressed
   */
  public void setMessagesSuppressed(@NonNull Boolean areMessagesSuppressed) {
    this.areMessagesSuppressed = areMessagesSuppressed;
  }

  /**
   * Determines whether messages are suppressed or not. This is honored by the UI sdk, which handles
   * rendering the in app message.
   *
   * @return true if messages should be suppressed
   */
  public boolean areMessagesSuppressed() {
    return areMessagesSuppressed;
  }

  /**
   * Sets message display component for FIAM SDK. This is the method used by both the default FIAM
   * display SDK or any app wanting to customize the message display.
   */
  public void setMessageDisplayComponent(@NonNull FirebaseInAppMessagingDisplay messageDisplay) {
    Logging.logi("Setting display event component");
    this.fiamDisplay = messageDisplay;
  }

  /**
   * Unregisters a fiamDisplay to in app message display events.
   *
   * @hide
   */
  public void clearDisplayListener() {
    Logging.logi("Removing display event component");
    this.fiamDisplay = null;
  }

  /**
   * Registers an impression listener with FIAM, which will be notified on every FIAM impression.
   *
   * @param impressionListener
   */
  public void addImpressionListener(
      @NonNull FirebaseInAppMessagingImpressionListener impressionListener) {
    developerListenerManager.addImpressionListener(impressionListener);
  }

  /**
   * Registers a click listener with FIAM, which will be notified on every FIAM click.
   *
   * @param clickListener
   */
  public void addClickListener(@NonNull FirebaseInAppMessagingClickListener clickListener) {
    developerListenerManager.addClickListener(clickListener);
  }

  /**
   * Registers a dismiss listener with FIAM, which will be notified on every FIAM dismiss.
   *
   * @param dismissListener
   */
  public void addDismissListener(@NonNull FirebaseInAppMessagingDismissListener dismissListener) {
    developerListenerManager.addDismissListener(dismissListener);
  }

  /**
   * Registers a display error listener with FIAM, which will be notified on every FIAM display
   * error.
   *
   * @param displayErrorListener
   */
  public void addDisplayErrorListener(
      @NonNull FirebaseInAppMessagingDisplayErrorListener displayErrorListener) {
    developerListenerManager.addDisplayErrorListener(displayErrorListener);
  }

  /**
   * Registers an impression listener with FIAM, which will be notified on every FIAM impression,
   * and triggered on the provided executor.
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
   * on the provided executor.
   *
   * @param clickListener
   * @param executor
   */
  public void addClickListener(
      @NonNull FirebaseInAppMessagingClickListener clickListener, @NonNull Executor executor) {
    developerListenerManager.addClickListener(clickListener, executor);
  }

  /**
   * Registers a dismiss listener with FIAM, which will be notified on every FIAM dismiss, and
   * triggered on the provided executor.
   *
   * @param dismissListener
   * @param executor
   */
  public void addDismissListener(
      @NonNull FirebaseInAppMessagingDismissListener dismissListener, @NonNull Executor executor) {
    developerListenerManager.addDismissListener(dismissListener, executor);
  }

  /**
   * Registers a display error listener with FIAM, which will be notified on every FIAM display
   * error, and triggered on the provided executor.
   *
   * @param displayErrorListener
   * @param executor
   */
  public void addDisplayErrorListener(
      @NonNull FirebaseInAppMessagingDisplayErrorListener displayErrorListener,
      @NonNull Executor executor) {
    developerListenerManager.addDisplayErrorListener(displayErrorListener, executor);
  }

  /**
   * Unregisters an impression listener.
   *
   * @param impressionListener
   */
  public void removeImpressionListener(
      @NonNull FirebaseInAppMessagingImpressionListener impressionListener) {
    developerListenerManager.removeImpressionListener(impressionListener);
  }

  /**
   * Unregisters a click listener.
   *
   * @param clickListener
   */
  public void removeClickListener(@NonNull FirebaseInAppMessagingClickListener clickListener) {
    developerListenerManager.removeClickListener(clickListener);
  }

  /**
   * Unregisters a display error listener.
   *
   * @param displayErrorListener
   */
  public void removeDisplayErrorListener(
      @NonNull FirebaseInAppMessagingDisplayErrorListener displayErrorListener) {
    developerListenerManager.removeDisplayErrorListener(displayErrorListener);
  }

  /**
   * Unregisters a dismiss listener.
   *
   * @param dismissListener the listener callback to be removed which was added using {@link
   *     #addDismissListener}
   */
  public void removeDismissListener(
      @NonNull FirebaseInAppMessagingDismissListener dismissListener) {
    developerListenerManager.removeDismissListener(dismissListener);
  }

  /**
   * Removes all registered listeners.
   *
   * @hide
   */
  public void removeAllListeners() {
    developerListenerManager.removeAllListeners();
  }

  /**
   * Programmatically triggers a contextual trigger. This will display any eligible in-app messages
   * that are triggered by this event.
   *
   * @param eventName
   */
  public void triggerEvent(@NonNull String eventName) {
    programaticContextualTriggers.triggerEvent(eventName);
  }

  private void triggerInAppMessage(TriggeredInAppMessage inAppMessage) {
    if (this.fiamDisplay != null) {
      // The APIs that control the UI are going to be called on the main thread. Yay!
      fiamDisplay.displayMessage(
          inAppMessage.getInAppMessage(),
          displayCallbacksFactory.generateDisplayCallback(
              inAppMessage.getInAppMessage(), inAppMessage.getTriggeringEvent()));
    }
  }
}
