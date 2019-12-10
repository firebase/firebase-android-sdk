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

import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.AppForeground;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.inappmessaging.model.RateLimit;
import javax.inject.Inject;

public class DisplayCallbacksFactory {

  private final ImpressionStorageClient impressionStorageClient;
  private final Clock clock;
  private final Schedulers schedulers;
  private final RateLimiterClient rateLimiterClient;
  private final CampaignCacheClient campaignCacheClient;
  private final RateLimit appForegroundRateLimit;
  private final MetricsLoggerClient metricsLoggerClient;
  private final DataCollectionHelper dataCollectionHelper;

  @Inject
  public DisplayCallbacksFactory(
      ImpressionStorageClient impressionStorageClient,
      Clock clock,
      Schedulers schedulers,
      RateLimiterClient rateLimiterClient,
      CampaignCacheClient campaignCacheClient,
      @AppForeground RateLimit appForegroundRateLimit,
      MetricsLoggerClient metricsLoggerClient,
      DataCollectionHelper dataCollectionHelper) {
    this.impressionStorageClient = impressionStorageClient;
    this.clock = clock;
    this.schedulers = schedulers;
    this.rateLimiterClient = rateLimiterClient;
    this.campaignCacheClient = campaignCacheClient;
    this.appForegroundRateLimit = appForegroundRateLimit;
    this.metricsLoggerClient = metricsLoggerClient;
    this.dataCollectionHelper = dataCollectionHelper;
  }

  public FirebaseInAppMessagingDisplayCallbacks generateDisplayCallback(
      InAppMessage inAppMessage, String triggeringEvent) {

    return new DisplayCallbacksImpl(
        impressionStorageClient,
        clock,
        schedulers,
        rateLimiterClient,
        campaignCacheClient,
        appForegroundRateLimit,
        metricsLoggerClient,
        dataCollectionHelper,
        inAppMessage,
        triggeringEvent);
  }
}
