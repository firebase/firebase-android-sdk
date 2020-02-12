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

import android.text.TextUtils;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.inappmessaging.model.Action;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.RateLimit;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;

public class DisplayCallbacksImpl implements FirebaseInAppMessagingDisplayCallbacks {

  private final ImpressionStorageClient impressionStorageClient;
  private final Clock clock;
  private final Schedulers schedulers;
  private final RateLimiterClient rateLimiterClient;
  private final CampaignCacheClient campaignCacheClient;
  private final RateLimit appForegroundRateLimit;
  private final MetricsLoggerClient metricsLoggerClient;
  private final DataCollectionHelper dataCollectionHelper;
  private final InAppMessage inAppMessage;
  private final String triggeringEvent;

  private static boolean wasImpressed;
  private static final String MESSAGE_CLICK = "message click to metrics logger";

  @VisibleForTesting
  DisplayCallbacksImpl(
      ImpressionStorageClient impressionStorageClient,
      Clock clock,
      Schedulers schedulers,
      RateLimiterClient rateLimiterClient,
      CampaignCacheClient campaignCacheClient,
      RateLimit appForegroundRateLimit,
      MetricsLoggerClient metricsLoggerClient,
      DataCollectionHelper dataCollectionHelper,
      InAppMessage inAppMessage,
      String triggeringEvent) {
    this.impressionStorageClient = impressionStorageClient;
    this.clock = clock;
    this.schedulers = schedulers;
    this.rateLimiterClient = rateLimiterClient;
    this.campaignCacheClient = campaignCacheClient;
    this.appForegroundRateLimit = appForegroundRateLimit;
    this.metricsLoggerClient = metricsLoggerClient;
    this.dataCollectionHelper = dataCollectionHelper;
    this.inAppMessage = inAppMessage;
    this.triggeringEvent = triggeringEvent;

    // just to be explicit
    wasImpressed = false;
  }

  @Override
  public Task<Void> impressionDetected() {

    // In the future, when more logAction events are supported, it might be worth
    // extracting this logic into a manager similar to InAppMessageStreamManager
    String MESSAGE_IMPRESSION = "message impression to metrics logger";

    if (shouldLog() && !wasImpressed) {
      Logging.logd("Attempting to record: " + MESSAGE_IMPRESSION);

      Completable logImpressionToMetricsLogger =
          Completable.fromAction(() -> metricsLoggerClient.logImpression(inAppMessage));

      Completable logImpressionCompletable =
          logToImpressionStore()
              .andThen(logImpressionToMetricsLogger)
              .andThen(updateWasImpressed());

      return maybeToTask(logImpressionCompletable.toMaybe(), schedulers.io());
    }
    logActionNotTaken(MESSAGE_IMPRESSION);
    return new TaskCompletionSource<Void>().getTask();
  }

  private Completable updateWasImpressed() {
    return Completable.fromAction(() -> wasImpressed = true);
  }

  @Override
  public Task<Void> messageDismissed(InAppMessagingDismissType dismissType) {

    /**
     * NOTE: While the api is passing us the campaign id via the FIAM, we pull the campaignId from
     * the cache to ensure that we're only logging events for campaigns that we've fetched - to
     * avoid implicitly trusting an id that is provided through the app
     */
    String MESSAGE_DISMISSAL = "message dismissal to metrics logger";
    if (shouldLog()) {
      Logging.logd("Attempting to record: " + MESSAGE_DISMISSAL);
      Completable completable =
          Completable.fromAction(() -> metricsLoggerClient.logDismiss(inAppMessage, dismissType));

      return logImpressionIfNeeded(completable);
    }
    logActionNotTaken(MESSAGE_DISMISSAL);
    return new TaskCompletionSource<Void>().getTask();
  }

  @Deprecated
  public Task<Void> messageClicked() {
    return messageClicked(inAppMessage.getAction());
  }

  @Override
  public Task<Void> messageClicked(Action action) {

    /**
     * NOTE: While the api is passing us the campaign id via the FIAM, we pul the campaignId from
     * the cache to ensure that we're only logging events for campaigns that we've fetched - to
     * avoid implicitly trusting an id that is provided through the app
     */
    if (shouldLog()) {
      if (action.getActionUrl() == null) {
        return messageDismissed(InAppMessagingDismissType.CLICK);
      }
      return logMessageClick(action);
    }
    logActionNotTaken(MESSAGE_CLICK);
    return new TaskCompletionSource<Void>().getTask();
  }

  private Task<Void> logMessageClick(Action action) {

    Logging.logd("Attempting to record: " + MESSAGE_CLICK);
    Completable completable =
        Completable.fromAction(() -> metricsLoggerClient.logMessageClick(inAppMessage, action));

    return logImpressionIfNeeded(completable);
  }

  private boolean actionMatches(Action messageAction, Action actionTaken) {
    if (messageAction == null) {
      return actionTaken == null || TextUtils.isEmpty(actionTaken.getActionUrl());
    } else {
      return messageAction.getActionUrl().equals(actionTaken.getActionUrl());
    }
  }

  @Override
  public Task<Void> displayErrorEncountered(InAppMessagingErrorReason errorReason) {
    /**
     * NOTE: While the api is passing us the campaign id via the FIAM, we pull the campaignId from
     * the cache to ensure that we're only logging events for campaigns that we've fetched - to
     * avoid implicitly trusting an id that is provided through the app
     */
    String RENDER_ERROR = "render error to metrics logger";
    if (shouldLog()) {
      Logging.logd("Attempting to record: " + RENDER_ERROR);

      Completable completable =
          Completable.fromAction(
              () -> metricsLoggerClient.logRenderError(inAppMessage, errorReason));

      return maybeToTask(
          logToImpressionStore().andThen(completable).andThen(updateWasImpressed()).toMaybe(),
          schedulers.io());
    }
    logActionNotTaken(RENDER_ERROR);
    return new TaskCompletionSource<Void>().getTask();
  }

  /** We should log if data collection is enabled and the message is not a test message. */
  private boolean shouldLog() {
    return dataCollectionHelper.isAutomaticDataCollectionEnabled();
  }

  private Task<Void> logImpressionIfNeeded(Completable actionToTake) {
    if (!wasImpressed) {
      impressionDetected();
    }

    return maybeToTask(actionToTake.toMaybe(), schedulers.io());
  }

  /**
   * Logging to clarify why an action was not taken. For example why an impression was not logged.
   * TODO: Refactor this to be a function wrapper.
   *
   * @hide
   */
  private void logActionNotTaken(String action, Maybe<String> reason) {
    // If provided a reason then use that.
    if (reason != null) {
      Logging.logd(String.format("Not recording: %s. Reason: %s", action, reason));
    }
    // If a reason is not provided then check for a test message.
    else if (inAppMessage.getCampaignMetadata().getIsTestMessage()) {
      Logging.logd(String.format("Not recording: %s. Reason: Message is test message", action));
    }
    // If no reason and not a test message check for data collection being disabled.
    else if (!dataCollectionHelper.isAutomaticDataCollectionEnabled()) {
      Logging.logd(String.format("Not recording: %s. Reason: Data collection is disabled", action));
    }
    // This should never happen.
    else Logging.logd(String.format("Not recording: %s", action));
  }

  private void logActionNotTaken(String action) {
    logActionNotTaken(action, null);
  }

  private Completable logToImpressionStore() {
    String campaignId = inAppMessage.getCampaignMetadata().getCampaignId();
    Logging.logd(
        "Attempting to record message impression in impression store for id: " + campaignId);
    Completable storeCampaignImpression =
        impressionStorageClient
            .storeImpression(
                CampaignImpression.newBuilder()
                    .setImpressionTimestampMillis(clock.now())
                    .setCampaignId(campaignId)
                    .build())
            .doOnError(e -> Logging.loge("Impression store write failure"))
            .doOnComplete(() -> Logging.logd("Impression store write success"));

    if (InAppMessageStreamManager.isAppForegroundEvent(triggeringEvent)) {
      Completable incrementAppForegroundRateLimit =
          rateLimiterClient
              .increment(appForegroundRateLimit)
              .doOnError(e -> Logging.loge("Rate limiter client write failure"))
              .doOnComplete(() -> Logging.logd("Rate limiter client write success"))
              .onErrorComplete(); // Absorb rate limiter write errors
      return incrementAppForegroundRateLimit.andThen(storeCampaignImpression);
    }

    return storeCampaignImpression;
  }

  /**
   * Converts an rx maybe to task.
   *
   * <p>Since the semantics of maybe are different from task, we adopt the following rules.
   *
   * <ul>
   *   <li>Maybe that resolves to a value is resolved to a succeeding task
   *   <li>Maybe that resolves to an exception is resolved to a failed task
   *   <li>Maybe that resolves to an error is resolved to a failed task with a wrapped exception
   *   <li>Maybe that resolves to empty is resolved to succeeding Void Task
   * </ul>
   */
  private static <T> Task<T> maybeToTask(Maybe<T> maybe, Scheduler scheduler) {
    TaskCompletionSource<T> tcs = new TaskCompletionSource<>();
    Disposable ignoredDisposable =
        maybe
            .doOnSuccess(tcs::setResult)
            .switchIfEmpty(
                Maybe.fromCallable(
                    () -> {
                      tcs.setResult(null);
                      return null;
                    }))
            .onErrorResumeNext(
                throwable -> {
                  if (throwable instanceof Exception) {
                    tcs.setException((Exception) throwable);
                  } else {
                    tcs.setException(new RuntimeException(throwable));
                  }
                  return Maybe.empty();
                })
            .subscribeOn(scheduler)
            .subscribe();

    return tcs.getTask();
  }
}
