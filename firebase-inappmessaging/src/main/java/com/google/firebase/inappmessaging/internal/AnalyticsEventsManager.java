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

import android.annotation.SuppressLint;
import android.text.TextUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.inappmessaging.CommonTypesProto;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.flowables.ConnectableFlowable;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Container for the analytics handler as well as the flowable used to act on emitted events
 *
 * @hide
 */
public class AnalyticsEventsManager {
  private final AnalyticsConnector analyticsConnector;
  private final ConnectableFlowable<String> flowable;
  private AnalyticsConnectorHandle handle;

  public AnalyticsEventsManager(AnalyticsConnector analyticsConnector) {
    this.analyticsConnector = analyticsConnector;
    AnalyticsFlowableSubscriber subscriber = new AnalyticsFlowableSubscriber();
    flowable = Flowable.<String>create(subscriber, BackpressureStrategy.BUFFER).publish();

    // We ignore the subscription since this connected flowable is expected to last the lifetime of
    // the app, but this calls the 'subscribe' method of the subscriber, which registers the handle
    flowable.connect();
  }

  @Nullable
  public AnalyticsConnectorHandle getHandle() {
    return handle;
  }

  public ConnectableFlowable<String> getAnalyticsEventsFlowable() {
    return flowable;
  }

  @VisibleForTesting
  static final String TOO_MANY_CONTEXTUAL_TRIGGERS_ERROR =
      "Too many contextual triggers defined - limiting to "
          + AnalyticsConstants.MAX_REGISTERED_EVENTS;

  @VisibleForTesting
  static Set<String> extractAnalyticsEventNames(FetchEligibleCampaignsResponse response) {
    Set<String> analyticsEvents = new HashSet<>();
    for (CampaignProto.ThickContent content : response.getMessagesList()) {
      for (CommonTypesProto.TriggeringCondition condition : content.getTriggeringConditionsList()) {
        if (!TextUtils.isEmpty(condition.getEvent().getName())) {
          analyticsEvents.add(condition.getEvent().getName());
        }
      }
    }
    // The analytics connector will automatically filter down to the maximum number of allowable
    // events,
    // and track sdks 'abusing' that limit, but we want to note this for the developers as well
    // Additionally, analytics also filters out 'ineligible' event names - which might result in
    // fewer than 50 eligible ones to register as contextual triggers.
    if (analyticsEvents.size() > AnalyticsConstants.MAX_REGISTERED_EVENTS) {
      Logging.logi(TOO_MANY_CONTEXTUAL_TRIGGERS_ERROR);
    }
    return analyticsEvents;
  }

  public void updateContextualTriggers(FetchEligibleCampaignsResponse serviceResponse) {
    Set<String> analyticsEventNames = extractAnalyticsEventNames(serviceResponse);
    Logging.logd(
        "Updating contextual triggers for the following analytics events: " + analyticsEventNames);
    handle.registerEventNames(analyticsEventNames);
  }

  private class AnalyticsFlowableSubscriber implements FlowableOnSubscribe<String> {

    AnalyticsFlowableSubscriber() {}

    @Override
    // fiam uses an AnalyticsConnector proxy that is Deferred-aware so it's safe to suppress.
    @SuppressLint("InvalidDeferredApiUse")
    public void subscribe(FlowableEmitter<String> emitter) {
      Logging.logd("Subscribing to analytics events.");
      handle =
          analyticsConnector.registerAnalyticsConnectorListener(
              AnalyticsConstants.ORIGIN_FIAM, new FiamAnalyticsConnectorListener(emitter));
    }
  }
}
