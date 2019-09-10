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

package com.google.firebase.inappmessaging.internal;

import static com.google.firebase.inappmessaging.EventType.CLICK_EVENT_TYPE;
import static com.google.firebase.inappmessaging.EventType.IMPRESSION_EVENT_TYPE;

import android.os.Bundle;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.inappmessaging.BuildConfig;
import com.google.firebase.inappmessaging.CampaignAnalytics;
import com.google.firebase.inappmessaging.ClientAppInfo;
import com.google.firebase.inappmessaging.DismissType;
import com.google.firebase.inappmessaging.EventType;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingDismissType;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason;
import com.google.firebase.inappmessaging.RenderErrorReason;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.CardMessage;
import com.google.firebase.inappmessaging.model.ImageOnlyMessage;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.ModalMessage;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Class to log actions to engagementMetrics
 *
 * @hide
 */
public class MetricsLoggerClient {
  private static final Map<InAppMessagingErrorReason, RenderErrorReason> errorTransform =
      new HashMap<>();
  private static final Map<InAppMessagingDismissType, DismissType> dismissTransform =
      new HashMap<>();

  static {
    errorTransform.put(
        InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR,
        RenderErrorReason.UNSPECIFIED_RENDER_ERROR);
    errorTransform.put(
        InAppMessagingErrorReason.IMAGE_FETCH_ERROR, RenderErrorReason.IMAGE_FETCH_ERROR);
    errorTransform.put(
        InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR, RenderErrorReason.IMAGE_DISPLAY_ERROR);
    errorTransform.put(
        InAppMessagingErrorReason.IMAGE_UNSUPPORTED_FORMAT,
        RenderErrorReason.IMAGE_UNSUPPORTED_FORMAT);
  }

  static {
    dismissTransform.put(InAppMessagingDismissType.AUTO, DismissType.AUTO);
    dismissTransform.put(InAppMessagingDismissType.CLICK, DismissType.CLICK);
    dismissTransform.put(InAppMessagingDismissType.SWIPE, DismissType.SWIPE);
    dismissTransform.put(
        InAppMessagingDismissType.UNKNOWN_DISMISS_TYPE, DismissType.UNKNOWN_DISMISS_TYPE);
  }

  private final EngagementMetricsLoggerInterface engagementMetricsLogger;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstanceId firebaseInstanceId;
  private final Clock clock;
  private final AnalyticsConnector analyticsConnector;
  private final DeveloperListenerManager developerListenerManager;

  public MetricsLoggerClient(
      EngagementMetricsLoggerInterface engagementMetricsLogger,
      AnalyticsConnector analyticsConnector,
      FirebaseApp firebaseApp,
      FirebaseInstanceId firebaseInstanceId,
      Clock clock,
      DeveloperListenerManager developerListenerManager) {
    this.engagementMetricsLogger = engagementMetricsLogger;
    this.analyticsConnector = analyticsConnector;
    this.firebaseApp = firebaseApp;
    this.firebaseInstanceId = firebaseInstanceId;
    this.clock = clock;
    this.developerListenerManager = developerListenerManager;
  }

  /** Log impression */
  public void logImpression(InAppMessage message) {
    if (!isTestCampaign(message)) {
      // If message is not a test message then log analytics
      engagementMetricsLogger.logEvent(
          createEventEntry(message, IMPRESSION_EVENT_TYPE).toByteArray());
      logEventAsync(
          message,
          AnalyticsConstants.ANALYTICS_IMPRESSION_EVENT,
          impressionCountsAsConversion(message));
    }
    // No matter what, always trigger developer callbacks
    developerListenerManager.impressionDetected(message);
  }

  /** Log click */
  public void logMessageClick(InAppMessage message, Action action) {
    if (!isTestCampaign(message)) {
      // If message is not a test message then log analytics
      engagementMetricsLogger.logEvent(createEventEntry(message, CLICK_EVENT_TYPE).toByteArray());
      logEventAsync(message, AnalyticsConstants.ANALYTICS_ACTION_EVENT, true);
    }
    // No matter what, always trigger developer callbacks
    developerListenerManager.messageClicked(message, action);
  }

  /** Log Rendering error */
  public void logRenderError(InAppMessage message, InAppMessagingErrorReason errorReason) {
    if (!isTestCampaign(message)) {
      // If message is not a test message then log analytics
      engagementMetricsLogger.logEvent(
          createRenderErrorEntry(message, errorTransform.get(errorReason)).toByteArray());
    }
    // No matter what, always trigger developer callbacks
    developerListenerManager.displayErrorEncountered(message, errorReason);
  }

  /** Log dismiss */
  public void logDismiss(InAppMessage message, InAppMessagingDismissType dismissType) {
    if (!isTestCampaign(message)) {
      // If message is not a test message then log analytics
      engagementMetricsLogger.logEvent(
          createDismissEntry(message, dismissTransform.get(dismissType)).toByteArray());
      logEventAsync(message, AnalyticsConstants.ANALYTICS_DISMISS_EVENT, false);
    }
  }

  private CampaignAnalytics createEventEntry(InAppMessage message, EventType eventType) {
    return createCampaignAnalyticsBuilder(message).setEventType(eventType).build();
  }

  private CampaignAnalytics createDismissEntry(InAppMessage message, DismissType dismissType) {
    return createCampaignAnalyticsBuilder(message).setDismissType(dismissType).build();
  }

  private CampaignAnalytics createRenderErrorEntry(InAppMessage message, RenderErrorReason reason) {
    return createCampaignAnalyticsBuilder(message).setRenderErrorReason(reason).build();
  }

  private CampaignAnalytics.Builder createCampaignAnalyticsBuilder(InAppMessage message) {
    ClientAppInfo clientAppInfo = createClientAppInfo();
    return CampaignAnalytics.newBuilder()
        .setFiamSdkVersion(BuildConfig.VERSION_NAME)
        .setProjectNumber(firebaseApp.getOptions().getGcmSenderId())
        .setCampaignId(message.getCampaignMetadata().getCampaignId())
        .setClientApp(clientAppInfo)
        .setClientTimestampMillis(clock.now());
  }

  private ClientAppInfo createClientAppInfo() {
    return ClientAppInfo.newBuilder()
        .setGoogleAppId(firebaseApp.getOptions().getApplicationId())
        .setFirebaseInstanceId(firebaseInstanceId.getId())
        .build();
  }

  /**
   * Asynchronously logs an event to analytics, If a conversion event should be tracked, we
   * additionally update the userProperty
   *
   * <p>Scion schedules a task to run on a worker thread within the client app to send the event.
   */
  private void logEventAsync(InAppMessage message, String event, boolean updateConversionTracking) {
    String campaignId = message.getCampaignMetadata().getCampaignId();
    String campaignName = message.getCampaignMetadata().getCampaignName();
    Bundle params = collectAnalyticsParams(campaignName, campaignId);

    Logging.logd("Sending event=" + event + " params=" + params);

    if (analyticsConnector != null) {
      analyticsConnector.logEvent(AnalyticsConstants.ORIGIN_FIAM, event, params);
      if (updateConversionTracking) {
        // Use USER_PROPERTY_FIREBASE_LAST_NOTIFICATION for conversion tracking, prefix the
        // campaignId with
        // "fiam:"

        analyticsConnector.setUserProperty(
            AnalyticsConstants.ORIGIN_FIAM,
            AnalyticsConstants.USER_PROPERTY_FIREBASE_LAST_NOTIFICATION,
            "fiam:" + campaignId);
      }
    } else {
      Logging.logw("Unable to log event: analytics library is missing");
    }
  }

  Bundle collectAnalyticsParams(String campaignName, String campaignId) {
    Bundle params = new Bundle();

    params.putString(AnalyticsConstants.PARAM_MESSAGE_ID, campaignId);
    params.putString(AnalyticsConstants.PARAM_MESSAGE_NAME, campaignName);

    try {
      // set message time to epoch seconds
      int epochSeconds = (int) (clock.now() / 1000);
      params.putInt(AnalyticsConstants.PARAM_MESSAGE_DEVICE_TIME, epochSeconds);
    } catch (NumberFormatException e) {
      Logging.logw("Error while parsing use_device_time in FIAM event: " + e.getMessage());
    }

    return params;
  }

  private boolean impressionCountsAsConversion(InAppMessage message) {
    switch (message.getMessageType()) {
      case CARD:
        {
          CardMessage m = (CardMessage) message;
          boolean hasNoPrimaryAction = !isValidAction(m.getPrimaryAction());
          boolean hasNoSecondaryAction = !isValidAction(m.getSecondaryAction());
          return hasNoPrimaryAction && hasNoSecondaryAction;
        }
      case MODAL:
        return !isValidAction(((ModalMessage) message).getAction());
      case BANNER:
        return !isValidAction(((BannerMessage) message).getAction());
      case IMAGE_ONLY:
        return !isValidAction(((ImageOnlyMessage) message).getAction());
      default:
        {
          Logging.loge("Unable to determine if impression should be counted as conversion.");
          return false;
        }
    }
  }

  private boolean isTestCampaign(InAppMessage message) {
    return message.getCampaignMetadata().getIsTestMessage();
  }

  private boolean isValidAction(@Nullable Action action) {
    return action != null && action.getActionUrl() != null && !action.getActionUrl().isEmpty();
  }

  /** Wrapper to assist unit testing usage */
  public interface EngagementMetricsLoggerInterface {
    void logEvent(byte[] bytes);
  }
}
