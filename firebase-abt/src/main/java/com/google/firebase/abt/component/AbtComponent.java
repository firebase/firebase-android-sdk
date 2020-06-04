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

package com.google.firebase.abt.component;

import android.content.Context;
import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.abt.FirebaseABTesting.OriginService;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import java.util.HashMap;
import java.util.Map;

/**
 * Component for providing multiple Firebase A/B Testing (ABT) instances. Firebase Android
 * Components uses this class to retrieve instances of ABT for dependency injection.
 *
 * <p>A unique ABT instance is returned for each {@code originService}.
 *
 * @author Miraziz Yusupov
 */
public class AbtComponent {

  @GuardedBy("this")
  private final Map<String, FirebaseABTesting> abtOriginInstances = new HashMap<>();

  private final Context appContext;
  private final AnalyticsConnector analyticsConnector;

  /** Firebase ABT Component constructor. */
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  protected AbtComponent(Context appContext, AnalyticsConnector analyticsConnector) {
    this.appContext = appContext;
    this.analyticsConnector = analyticsConnector;
  }

  /**
   * Returns the Firebase ABT instance associated with the given {@code originService}.
   *
   * @param originService the name of the ABT client, as defined in Analytics.
   */
  public synchronized FirebaseABTesting get(@OriginService String originService) {
    if (!abtOriginInstances.containsKey(originService)) {
      abtOriginInstances.put(originService, createAbtInstance(originService));
    }
    return abtOriginInstances.get(originService);
  }

  @VisibleForTesting
  protected FirebaseABTesting createAbtInstance(@OriginService String originService) {
    return new FirebaseABTesting(appContext, analyticsConnector, originService);
  }
}
