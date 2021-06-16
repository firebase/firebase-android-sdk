package com.google.firebase.dynamiclinks.internal;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.internal.BuildConstants;
import com.google.android.gms.common.internal.ClientSettings;
import com.google.android.gms.common.internal.GmsClient;
import com.google.android.gms.common.proto.GCoreServiceId.ServiceId;

/** GmsClient class for Dynamic Links. */
public class DynamicLinksClient extends GmsClient<IDynamicLinksService> {

  public static final String ACTION_START_SERVICE =
      "com.google.firebase.dynamiclinks.service.START";
  public static final String SERVICE_DESCRIPTOR =
      "com.google.firebase.dynamiclinks.internal.IDynamicLinksService";

  public DynamicLinksClient(
      Context context,
      Looper looper,
      ClientSettings clientSettings,
      GoogleApiClient.ConnectionCallbacks connectedListener,
      GoogleApiClient.OnConnectionFailedListener connectionFailedListener) {
    super(
        context,
        looper,
        ServiceId.DYNAMIC_LINKS_API_VALUE,
        clientSettings,
        connectedListener,
        connectionFailedListener);
  }

  @NonNull
  @Override
  protected String getStartServiceAction() {
    return ACTION_START_SERVICE;
  }

  @NonNull
  @Override
  protected String getServiceDescriptor() {
    return SERVICE_DESCRIPTOR;
  }

  @Nullable
  @Override
  protected IDynamicLinksService createServiceInterface(IBinder binder) {
    return IDynamicLinksService.Stub.asInterface(binder);
  }

  void getDynamicLink(IDynamicLinksCallbacks.Stub callback, @Nullable String dynamicLink) {
    try {
      getService().getDynamicLink(callback, dynamicLink);
    } catch (RemoteException e) {
      // client is dead.
    }
  }

  void createShortDynamicLink(IDynamicLinksCallbacks.Stub callback, Bundle parameters) {
    try {
      getService().createShortDynamicLink(callback, parameters);
    } catch (RemoteException e) {
      // client is dead.
    }
  }

  @Override
  public int getMinApkVersion() {
    // This should be compatible with the V17 .apk. Update this value IFF a newer .apk is required
    // or an older version is now supported. Do _not_ use JAR_BUILD_VERSION_CODE as long as this
    // code is shipped in the 3P SDK (which ships ~from head / dev and would not work with the head
    // version of the .apk)..
    return BuildConstants.BaseApkVersion.V17;
  }

  @Override
  public boolean usesClientTelemetry() {
    return true;
  }
}
