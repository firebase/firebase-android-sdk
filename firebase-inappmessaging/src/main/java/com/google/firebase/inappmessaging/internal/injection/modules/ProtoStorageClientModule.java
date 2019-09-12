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

package com.google.firebase.inappmessaging.internal.injection.modules;

import android.app.Application;
import com.google.firebase.inappmessaging.internal.ProtoStorageClient;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.CampaignCache;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.ImpressionStore;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.RateLimit;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Bindings for proto storage client
 *
 * @hide
 */
@Module
public class ProtoStorageClientModule {
  public static final String CAMPAIGN_CACHE_FILE = "fiam_eligible_campaigns_cache_file";
  public static final String IMPRESSIONS_STORE_FILE = "fiam_impressions_store_file";
  public static final String RATE_LIMIT_STORE_FILE = "rate_limit_store_file";

  @Provides
  @Singleton
  @CampaignCache
  public ProtoStorageClient providesProtoStorageClientForCampaign(Application application) {
    return new ProtoStorageClient(application, CAMPAIGN_CACHE_FILE);
  }

  @Provides
  @Singleton
  @ImpressionStore
  public ProtoStorageClient providesProtoStorageClientForImpressionStore(Application application) {
    return new ProtoStorageClient(application, IMPRESSIONS_STORE_FILE);
  }

  @Provides
  @Singleton
  @RateLimit
  public ProtoStorageClient providesProtoStorageClientForLimiterStore(Application application) {
    return new ProtoStorageClient(application, RATE_LIMIT_STORE_FILE);
  }
}
