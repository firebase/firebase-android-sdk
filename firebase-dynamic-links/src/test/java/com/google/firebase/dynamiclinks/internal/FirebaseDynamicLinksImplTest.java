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

package com.google.firebase.dynamiclinks.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.internal.TaskApiCall;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.dynamiclinks.DynamicLink.Builder;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import com.google.firebase.dynamiclinks.internal.FirebaseDynamicLinksImpl.CreateShortDynamicLinkImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Test {@link FirebaseDynamicLinksImpl}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseDynamicLinksImplTest {

  private static final String DEEP_LINK = "http://deeplink";
  private static final String DYNAMIC_LINK = "http://test.com/dynamic?link=" + DEEP_LINK;
  private static final int MINIMUM_VERSION = 1234;
  private static final long CLICK_TIMESTAMP = 54321L;

  private static final String FDL_SCHEME = "https";
  private static final String LONG_LINK = "https://long.link";
  private static final String DOMAIN = "https://domain";
  private static final String AUTHORITY = "domain";
  private static final String PARAMETER = "parameter";
  private static final String VALUE = "value";

  private static final String SCION_EVENT_NAME = "eventName";

  private static final String EXTRA_DYNAMIC_LINK_DATA =
      "com.google.firebase.dynamiclinks.DYNAMIC_LINK_DATA";

  private static final String ANALYTICS_FDL_ORIGIN = "fdl";

  @Mock private AnalyticsConnector mockAnalytics;
  @Mock private DynamicLinksApi mockGoogleApi;
  @Mock private TaskCompletionSource<PendingDynamicLinkData> mockCompletionSource;
  @Mock private TaskCompletionSource<ShortDynamicLink> mockShortFDLCompletionSource;
  @Mock private DynamicLinksClient mockDynamicLinksClient;
  private FirebaseApp firebaseApp;
  private FirebaseDynamicLinksImpl api;
  private Uri updateAppUri;

  private static Bundle createDynamicLinkBundle() {
    Bundle builderParameters = new Bundle();
    builderParameters.putParcelable(Builder.KEY_DYNAMIC_LINK, Uri.parse(LONG_LINK));
    return builderParameters;
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    FirebaseOptions.Builder firebaseOptionsBuilder =
        new FirebaseOptions.Builder().setApplicationId("application_id").setApiKey("api_key");
    firebaseApp =
        FirebaseApp.initializeApp(RuntimeEnvironment.application, firebaseOptionsBuilder.build());
    // Create uri with properly escaped query params.
    updateAppUri =
        Uri.parse("market://details?id=com.google.android.gm&min_version=10")
            .buildUpon()
            .appendQueryParameter("url", "http://example.google.com")
            .build();
    api = new FirebaseDynamicLinksImpl(mockGoogleApi, mockAnalytics);
  }

  @Test
  public void testFirebaseDynamicLinksImpl() {
    FirebaseDynamicLinksImpl impl = new FirebaseDynamicLinksImpl(firebaseApp, mockAnalytics);
    assertNotNull(impl);
  }

  @Test
  public void testGetPendingDynamicLinkData() {
    Bundle extensions = new Bundle();
    extensions.putString(PARAMETER, VALUE);
    DynamicLinkData dynamicLinkData =
        new DynamicLinkData(
            DYNAMIC_LINK,
            DEEP_LINK,
            MINIMUM_VERSION,
            CLICK_TIMESTAMP,
            extensions,
            null /* redirectUrl */);
    Intent intent = new Intent();
    SafeParcelableSerializer.serializeToIntentExtra(
        dynamicLinkData, intent, EXTRA_DYNAMIC_LINK_DATA);

    PendingDynamicLinkData pendingDynamicLinkData = api.getPendingDynamicLinkData(intent);
    assertNotNull(pendingDynamicLinkData);
    assertEquals(Uri.parse(DEEP_LINK), pendingDynamicLinkData.getLink());
    assertEquals(MINIMUM_VERSION, pendingDynamicLinkData.getMinimumAppVersion());
    assertEquals(CLICK_TIMESTAMP, pendingDynamicLinkData.getClickTimestamp());
    Bundle returnExtensions = pendingDynamicLinkData.getExtensions();
    assertEquals(extensions.keySet(), returnExtensions.keySet());
    for (String key : extensions.keySet()) {
      assertEquals(extensions.getString(key), returnExtensions.getString(key));
    }
    assertNull(pendingDynamicLinkData.getRedirectUrl());
  }

  @Test
  public void testGetPendingDynamicLinkData_NoDynamicLinkData() {
    assertNull(api.getPendingDynamicLinkData(new Intent()));
  }

  @Test
  public void testGetDynamicLink_Intent() {
    Intent intent = new Intent();
    api.getDynamicLink(intent);
    verify(mockGoogleApi)
        .doWrite(ArgumentMatchers.<TaskApiCall<DynamicLinksClient, PendingDynamicLinkData>>any());
  }

  @Test
  public void testGetDynamicLink_IntentWithDynamicLinkData() {
    Bundle extensions = new Bundle();
    extensions.putString(PARAMETER, VALUE);
    DynamicLinkData dynamicLinkData =
        new DynamicLinkData(
            DYNAMIC_LINK,
            DEEP_LINK,
            MINIMUM_VERSION,
            CLICK_TIMESTAMP,
            extensions,
            null /* redirectUrl */);
    Intent intent = new Intent();
    SafeParcelableSerializer.serializeToIntentExtra(
        dynamicLinkData, intent, EXTRA_DYNAMIC_LINK_DATA);

    Task<PendingDynamicLinkData> task = api.getDynamicLink(intent);
    verify(mockGoogleApi)
        .doWrite(ArgumentMatchers.<TaskApiCall<DynamicLinksClient, PendingDynamicLinkData>>any());
    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    PendingDynamicLinkData pendingDynamicLinkData = task.getResult();
    assertNotNull(pendingDynamicLinkData);
    assertEquals(Uri.parse(DEEP_LINK), pendingDynamicLinkData.getLink());
    assertEquals(MINIMUM_VERSION, pendingDynamicLinkData.getMinimumAppVersion());
    assertEquals(CLICK_TIMESTAMP, pendingDynamicLinkData.getClickTimestamp());
    Bundle returnExtensions = pendingDynamicLinkData.getExtensions();
    assertEquals(extensions.keySet(), returnExtensions.keySet());
    for (String key : extensions.keySet()) {
      assertEquals(extensions.getString(key), returnExtensions.getString(key));
    }
    assertNull(pendingDynamicLinkData.getRedirectUrl());
  }

  @Test
  public void testDynamicLinkCallbacks_onGetDynamicLink() {
    FirebaseDynamicLinksImpl.DynamicLinkCallbacks callbacks =
        new FirebaseDynamicLinksImpl.DynamicLinkCallbacks(mockAnalytics, mockCompletionSource);
    Bundle scionParams = new Bundle();
    DynamicLinkData data = genDynamicLinkData(scionParams);
    callbacks.onGetDynamicLink(Status.RESULT_SUCCESS, data);
    verify(mockCompletionSource).setResult(any(PendingDynamicLinkData.class));
  }

  @Test
  public void testDynamicLinkCallbacks_onGetDynamicLink_missingAnalytics() {
    // Make sure that a result is returned even if scion is not linked.
    FirebaseDynamicLinksImpl.DynamicLinkCallbacks callbacks =
        new FirebaseDynamicLinksImpl.DynamicLinkCallbacks(
            /* analytics= */ null, mockCompletionSource);
    DynamicLinkData data = genDynamicLinkData(new Bundle());
    callbacks.onGetDynamicLink(Status.RESULT_SUCCESS, data);
    verify(mockCompletionSource).setResult(any(PendingDynamicLinkData.class));
  }

  @Test
  public void testDynamicLinkCallbacks_onGetDynamicLink_NoData() {
    FirebaseDynamicLinksImpl.DynamicLinkCallbacks callbacks =
        new FirebaseDynamicLinksImpl.DynamicLinkCallbacks(mockAnalytics, mockCompletionSource);
    callbacks.onGetDynamicLink(Status.RESULT_SUCCESS, null);
    verify(mockCompletionSource).setResult(null);
  }

  @Test
  public void testDynamicLinkCallbacks_onGetDynamicLink_Exception() {
    FirebaseDynamicLinksImpl.DynamicLinkCallbacks callbacks =
        new FirebaseDynamicLinksImpl.DynamicLinkCallbacks(mockAnalytics, mockCompletionSource);
    callbacks.onGetDynamicLink(Status.RESULT_INTERNAL_ERROR, null);
    verify(mockCompletionSource).setException(any(Exception.class));
  }

  @Test
  public void testGetDynamicLink_Uri() {
    Uri dynamicLink = Uri.parse(DYNAMIC_LINK);
    api.getDynamicLink(dynamicLink);
    verify(mockGoogleApi)
        .doWrite(ArgumentMatchers.<TaskApiCall<DynamicLinksClient, PendingDynamicLinkData>>any());
  }

  @Test
  public void testGetDynamicLink_doExecute() {
    final FirebaseDynamicLinksImpl.GetDynamicLinkImpl getDynamicLink =
        new FirebaseDynamicLinksImpl.GetDynamicLinkImpl(mockAnalytics, DYNAMIC_LINK);
    try {
      getDynamicLink.doExecute(mockDynamicLinksClient, mockCompletionSource);
      ArgumentCaptor<IDynamicLinksCallbacks.Stub> captorCallbacks =
          ArgumentCaptor.forClass(IDynamicLinksCallbacks.Stub.class);

      verify(mockDynamicLinksClient).getDynamicLink(captorCallbacks.capture(), eq(DYNAMIC_LINK));
      captorCallbacks
          .getValue()
          .onGetDynamicLink(Status.RESULT_SUCCESS, genDynamicLinkData(new Bundle()));
    } catch (RemoteException e) {
      fail("Unexpected Remote Exception");
    }
  }

  @Test
  public void testCreateDynamicLink() {
    assertNotNull(api.createDynamicLink());
  }

  @Test
  public void testCreateDynamicLink_Bundle_LongLink() {
    Uri dynamicLink = FirebaseDynamicLinksImpl.createDynamicLink(createDynamicLinkBundle());
    assertEquals(LONG_LINK, dynamicLink.toString());
  }

  @Test
  public void testCreateDynamicLink_Bundle_Parameters() {
    Bundle builderParameters = new Bundle();
    builderParameters.putString(Builder.KEY_DOMAIN_URI_PREFIX, DOMAIN);
    Bundle fdlParameters = new Bundle();
    fdlParameters.putString(PARAMETER, VALUE);
    builderParameters.putBundle(Builder.KEY_DYNAMIC_LINK_PARAMETERS, fdlParameters);
    Uri dynamicLink = FirebaseDynamicLinksImpl.createDynamicLink(builderParameters);
    assertEquals(FDL_SCHEME, dynamicLink.getScheme());
    assertEquals(AUTHORITY, dynamicLink.getAuthority());
    assertEquals(VALUE, dynamicLink.getQueryParameter(PARAMETER));
  }

  @Test
  public void testCreateShortDynamicLink() {
    api.createShortDynamicLink(createDynamicLinkBundle());
    verify(mockGoogleApi).doWrite(any(CreateShortDynamicLinkImpl.class));
  }

  @Test
  public void testCreateShortDynamicLink_doExecute() {
    Bundle bundle = createDynamicLinkBundle();
    final FirebaseDynamicLinksImpl.CreateShortDynamicLinkImpl createShortDynamicLink =
        new FirebaseDynamicLinksImpl.CreateShortDynamicLinkImpl(bundle);
    try {
      createShortDynamicLink.doExecute(mockDynamicLinksClient, mockShortFDLCompletionSource);
      ArgumentCaptor<IDynamicLinksCallbacks.Stub> captorCallbacks =
          ArgumentCaptor.forClass(IDynamicLinksCallbacks.Stub.class);

      verify(mockDynamicLinksClient).createShortDynamicLink(captorCallbacks.capture(), eq(bundle));
      captorCallbacks.getValue().onCreateShortDynamicLink(Status.RESULT_SUCCESS, null);
    } catch (RemoteException e) {
      fail("Unexpected Remote Exception");
    }
  }

  @Test
  public void testverifyDomainUriPrefix_LongLink() {
    // Should not throw an Exception.
    FirebaseDynamicLinksImpl.verifyDomainUriPrefix(createDynamicLinkBundle());
  }

  @Test
  public void testverifyDomainUriPrefix_WithDomain() {
    // Should not throw an Exception.
    FirebaseDynamicLinksImpl.verifyDomainUriPrefix(createDynamicLinkBundle());
  }

  @Test
  public void testverifyDomainUriPrefix_WithNoDomain() {
    try {
      FirebaseDynamicLinksImpl.verifyDomainUriPrefix(new Bundle());
      fail("Expected IllegalArgumentException with no domain set.");
    } catch (IllegalArgumentException expected) {
      // Expected
    }
  }

  private DynamicLinkData genDynamicLinkData(Bundle scionParams) {
    Bundle extensions = new Bundle();
    Bundle scionData = new Bundle();
    scionData.putBundle(SCION_EVENT_NAME, scionParams);
    extensions.putBundle(FirebaseDynamicLinksImpl.KEY_SCION_DATA, scionData);
    return new DynamicLinkData(
        DYNAMIC_LINK, DEEP_LINK, MINIMUM_VERSION, CLICK_TIMESTAMP, extensions, updateAppUri);
  }
}
