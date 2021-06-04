package com.google.firebase.dynamiclinks.internal;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.android.gms.client.annotations.LegacyGmsCoreInheritance;
import com.google.android.gms.client.annotations.ReviewedExceptionGmsCoreInheritance;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.Api.ApiOptions;
import com.google.android.gms.common.api.Api.ApiOptions.NoOptions;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.internal.ClientSettings;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.errorprone.annotations.RestrictedInheritance;

/** */
@RestrictedInheritance(
    explanation =
        "Sub classing of GMS Core's APIs are restricted to GMS Core client libs and testing fakes.",
    link = "go/gmscore-restrictedinheritance",
    // This class can only be subclassed within GMS Core's code base.
    allowedOnPath = ".*java.*/com/google/android/gms.*",
    allowlistAnnotations = {
      LegacyGmsCoreInheritance.class,
      ReviewedExceptionGmsCoreInheritance.class
    })
public class DynamicLinksApi extends GoogleApi<NoOptions> {

  private static final Api.ClientKey<DynamicLinksClient> CLIENT_KEY =
      new Api.ClientKey<DynamicLinksClient>();

  private static final Api.AbstractClientBuilder<DynamicLinksClient, NoOptions> CLIENT_BUILDER =
      new Api.AbstractClientBuilder<DynamicLinksClient, NoOptions>() {
        @Override
        public DynamicLinksClient buildClient(
            Context context,
            Looper looper,
            ClientSettings commonSettings,
            NoOptions apiOptions,
            ConnectionCallbacks connectedListener,
            OnConnectionFailedListener connectionFailedListener) {
          return new DynamicLinksClient(
              context, looper, commonSettings, connectedListener, connectionFailedListener);
        }
      };

  static final Api<NoOptions> API =
      new Api<NoOptions>("DynamicLinks.API", CLIENT_BUILDER, CLIENT_KEY);

  /**
   * Use the main Looper for callbacks, otherwise the calling thread's Looper (if it has one) will
   * be used, which is undesirable for two reasons:
   *
   * <p>1. Clients might accidentally wait on tasks in the same thread that created the
   * FirebaseDynamicLinks object, thus blocking retries and creating a deadlock. 2. We want to be
   * able to create the FirebaseDynamicLinks instance as a singleton object, so it should not depend
   * on the state of the thread that happened to create it first.
   *
   * <p>Using the main Looper avoids these problems because it is always available, and it's
   * accepted that no blocking operations should be done on it. See {@link
   * com.google.firebase.appindexing.internal.FirebaseAppIndexImpl.FirebaseAppIndexClient}
   */
  @VisibleForTesting
  public DynamicLinksApi(@NonNull Context context) {
    super(context, API, ApiOptions.NO_OPTIONS /* options */, Settings.DEFAULT_SETTINGS);
  }
}
