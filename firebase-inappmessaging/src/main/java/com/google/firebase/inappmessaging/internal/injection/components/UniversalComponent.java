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

package com.google.firebase.inappmessaging.internal.injection.components;

import android.app.Application;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inappmessaging.internal.AnalyticsEventsManager;
import com.google.firebase.inappmessaging.internal.CampaignCacheClient;
import com.google.firebase.inappmessaging.internal.DeveloperListenerManager;
import com.google.firebase.inappmessaging.internal.ImpressionStorageClient;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.ProviderInstaller;
import com.google.firebase.inappmessaging.internal.RateLimiterClient;
import com.google.firebase.inappmessaging.internal.Schedulers;
import com.google.firebase.inappmessaging.internal.injection.modules.AnalyticsEventsModule;
import com.google.firebase.inappmessaging.internal.injection.modules.AppMeasurementModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ApplicationModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ForegroundFlowableModule;
import com.google.firebase.inappmessaging.internal.injection.modules.GrpcChannelModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ProgrammaticContextualTriggerFlowableModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ProtoStorageClientModule;
import com.google.firebase.inappmessaging.internal.injection.modules.RateLimitModule;
import com.google.firebase.inappmessaging.internal.injection.modules.SchedulerModule;
import com.google.firebase.inappmessaging.internal.injection.modules.SystemClockModule;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.AnalyticsListener;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.AppForeground;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.ProgrammaticTrigger;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.inappmessaging.model.ProtoMarshallerClient;
import com.google.firebase.inappmessaging.model.RateLimit;
import dagger.Component;
import io.grpc.Channel;
import io.reactivex.flowables.ConnectableFlowable;
import javax.inject.Singleton;

/**
 * A single Network component is shared by all components in the {@link
 * com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope}
 *
 * @hide
 */
@Singleton
@Component(
    modules = {
      GrpcChannelModule.class,
      SchedulerModule.class,
      ApplicationModule.class,
      ForegroundFlowableModule.class,
      ProgrammaticContextualTriggerFlowableModule.class,
      AnalyticsEventsModule.class,
      ProtoStorageClientModule.class,
      SystemClockModule.class,
      RateLimitModule.class,
      AppMeasurementModule.class
    })
public interface UniversalComponent {
  ProviderInstaller probiderInstaller();

  Channel gRPCChannel();

  Schedulers schedulers();

  @AppForeground
  ConnectableFlowable<String> appForegroundEventFlowable();

  @ProgrammaticTrigger
  ConnectableFlowable<String> programmaticContextualTriggerFlowable();

  @ProgrammaticTrigger
  ProgramaticContextualTriggers programmaticContextualTriggers();

  @AnalyticsListener
  ConnectableFlowable<String> analyticsEventsFlowable();

  AnalyticsEventsManager analyticsEventsManager();

  AnalyticsConnector analyticsConnector();

  Subscriber firebaseEventsSubscriber();

  CampaignCacheClient campaignCacheClient();

  ImpressionStorageClient impressionStorageClient();

  Clock clock();

  ProtoMarshallerClient protoMarshallerClient();

  RateLimiterClient rateLimiterClient();

  Application application();

  @AppForeground
  RateLimit appForegroundRateLimit();

  DeveloperListenerManager developerListenerManager();
}
