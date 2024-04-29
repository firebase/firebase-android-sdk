// Copyright 2021 Google LLC
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

package com.google.firebase.dynamiclinks.internal;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.common.api.Api.ApiOptions.NoOptions;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.internal.TaskApiCall;
import com.google.android.gms.common.api.internal.TaskUtil;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.DynamicLink.Builder;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import com.google.firebase.inject.Provider;

/**
 * Implementation of FirebaseDynamicLinks that passes requests to the binder interface to gmscore.
 */
public class FirebaseDynamicLinksImpl extends FirebaseDynamicLinks {

  // Scion data stored as a Bundle of Bundles with the event name as the key to the params Bundle.
  public static final String KEY_SCION_DATA = "scionData";
  public static final String EXTRA_DYNAMIC_LINK_DATA =
      "com.google.firebase.dynamiclinks.DYNAMIC_LINK_DATA";

  private static final String TAG = "FDL";

  // Value must be kept in sync with analytics: AppMeasurement.FDL_ORIGIN
  private static final String ANALYTICS_FDL_ORIGIN = "fdl";

  private final GoogleApi<NoOptions> googleApi;

  private final Provider<AnalyticsConnector> analytics;

  private final FirebaseApp firebaseApp;

  public FirebaseDynamicLinksImpl(FirebaseApp firebaseApp, Provider<AnalyticsConnector> analytics) {
    this(new DynamicLinksApi(firebaseApp.getApplicationContext()), firebaseApp, analytics);
  }

  // This overload exists to allow injecting a mock GoogleApi instance in tests.
  @VisibleForTesting
  public FirebaseDynamicLinksImpl(
      GoogleApi<NoOptions> googleApi,
      FirebaseApp firebaseApp,
      Provider<AnalyticsConnector> analytics) {
    this.googleApi = googleApi;
    this.firebaseApp = Preconditions.checkNotNull(firebaseApp);
    this.analytics = analytics;

    if (analytics.get() == null) {
      // b/34217790: Try to get an instance of Analytics. This initializes Google Analytics
      // if it is set up for the app, which sets up the association for the app and package name,
      // allowing GmsCore to log FDL events on behalf of the app.

      // AppMeasurement was not found. This probably means that the app did not include
      // the FirebaseAnalytics dependency.
      Log.w(
          TAG,
          "FDL logging failed. Add a dependency for Firebase Analytics"
              + " to your app to enable logging of Dynamic Link events.");
    }
  }

  public FirebaseApp getFirebaseApp() {
    return firebaseApp;
  }

  @Nullable
  public PendingDynamicLinkData getPendingDynamicLinkData(@NonNull Intent intent) {
    DynamicLinkData dynamicLinkData =
        SafeParcelableSerializer.deserializeFromIntentExtra(
            intent, EXTRA_DYNAMIC_LINK_DATA, DynamicLinkData.CREATOR);
    return dynamicLinkData != null ? new PendingDynamicLinkData(dynamicLinkData) : null;
  }

  @Override
  public Task<PendingDynamicLinkData> getDynamicLink(@Nullable final Intent intent) {
    String dynamicLinkDataString = intent != null ? intent.getDataString() : null;
    Task<PendingDynamicLinkData> result =
        googleApi.doWrite(new GetDynamicLinkImpl(analytics, dynamicLinkDataString));

    if (intent != null) {
      PendingDynamicLinkData pendingDynamicLinkData = getPendingDynamicLinkData(intent);
      if (pendingDynamicLinkData != null) {
        // DynamicLinkData included in the Intent, return it immediately and allow the Task to run
        // in the background to do logging and mark the FDL as returned.
        result = Tasks.forResult(pendingDynamicLinkData);
      }
    }

    return result;
  }

  @Override
  public Task<PendingDynamicLinkData> getDynamicLink(@NonNull final Uri dynamicLinkUri) {
    return googleApi.doWrite(new GetDynamicLinkImpl(analytics, dynamicLinkUri.toString()));
  }

  @Override
  public DynamicLink.Builder createDynamicLink() {
    return new DynamicLink.Builder(this);
  }

  public static Uri createDynamicLink(Bundle builderParameters) {
    verifyDomainUriPrefix(builderParameters);
    Uri longLink = builderParameters.getParcelable(Builder.KEY_DYNAMIC_LINK);
    if (longLink == null) {
      // Long link was not supplied, build the Dynamic Link from Dynamic Link parameters.
      Uri.Builder builder = new Uri.Builder();
      String domainUriPrefix =
          Preconditions.checkNotNull(builderParameters.getString(Builder.KEY_DOMAIN_URI_PREFIX));
      Uri uri = Uri.parse(domainUriPrefix);
      builder.scheme(uri.getScheme());
      builder.authority(uri.getAuthority());
      builder.path(uri.getPath());
      Bundle fdlParameters = builderParameters.getBundle(Builder.KEY_DYNAMIC_LINK_PARAMETERS);
      if (fdlParameters != null) {
        for (String key : fdlParameters.keySet()) {
          Object value = fdlParameters.get(key);
          if (value != null) {
            builder.appendQueryParameter(key, value.toString());
          }
        }
      }
      longLink = builder.build();
    }
    return longLink;
  }

  public Task<ShortDynamicLink> createShortDynamicLink(final Bundle builderParameters) {
    verifyDomainUriPrefix(builderParameters);
    return googleApi.doWrite(new CreateShortDynamicLinkImpl(builderParameters));
  }

  public static void verifyDomainUriPrefix(Bundle builderParameters) {
    Uri longLink = builderParameters.getParcelable(Builder.KEY_DYNAMIC_LINK);
    if (TextUtils.isEmpty(builderParameters.getString(Builder.KEY_DOMAIN_URI_PREFIX))
        && longLink == null) {
      throw new IllegalArgumentException(
          "FDL domain is missing. Set with setDomainUriPrefix() or setDynamicLinkDomain().");
    }
  }

  static final class GetDynamicLinkImpl
      extends TaskApiCall<DynamicLinksClient, PendingDynamicLinkData> {

    @Nullable private final String dynamicLink;
    private final Provider<AnalyticsConnector> analytics;

    GetDynamicLinkImpl(Provider<AnalyticsConnector> analytics, @Nullable String dynamicLink) {
      super(null, false, FirebaseDynamicLinksImplConstants.GET_DYNAMIC_LINK_METHOD_KEY);
      this.dynamicLink = dynamicLink;
      this.analytics = analytics;
    }

    @Override
    protected void doExecute(
        DynamicLinksClient clientImpl,
        final TaskCompletionSource<PendingDynamicLinkData> completionSource)
        throws RemoteException {
      clientImpl.getDynamicLink(new DynamicLinkCallbacks(analytics, completionSource), dynamicLink);
    }
  }

  static final class CreateShortDynamicLinkImpl
      extends TaskApiCall<DynamicLinksClient, ShortDynamicLink> {

    private final Bundle builderParameters;

    CreateShortDynamicLinkImpl(Bundle builderParameters) {
      super(null, false, FirebaseDynamicLinksImplConstants.CREATE_SHORT_DYNAMIC_LINK_METHOD_KEY);
      this.builderParameters = builderParameters;
    }

    @Override
    protected void doExecute(
        DynamicLinksClient clientImpl,
        final TaskCompletionSource<ShortDynamicLink> completionSource)
        throws RemoteException {
      clientImpl.createShortDynamicLink(
          new CreateShortDynamicLinkCallbacks(completionSource), builderParameters);
    }
  }

  static class AbstractDynamicLinkCallbacks extends IDynamicLinksCallbacks.Stub {

    @Override
    public void onGetDynamicLink(Status status, @Nullable DynamicLinkData dynamicLinkData) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onCreateShortDynamicLink(
        Status status, @Nullable ShortDynamicLinkImpl shortDynamicLink) {
      throw new UnsupportedOperationException();
    }
  }

  static class DynamicLinkCallbacks extends AbstractDynamicLinkCallbacks {
    private final TaskCompletionSource<PendingDynamicLinkData> completionSource;
    private final Provider<AnalyticsConnector> analytics;

    public DynamicLinkCallbacks(
        Provider<AnalyticsConnector> analytics,
        TaskCompletionSource<PendingDynamicLinkData> completionSource) {
      this.analytics = analytics;
      this.completionSource = completionSource;
    }

    @Override
    public void onGetDynamicLink(Status status, @Nullable DynamicLinkData dynamicLinkData) {
      // Send result to client.
      TaskUtil.setResultOrApiException(
          status,
          dynamicLinkData == null ? null : new PendingDynamicLinkData(dynamicLinkData),
          completionSource);
      // Log any scion data included with the result.
      if (dynamicLinkData == null) {
        return;
      }
      Bundle scionData = dynamicLinkData.getExtensionBundle().getBundle(KEY_SCION_DATA);
      // Scion data stored with the event name as the key to the params Bundle.
      if (scionData == null || scionData.keySet() == null) {
        return;
      }

      AnalyticsConnector connector = analytics.get();
      if (connector == null) {
        return;
      }

      for (String name : scionData.keySet()) {
        Bundle params = scionData.getBundle(name);
        connector.logEvent(ANALYTICS_FDL_ORIGIN, name, params);
      }
    }
  }

  static class CreateShortDynamicLinkCallbacks extends AbstractDynamicLinkCallbacks {
    private final TaskCompletionSource<ShortDynamicLink> completionSource;

    CreateShortDynamicLinkCallbacks(TaskCompletionSource<ShortDynamicLink> completionSource) {
      this.completionSource = completionSource;
    }

    @Override
    public void onCreateShortDynamicLink(
        Status status, @Nullable ShortDynamicLinkImpl shortDynamicLink) {
      TaskUtil.setResultOrApiException(status, shortDynamicLink, completionSource);
    }
  }
}
