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

package com.google.firebase.dynamiclinks;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.os.Bundle;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.dynamiclinks.DynamicLink.AndroidParameters;
import com.google.firebase.dynamiclinks.DynamicLink.Builder;
import com.google.firebase.dynamiclinks.DynamicLink.GoogleAnalyticsParameters;
import com.google.firebase.dynamiclinks.DynamicLink.IosParameters;
import com.google.firebase.dynamiclinks.DynamicLink.ItunesConnectAnalyticsParameters;
import com.google.firebase.dynamiclinks.DynamicLink.NavigationInfoParameters;
import com.google.firebase.dynamiclinks.DynamicLink.SocialMetaTagParameters;
import com.google.firebase.dynamiclinks.ShortDynamicLink.Suffix;
import com.google.firebase.dynamiclinks.internal.FirebaseDynamicLinksImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Test {@link com.google.firebase.dynamiclinks.DynamicLink}. */
@RunWith(RobolectricTestRunner.class)
public class DynamicLinkTest {

  private static final String DEEP_LINK = "https://deep.link";
  private static final String LONG_LINK = "https://long.link";
  private static final String DOMAIN_WITH_SCHEME = "https://test.app.goo.gl";
  private static final String DOMAIN = "test.app.goo.gl";
  private static final String CUSTOM_DOMAIN_URI_PREFIX = "https://custom.com/xyz";
  private static final String CUSTOM_DOMAIN_WITHOUT_SCHEME = "custom.com";
  private static final String PARAMETER = "parameter";
  private static final String VALUE = "value";
  private static final String API_KEY = "api_key";
  private static final String PACKAGE_NAME = "package.name";
  private static final String FALLBACK_LINK = "https://fallback.link";
  private static final int MIN_VERSION_CODE = 123;
  private static final String BUNDLE_ID = "bundleId";
  private static final String CUSTOM_SCHEME = "customScheme";
  private static final String IPAD_FALLBACK_LINK = "https://ipad.fallback.link";
  private static final String IPAD_BUNDLE_ID = "ipadBundleId";
  private static final String APP_STORE_ID = "appStoreId";
  private static final String MIN_VERSION = "minVersion";
  private static final String UTM_SOURCE = "utmSource";
  private static final String UTM_MEDIUM = "utmMedium";
  private static final String UTM_CAMPAIGN = "utmCampaign";
  private static final String UTM_TERM = "utmTerm";
  private static final String UTM_CONTENT = "utmContent";
  private static final String PROVIDER_TOKEN = "providerToken";
  private static final String AFFILIATE_TOKEN = "affiliateToken";
  private static final String CAMPAIGN_TOKEN = "campaignToken";
  private static final String SOCIAL_TITLE = "title";
  private static final String SOCIAL_DESCRIPTION = "description";
  private static final String SOCIAL_IMAGE_LINK = "imageLink";

  @Mock private FirebaseDynamicLinksImpl mockFDLImpl;
  @Captor private ArgumentCaptor<Bundle> bundleCaptor;

  private Builder builder;

  private static Bundle createDynamicLinkBundle() {
    Bundle builderParameters = new Bundle();
    builderParameters.putString(Builder.KEY_DOMAIN_URI_PREFIX, DOMAIN_WITH_SCHEME);
    Bundle fdlParameters = new Bundle();
    fdlParameters.putString(PARAMETER, VALUE);
    builderParameters.putBundle(Builder.KEY_DYNAMIC_LINK_PARAMETERS, fdlParameters);
    return builderParameters;
  }

  /** Compare FDL parameters as Strings since that's what is used when generating long FDLs. */
  private static void assertParameterEquals(Object expected, Object actual) {
    assertNotNull(actual);
    assertEquals(expected.toString(), actual.toString());
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();
    FirebaseOptions.Builder firebaseOptionsBuilder =
        new FirebaseOptions.Builder().setApplicationId("application_id").setApiKey(API_KEY);
    FirebaseApp.initializeApp(RuntimeEnvironment.application, firebaseOptionsBuilder.build());

    builder = new Builder(mockFDLImpl);
    builder.setDynamicLinkDomain(DOMAIN);
  }

  @Test
  public void testGetUri() {
    Bundle parameters = createDynamicLinkBundle();
    DynamicLink dynamicLink = new DynamicLink(parameters);
    assertEquals(FirebaseDynamicLinksImpl.createDynamicLink(parameters), dynamicLink.getUri());
  }

  @Test
  public void testBuilder_SetLongLink() {
    Uri longLink = Uri.parse(LONG_LINK);
    builder.setLongLink(longLink);
    Bundle parameters = getParameterBundle();
    assertEquals(longLink, parameters.getParcelable(Builder.KEY_DYNAMIC_LINK));
    assertEquals(API_KEY, parameters.getString(Builder.KEY_API_KEY));
  }

  @Test
  public void testBuilder_SetLink() {
    builder.setLink(Uri.parse(DEEP_LINK));
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(DEEP_LINK, fdlParameters.get(Builder.KEY_LINK));
  }

  @Test
  public void testBuilder_SetDynamicLinkDomain() {
    // Domain set to DOMAIN as part of setup().
    Bundle parameters = getParameterBundle();
    assertEquals(DOMAIN_WITH_SCHEME, parameters.getString(Builder.KEY_DOMAIN_URI_PREFIX));
  }

  @Test
  public void testBuilder_SetDomainUriPrefix() throws Exception {
    DynamicLink.Builder builder = FirebaseDynamicLinks.getInstance().createDynamicLink();
    builder.setDomainUriPrefix(CUSTOM_DOMAIN_URI_PREFIX);
    DynamicLink dynamicLink = builder.buildDynamicLink();
    assertEquals(CUSTOM_DOMAIN_URI_PREFIX, dynamicLink.getUri().toString());

    builder = FirebaseDynamicLinks.getInstance().createDynamicLink();
    builder.setDomainUriPrefix(DOMAIN_WITH_SCHEME);
    dynamicLink = builder.buildDynamicLink();
    assertEquals(DOMAIN_WITH_SCHEME, dynamicLink.getUri().toString());
  }

  @Test
  public void testBuilder_BuildDynamicLink() {
    Uri longLink = Uri.parse(LONG_LINK);
    builder.setLongLink(longLink);
    DynamicLink dynamicLink = builder.buildDynamicLink();
    assertEquals(longLink, dynamicLink.getUri());
  }

  @Test
  public void testBuilder_BuildShortDynamicLink_WithSuffix() {
    builder.buildShortDynamicLink(Suffix.SHORT);
    verify(mockFDLImpl).createShortDynamicLink(bundleCaptor.capture());
    Bundle parameters = bundleCaptor.getValue();
    assertEquals(Suffix.SHORT, parameters.getInt(Builder.KEY_SUFFIX));
  }

  @Test
  public void testAndroidParameters_Builder_Constructor() {
    AndroidParameters.Builder androidBuilder = new AndroidParameters.Builder();
    builder.setAndroidParameters(androidBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        RuntimeEnvironment.application.getPackageName(),
        fdlParameters.get(AndroidParameters.KEY_ANDROID_PACKAGE_NAME));
  }

  @Test
  public void testAndroidParameters_Builder_Constructor_PackageName() {
    AndroidParameters.Builder androidBuilder = new AndroidParameters.Builder(PACKAGE_NAME);
    builder.setAndroidParameters(androidBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        PACKAGE_NAME, fdlParameters.get(AndroidParameters.KEY_ANDROID_PACKAGE_NAME));
  }

  @Test
  public void testAndroidParameters_Builder_SetFallbackLink() {
    AndroidParameters.Builder androidBuilder = new AndroidParameters.Builder();
    androidBuilder.setFallbackUrl(Uri.parse(FALLBACK_LINK));
    builder.setAndroidParameters(androidBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        FALLBACK_LINK, fdlParameters.get(AndroidParameters.KEY_ANDROID_FALLBACK_LINK));
  }

  @Test
  public void testAndroidParameters_Builder_SetMinimumVersion() {
    AndroidParameters.Builder androidBuilder = new AndroidParameters.Builder();
    androidBuilder.setMinimumVersion(MIN_VERSION_CODE);
    builder.setAndroidParameters(androidBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        MIN_VERSION_CODE, fdlParameters.get(AndroidParameters.KEY_ANDROID_MIN_VERSION_CODE));
  }

  @Test
  public void testIosParameters_Builder_Constructor() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(BUNDLE_ID, fdlParameters.get(IosParameters.KEY_IOS_BUNDLE_ID));
  }

  @Test
  public void testIosParameters_Builder_SetFallbackLink() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    iosBuilder.setFallbackUrl(Uri.parse(FALLBACK_LINK));
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(FALLBACK_LINK, fdlParameters.get(IosParameters.KEY_IOS_FALLBACK_LINK));
  }

  @Test
  public void testIosParameters_Builder_SetCustomScheme() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    iosBuilder.setCustomScheme(CUSTOM_SCHEME);
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(CUSTOM_SCHEME, fdlParameters.get(IosParameters.KEY_IOS_CUSTOM_SCHEME));
  }

  @Test
  public void testIosParameters_Builder_SetIpadFallbackLink() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    iosBuilder.setIpadFallbackUrl(Uri.parse(IPAD_FALLBACK_LINK));
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        IPAD_FALLBACK_LINK, fdlParameters.get(IosParameters.KEY_IPAD_FALLBACK_LINK));
  }

  @Test
  public void testIosParameters_Builder_SetIpadBundleId() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    iosBuilder.setIpadBundleId(IPAD_BUNDLE_ID);
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(IPAD_BUNDLE_ID, fdlParameters.get(IosParameters.KEY_IPAD_BUNDLE_ID));
  }

  @Test
  public void testIosParameters_Builder_SetAppStoreId() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    iosBuilder.setAppStoreId(APP_STORE_ID);
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(APP_STORE_ID, fdlParameters.get(IosParameters.KEY_IOS_APP_STORE_ID));
  }

  @Test
  public void testIosParameters_Builder_SetMinimumVersion() {
    IosParameters.Builder iosBuilder = new IosParameters.Builder(BUNDLE_ID);
    iosBuilder.setMinimumVersion(MIN_VERSION);
    builder.setIosParameters(iosBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(MIN_VERSION, fdlParameters.get(IosParameters.KEY_IOS_MINIMUM_VERSION));
  }

  @Test
  public void testGoogleAnalyticsParameters_Builder_Constructor() {
    GoogleAnalyticsParameters.Builder analyticsBuilder =
        new GoogleAnalyticsParameters.Builder(UTM_SOURCE, UTM_MEDIUM, UTM_CAMPAIGN);
    builder.setGoogleAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(UTM_SOURCE, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_SOURCE));
    assertParameterEquals(UTM_MEDIUM, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_MEDIUM));
    assertParameterEquals(
        UTM_CAMPAIGN, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_CAMPAIGN));
  }

  @Test
  public void testGoogleAnalyticsParameters_Builder_SetSource() {
    GoogleAnalyticsParameters.Builder analyticsBuilder = new GoogleAnalyticsParameters.Builder();
    analyticsBuilder.setSource(UTM_SOURCE);
    builder.setGoogleAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(UTM_SOURCE, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_SOURCE));
  }

  @Test
  public void testGoogleAnalyticsParameters_Builder_SetMedium() {
    GoogleAnalyticsParameters.Builder analyticsBuilder = new GoogleAnalyticsParameters.Builder();
    analyticsBuilder.setMedium(UTM_MEDIUM);
    builder.setGoogleAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(UTM_MEDIUM, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_MEDIUM));
  }

  @Test
  public void testGoogleAnalyticsParameters_Builder_SetCampaign() {
    GoogleAnalyticsParameters.Builder analyticsBuilder = new GoogleAnalyticsParameters.Builder();
    analyticsBuilder.setCampaign(UTM_CAMPAIGN);
    builder.setGoogleAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        UTM_CAMPAIGN, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_CAMPAIGN));
  }

  @Test
  public void testGoogleAnalyticsParameters_Builder_SetTerm() {
    GoogleAnalyticsParameters.Builder analyticsBuilder =
        new GoogleAnalyticsParameters.Builder(UTM_SOURCE, UTM_MEDIUM, UTM_CAMPAIGN);
    analyticsBuilder.setTerm(UTM_TERM);
    builder.setGoogleAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(UTM_TERM, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_TERM));
  }

  @Test
  public void testGoogleAnalyticsParameters_Builder_SetContent() {
    GoogleAnalyticsParameters.Builder analyticsBuilder =
        new GoogleAnalyticsParameters.Builder(UTM_SOURCE, UTM_MEDIUM, UTM_CAMPAIGN);
    analyticsBuilder.setContent(UTM_CONTENT);
    builder.setGoogleAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        UTM_CONTENT, fdlParameters.get(GoogleAnalyticsParameters.KEY_UTM_CONTENT));
  }

  @Test
  public void testItunesConnectAnalyticsParameters_Builder_SetProviderToken() {
    ItunesConnectAnalyticsParameters.Builder analyticsBuilder =
        new ItunesConnectAnalyticsParameters.Builder();
    analyticsBuilder.setProviderToken(PROVIDER_TOKEN);
    builder.setItunesConnectAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        PROVIDER_TOKEN, fdlParameters.get(ItunesConnectAnalyticsParameters.KEY_ITUNES_CONNECT_PT));
  }

  @Test
  public void testItunesConnectAnalyticsParameters_Builder_SetAffiliateToken() {
    ItunesConnectAnalyticsParameters.Builder analyticsBuilder =
        new ItunesConnectAnalyticsParameters.Builder();
    analyticsBuilder.setAffiliateToken(AFFILIATE_TOKEN);
    builder.setItunesConnectAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        AFFILIATE_TOKEN, fdlParameters.get(ItunesConnectAnalyticsParameters.KEY_ITUNES_CONNECT_AT));
  }

  @Test
  public void testItunesConnectAnalyticsParameters_Builder_SetCampaignToken() {
    ItunesConnectAnalyticsParameters.Builder analyticsBuilder =
        new ItunesConnectAnalyticsParameters.Builder();
    analyticsBuilder.setCampaignToken(CAMPAIGN_TOKEN);
    builder.setItunesConnectAnalyticsParameters(analyticsBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        CAMPAIGN_TOKEN, fdlParameters.get(ItunesConnectAnalyticsParameters.KEY_ITUNES_CONNECT_CT));
  }

  @Test
  public void testSocialMetaTagParameters_Builder_SetTitle() {
    SocialMetaTagParameters.Builder socialBuilder = new SocialMetaTagParameters.Builder();
    socialBuilder.setTitle(SOCIAL_TITLE);
    builder.setSocialMetaTagParameters(socialBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        SOCIAL_TITLE, fdlParameters.get(SocialMetaTagParameters.KEY_SOCIAL_TITLE));
  }

  @Test
  public void testSocialMetaTagParameters_Builder_SetDescription() {
    SocialMetaTagParameters.Builder socialBuilder = new SocialMetaTagParameters.Builder();
    socialBuilder.setDescription(SOCIAL_DESCRIPTION);
    builder.setSocialMetaTagParameters(socialBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        SOCIAL_DESCRIPTION, fdlParameters.get(SocialMetaTagParameters.KEY_SOCIAL_DESCRIPTION));
  }

  @Test
  public void testSocialMetaTagParameters_Builder_SetImageLink() {
    SocialMetaTagParameters.Builder socialBuilder = new SocialMetaTagParameters.Builder();
    socialBuilder.setImageUrl(Uri.parse(SOCIAL_IMAGE_LINK));
    builder.setSocialMetaTagParameters(socialBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    assertParameterEquals(
        SOCIAL_IMAGE_LINK, fdlParameters.get(SocialMetaTagParameters.KEY_SOCIAL_IMAGE_LINK));
  }

  @Test
  public void testNavigationInfoParameters_Builder_ForcedRedirectNotSet() {
    NavigationInfoParameters.Builder navigationBuilder = new NavigationInfoParameters.Builder();
    builder.setNavigationInfoParameters(navigationBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    // Should not be set to "1" when not set.
    assertFalse("1".equals(fdlParameters.get(NavigationInfoParameters.KEY_FORCED_REDIRECT)));
  }

  @Test
  public void testNavigationInfoParameters_Builder_SetForcedRedirectEnabled() {
    NavigationInfoParameters.Builder navigationBuilder = new NavigationInfoParameters.Builder();
    navigationBuilder.setForcedRedirectEnabled(true);
    builder.setNavigationInfoParameters(navigationBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    // Should be set to "1" when enabled.
    assertParameterEquals("1", fdlParameters.get(NavigationInfoParameters.KEY_FORCED_REDIRECT));
  }

  @Test
  public void testNavigationInfoParameters_Builder_SetForcedRedirectEnabled_False() {
    NavigationInfoParameters.Builder navigationBuilder = new NavigationInfoParameters.Builder();
    navigationBuilder.setForcedRedirectEnabled(false);
    builder.setNavigationInfoParameters(navigationBuilder.build());
    Bundle fdlParameters = getFdlParameterBundle();
    // Should not be set to "1" when not enabled.
    assertFalse("1".equals(fdlParameters.get(NavigationInfoParameters.KEY_FORCED_REDIRECT)));
  }

  /** Gets the Bundle that contains the options used to create the short Dynamic Link. */
  private Bundle getParameterBundle() {
    builder.buildShortDynamicLink();
    verify(mockFDLImpl).createShortDynamicLink(bundleCaptor.capture());
    return bundleCaptor.getValue();
  }

  /** Gets the Bundle that contains the parameters to build the long Dynamic Link to shorten. */
  private Bundle getFdlParameterBundle() {
    return getParameterBundle().getBundle(Builder.KEY_DYNAMIC_LINK_PARAMETERS);
  }
}
