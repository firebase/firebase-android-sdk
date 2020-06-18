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
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.AppForeground;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.ProgrammaticTrigger;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.MessageType;
import com.google.firebase.inappmessaging.model.ProtoMarshallerClient;
import com.google.firebase.inappmessaging.model.RateLimit;
import com.google.firebase.inappmessaging.model.TriggeredInAppMessage;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpressionList;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import java.util.Locale;
import javax.inject.Inject;

/**
 * Class to federate multiple clients and encapsulate the high level behavior of the fiam headless
 * sdk
 *
 * @hide
 */
@FirebaseAppScope
public class InAppMessageStreamManager {
  public static final String ON_FOREGROUND = "ON_FOREGROUND";
  private final ConnectableFlowable<String> appForegroundEventFlowable;
  private final ConnectableFlowable<String> programmaticTriggerEventFlowable;
  private final CampaignCacheClient campaignCacheClient;
  private final Clock clock;
  private final ApiClient apiClient;
  private final Schedulers schedulers;
  private final ImpressionStorageClient impressionStorageClient;
  private final RateLimiterClient rateLimiterClient;
  private final RateLimit appForegroundRateLimit;
  private final AnalyticsEventsManager analyticsEventsManager;
  private final TestDeviceHelper testDeviceHelper;
  private final AbtIntegrationHelper abtIntegrationHelper;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final DataCollectionHelper dataCollectionHelper;

  @Inject
  public InAppMessageStreamManager(
      @AppForeground ConnectableFlowable<String> appForegroundEventFlowable,
      @ProgrammaticTrigger ConnectableFlowable<String> programmaticTriggerEventFlowable,
      CampaignCacheClient campaignCacheClient,
      Clock clock,
      ApiClient apiClient,
      AnalyticsEventsManager analyticsEventsManager,
      Schedulers schedulers,
      ImpressionStorageClient impressionStorageClient,
      RateLimiterClient rateLimiterClient,
      @AppForeground RateLimit appForegroundRateLimit,
      TestDeviceHelper testDeviceHelper,
      FirebaseInstallationsApi firebaseInstallations,
      DataCollectionHelper dataCollectionHelper,
      AbtIntegrationHelper abtIntegrationHelper) {
    this.appForegroundEventFlowable = appForegroundEventFlowable;
    this.programmaticTriggerEventFlowable = programmaticTriggerEventFlowable;
    this.campaignCacheClient = campaignCacheClient;
    this.clock = clock;
    this.apiClient = apiClient;
    this.analyticsEventsManager = analyticsEventsManager;
    this.schedulers = schedulers;
    this.impressionStorageClient = impressionStorageClient;
    this.rateLimiterClient = rateLimiterClient;
    this.appForegroundRateLimit = appForegroundRateLimit;
    this.testDeviceHelper = testDeviceHelper;
    this.dataCollectionHelper = dataCollectionHelper;
    this.firebaseInstallations = firebaseInstallations;
    this.abtIntegrationHelper = abtIntegrationHelper;
  }

  private static boolean containsTriggeringCondition(String event, ThickContent content) {
    if (isAppForegroundEvent(event) && content.getIsTestCampaign()) {
      return true; // the triggering condition for test campaigns is always 'app foreground'
    }
    for (TriggeringCondition condition : content.getTriggeringConditionsList()) {
      if (hasFiamTrigger(condition, event) || hasAnalyticsTrigger(condition, event)) {
        Logging.logd(String.format("The event %s is contained in the list of triggers", event));
        return true;
      }
    }
    return false;
  }

  private static boolean hasFiamTrigger(TriggeringCondition tc, String event) {
    return tc.getFiamTrigger().toString().equals(event);
  }

  private static boolean hasAnalyticsTrigger(TriggeringCondition tc, String event) {
    return tc.getEvent().getName().equals(event);
  }

  private static boolean isActive(Clock clock, ThickContent content) {
    long campaignStartTime;
    long campaignEndTime;
    if (content.getPayloadCase().equals(ThickContent.PayloadCase.VANILLA_PAYLOAD)) {
      // Handle the campaign case
      campaignStartTime = content.getVanillaPayload().getCampaignStartTimeMillis();
      campaignEndTime = content.getVanillaPayload().getCampaignEndTimeMillis();
    } else if (content.getPayloadCase().equals(ThickContent.PayloadCase.EXPERIMENTAL_PAYLOAD)) {
      // Handle the experiment case
      campaignStartTime = content.getExperimentalPayload().getCampaignStartTimeMillis();
      campaignEndTime = content.getExperimentalPayload().getCampaignEndTimeMillis();
    } else {
      // If we have no valid payload then don't display
      return false;
    }
    long currentTime = clock.now();
    return currentTime > campaignStartTime && currentTime < campaignEndTime;
  }

  // Comparisons treat the numeric values of priorities like they were ranks i.e lower is better.
  // If one campaign is a test campaign it is of higher priority.
  // Example: P1 > P2. P2(test) > P1. P1(test) > P2(test)
  private static int compareByPriority(ThickContent content1, ThickContent content2) {
    if (content1.getIsTestCampaign() && !content2.getIsTestCampaign()) {
      return -1;
    }
    if (content2.getIsTestCampaign() && !content1.getIsTestCampaign()) {
      return 1;
    }
    return Integer.compare(content1.getPriority().getValue(), content2.getPriority().getValue());
  }

  public static boolean isAppForegroundEvent(TriggeringCondition event) {
    return event.getFiamTrigger().toString().equals(ON_FOREGROUND);
  }

  public static boolean isAppForegroundEvent(String event) {
    return event.equals(ON_FOREGROUND);
  }

  private boolean shouldIgnoreCache(String event) {
    if (testDeviceHelper.isAppInstallFresh()) {
      return isAppForegroundEvent(event);
    }
    return testDeviceHelper.isDeviceInTestMode();
  }

  public Flowable<TriggeredInAppMessage> createFirebaseInAppMessageStream() {
    return Flowable.merge(
            appForegroundEventFlowable,
            analyticsEventsManager.getAnalyticsEventsFlowable(),
            programmaticTriggerEventFlowable)
        .doOnNext(e -> Logging.logd("Event Triggered: " + e))
        .observeOn(schedulers.io())
        .concatMap(
            event -> {
              Maybe<FetchEligibleCampaignsResponse> cacheRead =
                  campaignCacheClient
                      .get()
                      .doOnSuccess(r -> Logging.logd("Fetched from cache"))
                      .doOnError(e -> Logging.logw("Cache read error: " + e.getMessage()))
                      .onErrorResumeNext(Maybe.empty()); // Absorb cache read failures

              Consumer<FetchEligibleCampaignsResponse> cacheWrite =
                  response ->
                      campaignCacheClient
                          .put(response)
                          .doOnComplete(() -> Logging.logd("Wrote to cache"))
                          .doOnError(e -> Logging.logw("Cache write error: " + e.getMessage()))
                          .onErrorResumeNext(
                              ignored -> Completable.complete()) // Absorb cache write fails
                          .subscribe();

              Function<ThickContent, Maybe<ThickContent>> filterAlreadyImpressed =
                  content ->
                      content.getIsTestCampaign()
                          ? Maybe.just(content)
                          : impressionStorageClient
                              .isImpressed(content)
                              .doOnError(
                                  e ->
                                      Logging.logw("Impression store read fail: " + e.getMessage()))
                              .onErrorResumeNext(
                                  Single.just(false)) // Absorb impression read errors
                              .doOnSuccess(isImpressed -> logImpressionStatus(content, isImpressed))
                              .filter(isImpressed -> !isImpressed)
                              .map(isImpressed -> content);

              Function<ThickContent, Maybe<ThickContent>> appForegroundRateLimitFilter =
                  content -> getContentIfNotRateLimited(event, content);

              Function<ThickContent, Maybe<ThickContent>> filterDisplayable =
                  thickContent -> {
                    switch (thickContent.getContent().getMessageDetailsCase()) {
                      case BANNER:
                      case IMAGE_ONLY:
                      case MODAL:
                      case CARD:
                        return Maybe.just(thickContent);
                      default:
                        Logging.logd("Filtering non-displayable message");
                        return Maybe.empty();
                    }
                  };

              Function<FetchEligibleCampaignsResponse, Maybe<TriggeredInAppMessage>>
                  selectThickContent =
                      response ->
                          getTriggeredInAppMessageMaybe(
                              event,
                              filterAlreadyImpressed,
                              appForegroundRateLimitFilter,
                              filterDisplayable,
                              response);

              Maybe<CampaignImpressionList> alreadySeenCampaigns =
                  impressionStorageClient
                      .getAllImpressions()
                      .doOnError(
                          e -> Logging.logw("Impressions store read fail: " + e.getMessage()))
                      .defaultIfEmpty(CampaignImpressionList.getDefaultInstance())
                      .onErrorResumeNext(Maybe.just(CampaignImpressionList.getDefaultInstance()));

              Maybe<InstallationIdResult> getIID =
                  Maybe.zip(
                          taskToMaybe(firebaseInstallations.getId()),
                          taskToMaybe(firebaseInstallations.getToken(false)),
                          InstallationIdResult::create)
                      .observeOn(schedulers.io());

              Function<CampaignImpressionList, Maybe<FetchEligibleCampaignsResponse>> serviceFetch =
                  campaignImpressionList -> {
                    if (!dataCollectionHelper.isAutomaticDataCollectionEnabled()) {
                      Logging.logi(
                          "Automatic data collection is disabled, not attempting campaign fetch from service.");
                      return Maybe.just(cacheExpiringResponse());
                    }

                    return getIID
                        .filter(InAppMessageStreamManager::validIID)
                        .map(iid -> apiClient.getFiams(iid, campaignImpressionList))
                        .switchIfEmpty(Maybe.just(cacheExpiringResponse()))
                        .doOnSuccess(
                            resp ->
                                Logging.logi(
                                    String.format(
                                        Locale.US,
                                        "Successfully fetched %d messages from backend",
                                        resp.getMessagesList().size())))
                        .doOnSuccess(
                            resp -> impressionStorageClient.clearImpressions(resp).subscribe())
                        .doOnSuccess(analyticsEventsManager::updateContextualTriggers)
                        .doOnSuccess(testDeviceHelper::processCampaignFetch)
                        .doOnError(e -> Logging.logw("Service fetch error: " + e.getMessage()))
                        .onErrorResumeNext(Maybe.empty()); // Absorb service failures
                  };

              if (shouldIgnoreCache(event)) {
                Logging.logi(
                    String.format(
                        "Forcing fetch from service rather than cache. "
                            + "Test Device: %s | App Fresh Install: %s",
                        testDeviceHelper.isDeviceInTestMode(),
                        testDeviceHelper.isAppInstallFresh()));
                return alreadySeenCampaigns
                    .flatMap(serviceFetch)
                    .flatMap(selectThickContent)
                    .toFlowable();
              }

              Logging.logd("Attempting to fetch campaigns using cache");
              return cacheRead
                  .switchIfEmpty(alreadySeenCampaigns.flatMap(serviceFetch).doOnSuccess(cacheWrite))
                  .flatMap(selectThickContent)
                  .toFlowable();
            })
        .observeOn(schedulers.mainThread()); // Updates are delivered on the main thread
  }

  private Maybe<ThickContent> getContentIfNotRateLimited(String event, ThickContent content) {
    if (!content.getIsTestCampaign() && isAppForegroundEvent(event)) {
      return rateLimiterClient
          .isRateLimited(appForegroundRateLimit)
          .doOnSuccess(
              isRateLimited -> Logging.logi("App foreground rate limited ? : " + isRateLimited))
          .onErrorResumeNext(Single.just(false)) // Absorb rate limit errors
          .filter(isRateLimited -> !isRateLimited)
          .map(isRateLimited -> content);
    }
    return Maybe.just(content);
  }

  private static void logImpressionStatus(ThickContent content, Boolean isImpressed) {
    if (content.getPayloadCase().equals(ThickContent.PayloadCase.VANILLA_PAYLOAD)) {
      Logging.logi(
          String.format(
              "Already impressed campaign %s ? : %s",
              content.getVanillaPayload().getCampaignName(), isImpressed));
    } else if (content.getPayloadCase().equals(ThickContent.PayloadCase.EXPERIMENTAL_PAYLOAD)) {
      Logging.logi(
          String.format(
              "Already impressed experiment %s ? : %s",
              content.getExperimentalPayload().getCampaignName(), isImpressed));
    }
  }

  private Maybe<TriggeredInAppMessage> getTriggeredInAppMessageMaybe(
      String event,
      Function<ThickContent, Maybe<ThickContent>> filterAlreadyImpressed,
      Function<ThickContent, Maybe<ThickContent>> appForegroundRateLimitFilter,
      Function<ThickContent, Maybe<ThickContent>> filterDisplayable,
      FetchEligibleCampaignsResponse response) {
    return Flowable.fromIterable(response.getMessagesList())
        .filter(content -> testDeviceHelper.isDeviceInTestMode() || isActive(clock, content))
        .filter(content -> containsTriggeringCondition(event, content))
        .flatMapMaybe(filterAlreadyImpressed)
        .flatMapMaybe(appForegroundRateLimitFilter)
        .flatMapMaybe(filterDisplayable)
        .sorted(InAppMessageStreamManager::compareByPriority)
        .firstElement()
        .flatMap(content -> triggeredInAppMessage(content, event));
  }

  private Maybe<TriggeredInAppMessage> triggeredInAppMessage(ThickContent content, String event) {
    String campaignId;
    String campaignName;
    if (content.getPayloadCase().equals(ThickContent.PayloadCase.VANILLA_PAYLOAD)) {
      // Handle vanilla campaign case
      campaignId = content.getVanillaPayload().getCampaignId();
      campaignName = content.getVanillaPayload().getCampaignName();
    } else if (content.getPayloadCase().equals(ThickContent.PayloadCase.EXPERIMENTAL_PAYLOAD)) {
      // Handle experiment case
      campaignId = content.getExperimentalPayload().getCampaignId();
      campaignName = content.getExperimentalPayload().getCampaignName();
      // At this point we set the experiment to become active in analytics.
      // As long as it's not a test experiment.
      if (!content.getIsTestCampaign()) {
        abtIntegrationHelper.setExperimentActive(
            content.getExperimentalPayload().getExperimentPayload());
      }
    } else {
      return Maybe.empty();
    }
    InAppMessage inAppMessage =
        ProtoMarshallerClient.decode(
            content.getContent(),
            campaignId,
            campaignName,
            content.getIsTestCampaign(),
            content.getDataBundleMap());
    if (inAppMessage.getMessageType().equals(MessageType.UNSUPPORTED)) {
      return Maybe.empty();
    }

    return Maybe.just(new TriggeredInAppMessage(inAppMessage, event));
  }

  private static boolean validIID(InstallationIdResult iid) {
    return !TextUtils.isEmpty(iid.installationId())
        && !TextUtils.isEmpty(iid.installationTokenResult().getToken());
  }

  @VisibleForTesting
  static FetchEligibleCampaignsResponse cacheExpiringResponse() {
    // Within the cache, we use '0' as a special case to 'never' expire. '1' is used when we want to
    // retry the getFiams call on subsequent event triggers, and force the cache to always expire
    return FetchEligibleCampaignsResponse.newBuilder().setExpirationEpochTimestampMillis(1).build();
  }

  private static <T> Maybe<T> taskToMaybe(Task<T> task) {
    return Maybe.create(
        emitter -> {
          task.addOnSuccessListener(
              result -> {
                emitter.onSuccess(result);
                emitter.onComplete();
              });
          task.addOnFailureListener(
              e -> {
                emitter.onError(e);
                emitter.onComplete();
              });
        });
  }
}
