// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.settings.network;

import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.ANDROID_CLIENT_TYPE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_CLIENT_TYPE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_CLIENT_VERSION;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_GOOGLE_APP_ID;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_ORG_ID;

import android.content.Context;
import android.util.Log;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.DeliveryMechanism;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.InspectableHttpRequest;
import com.google.firebase.crashlytics.internal.settings.model.AppRequestData;
import com.google.firebase.crashlytics.test.R;
import java.util.Map;

public class AppSpiCallTest extends CrashlyticsTestCase {
  private static final String TAG = "AppSpiCallTest";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  private static final String URL = "http://localhost:3000/spi/v1/platforms/android/apps";

  class TestAppSpiCall extends AbstractAppSpiCall {
    public TestAppSpiCall(Context context, HttpRequestFactory requestFactory) {
      super(null, URL, requestFactory, HttpMethod.GET, "1.0");
    }
  }

  public void testWebCall() throws Exception {
    final InspectableHttpRequest request = new InspectableHttpRequest();

    final AppSpiCall call =
        new TestAppSpiCall(
            getContext(),
            new HttpRequestFactory() {
              @Override
              public HttpRequest buildHttpRequest(
                  HttpMethod method, String url, Map<String, String> queryParams) {
                request.setUrl(url);
                request.setQueryParams(queryParams);
                return request;
              }
            });
    final String iconHash = "fakeHash";
    final int iconResourceId = R.drawable.ic_launcher;

    Log.d(TAG, "ICON::HASH=" + iconHash);
    Log.d(TAG, "ICON::RES=" + iconResourceId);

    final AppRequestData requestData =
        buildAppRequest(CommonUtils.createInstanceIdFrom("fake_build_id"));

    assertTrue(call.invoke(requestData, true));

    assertEquals(URL, request.getUrl());

    final Map<String, String> headers = request.getHeaders();
    assertEquals(requestData.organizationId, headers.get(HEADER_ORG_ID));
    assertEquals(requestData.googleAppId, headers.get(HEADER_GOOGLE_APP_ID));
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals("1.0", headers.get(HEADER_CLIENT_VERSION));

    final Map<String, Object> partsToValues = request.getMultipartValues();
    assertEquals(
        requestData.organizationId, partsToValues.get(AbstractAppSpiCall.ORGANIZATION_ID_PARAM));
    assertEquals(requestData.appId, partsToValues.get(AbstractAppSpiCall.APP_IDENTIFIER_PARAM));
    assertEquals(requestData.name, partsToValues.get(AbstractAppSpiCall.APP_NAME_PARAM));
    assertEquals(
        requestData.instanceIdentifier,
        partsToValues.get(AbstractAppSpiCall.APP_INSTANCE_IDENTIFIER_PARAM));
    assertEquals(
        requestData.displayVersion,
        partsToValues.get(AbstractAppSpiCall.APP_DISPLAY_VERSION_PARAM));
    assertEquals(
        requestData.buildVersion, partsToValues.get(AbstractAppSpiCall.APP_BUILD_VERSION_PARAM));
    assertEquals(
        Integer.toString(requestData.source),
        partsToValues.get(AbstractAppSpiCall.APP_SOURCE_PARAM));
    assertEquals(
        requestData.minSdkVersion, partsToValues.get(AbstractAppSpiCall.APP_MIN_SDK_VERSION_PARAM));
    assertEquals(
        requestData.builtSdkVersion,
        partsToValues.get(AbstractAppSpiCall.APP_BUILT_SDK_VERSION_PARAM));

    final Map<String, Object> multiPartData = request.getMultipartValues();

    /*

    TODO: Should we be testing stuff specifically for Onboarding and CrashlyticsCore here?

    for (KitInfo kit : sdkKits) {
      final String kitKey = ((TestAppSpiCall) call).getKitVersionKey(kit);

      final String kitVersion = kit.getVersion();
      assertTrue(multiPartData.containsKey(kitKey));
      assertEquals(kitVersion, multiPartData.get(kitKey));
    }

    for (KitInfo kit : sdkKits) {
      final String kitKey = ((TestAppSpiCall) call).getKitBuildTypeKey(kit);

      final String kitBuildType = kit.getBuildType();
      assertTrue(multiPartData.containsKey(kitKey));
      assertEquals(kitBuildType, multiPartData.get(kitKey));
    }
    */
  }

  public void testWebCallNoIcon() throws Exception {
    final InspectableHttpRequest request = new InspectableHttpRequest();

    final AppSpiCall call =
        new TestAppSpiCall(
            getContext(),
            new HttpRequestFactory() {
              @Override
              public HttpRequest buildHttpRequest(
                  HttpMethod method, String url, Map<String, String> queryParams) {
                request.setUrl(URL);
                request.setQueryParams(queryParams);
                return request;
              }
            });

    final AppRequestData requestData =
        buildAppRequest(CommonUtils.createInstanceIdFrom("fake_build_id"));

    assertTrue(call.invoke(requestData, true));

    assertEquals(URL, request.getUrl());

    final Map<String, String> headers = request.getHeaders();
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals("1.0", headers.get(HEADER_CLIENT_VERSION));

    final Map<String, Object> partsToValues = request.getMultipartValues();
    assertEquals(
        requestData.organizationId, partsToValues.get(AbstractAppSpiCall.ORGANIZATION_ID_PARAM));
    assertEquals(requestData.appId, partsToValues.get(AbstractAppSpiCall.APP_IDENTIFIER_PARAM));
    assertEquals(requestData.name, partsToValues.get(AbstractAppSpiCall.APP_NAME_PARAM));
    assertEquals(
        requestData.instanceIdentifier,
        partsToValues.get(AbstractAppSpiCall.APP_INSTANCE_IDENTIFIER_PARAM));
    assertEquals(
        requestData.displayVersion,
        partsToValues.get(AbstractAppSpiCall.APP_DISPLAY_VERSION_PARAM));
    assertEquals(
        requestData.buildVersion, partsToValues.get(AbstractAppSpiCall.APP_BUILD_VERSION_PARAM));
    assertEquals(
        Integer.toString(requestData.source),
        partsToValues.get(AbstractAppSpiCall.APP_SOURCE_PARAM));
    assertEquals(
        requestData.minSdkVersion, partsToValues.get(AbstractAppSpiCall.APP_MIN_SDK_VERSION_PARAM));
    assertEquals(
        requestData.builtSdkVersion,
        partsToValues.get(AbstractAppSpiCall.APP_BUILT_SDK_VERSION_PARAM));
  }

  public void testWebCallNoInstanceIdNoIcon() throws Exception {
    final InspectableHttpRequest request = new InspectableHttpRequest();

    final AppSpiCall call =
        new TestAppSpiCall(
            getContext(),
            new HttpRequestFactory() {
              @Override
              public HttpRequest buildHttpRequest(
                  HttpMethod method, String url, Map<String, String> queryParams) {
                request.setUrl(URL);
                request.setQueryParams(queryParams);
                return request;
              }
            });

    final AppRequestData requestData = buildAppRequest(null);

    assertTrue(call.invoke(requestData, true));

    assertEquals(URL, request.getUrl());

    final Map<String, String> headers = request.getHeaders();
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals("1.0", headers.get(HEADER_CLIENT_VERSION));

    final Map<String, Object> partsToValues = request.getMultipartValues();
    assertEquals(
        requestData.organizationId, partsToValues.get(AbstractAppSpiCall.ORGANIZATION_ID_PARAM));
    assertEquals(requestData.appId, partsToValues.get(AbstractAppSpiCall.APP_IDENTIFIER_PARAM));
    assertEquals(requestData.name, partsToValues.get(AbstractAppSpiCall.APP_NAME_PARAM));
    assertNull(partsToValues.get(AbstractAppSpiCall.APP_INSTANCE_IDENTIFIER_PARAM));
    assertEquals(
        requestData.displayVersion,
        partsToValues.get(AbstractAppSpiCall.APP_DISPLAY_VERSION_PARAM));
    assertEquals(
        requestData.buildVersion, partsToValues.get(AbstractAppSpiCall.APP_BUILD_VERSION_PARAM));
    assertEquals(
        Integer.toString(requestData.source),
        partsToValues.get(AbstractAppSpiCall.APP_SOURCE_PARAM));
    assertEquals(
        requestData.minSdkVersion, partsToValues.get(AbstractAppSpiCall.APP_MIN_SDK_VERSION_PARAM));
    assertEquals(
        requestData.builtSdkVersion,
        partsToValues.get(AbstractAppSpiCall.APP_BUILT_SDK_VERSION_PARAM));
  }

  private AppRequestData buildAppRequest(String instanceIdentifier) {
    final Context context = getContext();
    final String organizationId = "4da273c6277cc2463c000002";
    final String googleAppId = "1:12345678901:android:1234567890abcdefg";
    final String appId = "com.crashlytics.crashdroid1";
    final String packagename = context.getPackageName();

    final String installerPackageName =
        getContext().getPackageManager().getInstallerPackageName(packagename);

    final int source = DeliveryMechanism.determineFrom(installerPackageName).getId();

    return new AppRequestData(
        organizationId,
        googleAppId,
        appId,
        "1.0",
        "1",
        instanceIdentifier,
        "Crashdroid1",
        source,
        "7",
        "0");
  }
}
