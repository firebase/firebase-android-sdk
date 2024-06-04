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

package com.google.firebase.dynamiclinks;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.dynamiclinks.ShortDynamicLink.Suffix;
import com.google.firebase.dynamiclinks.internal.FirebaseDynamicLinksImpl;

/**
 * Contains Builders for constructing Dynamic Links. Returned by {@link Builder#buildDynamicLink()}
 * with the constructed Dynamic Link.
 *
 * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
 *   service will shut down on August 25, 2025. For more information, see
 *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
 */
@Deprecated
public final class DynamicLink {

  private final Bundle builderParameters;

  DynamicLink(Bundle builderParameters) {
    this.builderParameters = builderParameters;
  }

  /**
   * Gets the Uri for this Dynamic Link.
   *
   * @throws IllegalArgumentException if the FDL domain is not set. Set with {@link
   *     Builder#setDynamicLinkDomain(String)}.
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @NonNull
  @Deprecated
  public Uri getUri() {
    return FirebaseDynamicLinksImpl.createDynamicLink(builderParameters);
  }

  /**
   * Builder for creating Dynamic Links.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   * */
  @Deprecated
  public static final class Builder {

    // Dynamic Link parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_DOMAIN = "domain";
    /** @hide */
    // TODO(b/120887495): This @VisibleForTesting annotation was being ignored by prod code.
    // Please check that removing it is correct, and remove this comment along with it.
    // @VisibleForTesting
    public static final String KEY_DOMAIN_URI_PREFIX = "domainUriPrefix";
    /** @hide */
    // TODO(b/120887495): This @VisibleForTesting annotation was being ignored by prod code.
    // Please check that removing it is correct, and remove this comment along with it.
    // @VisibleForTesting
    public static final String KEY_DYNAMIC_LINK = "dynamicLink";
    /** @hide */
    // TODO(b/120887495): This @VisibleForTesting annotation was being ignored by prod code.
    // Please check that removing it is correct, and remove this comment along with it.
    // @VisibleForTesting
    public static final String KEY_DYNAMIC_LINK_PARAMETERS = "parameters";
    /** @hide */
    // TODO(b/120887495): This @VisibleForTesting annotation was being ignored by prod code.
    // Please check that removing it is correct, and remove this comment along with it.
    // @VisibleForTesting
    public static final String KEY_SUFFIX = "suffix";
    /** @hide */
    // TODO(b/120887495): This @VisibleForTesting annotation was being ignored by prod code.
    // Please check that removing it is correct, and remove this comment along with it.
    // @VisibleForTesting
    public static final String KEY_API_KEY = "apiKey";

    /** @hide */
    @VisibleForTesting public static final String KEY_LINK = "link";

    private static final String SCHEME_PREFIX = "https://";
    private static final String PAGE_LINK_PATTERN = "(https:\\/\\/)?[a-z0-9]{3,}\\.page\\.link$";
    private static final String APP_GOO_GL_PATTERN =
        "(https:\\/\\/)?[a-z0-9]{3,}\\.app\\.goo\\.gl$";

    private final FirebaseDynamicLinksImpl firebaseDynamicLinksImpl;
    private final Bundle builderParameters;
    private final Bundle fdlParameters;

    /** @hide */
    public Builder(FirebaseDynamicLinksImpl firebaseDynamicLinks) {
      firebaseDynamicLinksImpl = firebaseDynamicLinks;
      builderParameters = new Bundle();
      builderParameters.putString(
          KEY_API_KEY, firebaseDynamicLinks.getFirebaseApp().getOptions().getApiKey());
      fdlParameters = new Bundle();
      builderParameters.putBundle(KEY_DYNAMIC_LINK_PARAMETERS, fdlParameters);
    }

    /**
     * Set the long Dynamic Link. This can be used with {@link #buildShortDynamicLink} to shorten an
     * existing long FDL into a short FDL.
     *
     * @param longLink The long FDL to shorten.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setLongLink(@NonNull Uri longLink) {
      builderParameters.putParcelable(KEY_DYNAMIC_LINK, longLink);
      return this;
    }

    /**
     * Return the long Dynamic link associated with this DynamicLink.
     *
     * @return the long Dynamic link associated with this DynamicLink.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Uri getLongLink() {
      Uri longLink = fdlParameters.getParcelable(KEY_DYNAMIC_LINK);
      if (longLink == null) {
        longLink = Uri.EMPTY;
      }
      return longLink;
    }

    /**
     * Set the deep link.
     *
     * @param link The link your app will open. You can specify any URL your app can handle, such as
     *     a link to your app's content, or a URL that initiates some app-specific logic such as
     *     crediting the user with a coupon, or displaying a specific welcome screen. This link must
     *     be a well-formatted URL, be properly URL-encoded, and use the HTTP or HTTPS scheme.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setLink(@NonNull Uri link) {
      fdlParameters.putParcelable(KEY_LINK, link);
      return this;
    }

    /**
     * Return the deep link associated with this DynamicLink.
     *
     * @return the deep link associated with this DynamicLink.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Uri getLink() {
      Uri link = fdlParameters.getParcelable(KEY_LINK);
      if (link == null) {
        link = Uri.EMPTY;
      }
      return link;
    }

    /**
     * Sets the domain (of the form "xyz.app.goo.gl") to use for this Dynamic Link. Only applicable
     * for *.page.link and *.app.goo.gl, use {@link #setDomainUriPrefix(String)} if domain is
     * custom.
     *
     * @param dynamicLinkDomain The target project's Dynamic Links domain. You can find this value
     *     in the Dynamic Links section of the Firebase console.
     * @deprecated Use {@link #setDomainUriPrefix(String)} instead
     */
    @NonNull
    @Deprecated
    public Builder setDynamicLinkDomain(@NonNull String dynamicLinkDomain) {
      if (!dynamicLinkDomain.matches(APP_GOO_GL_PATTERN)
          && !dynamicLinkDomain.matches(PAGE_LINK_PATTERN)) {
        throw new IllegalArgumentException(
            "Use setDomainUriPrefix() instead, setDynamicLinkDomain() is only applicable for "
                + "*.page.link and *.app.goo.gl domains.");
      }
      builderParameters.putString(KEY_DOMAIN, dynamicLinkDomain);
      builderParameters.putString(KEY_DOMAIN_URI_PREFIX, SCHEME_PREFIX + dynamicLinkDomain);
      return this;
    }

    /**
     * Sets the domain uri prefix (of the form "https://xyz.app.goo.gl", "https://custom.com/xyz")
     * to use for this Dynamic Link.
     *
     * @param domainUriPrefix The target project's Domain Uri Prefix. You can find this value in the
     *     Dynamic Links section of the Firebase console.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setDomainUriPrefix(@NonNull String domainUriPrefix) {
      if (domainUriPrefix.matches(APP_GOO_GL_PATTERN)
          || domainUriPrefix.matches(PAGE_LINK_PATTERN)) {
        builderParameters.putString(KEY_DOMAIN, domainUriPrefix.replace(SCHEME_PREFIX, ""));
      }
      builderParameters.putString(KEY_DOMAIN_URI_PREFIX, domainUriPrefix);
      return this;
    }

    /**
     * Returns the deep link set to this DynamicLink.
     *
     * @return the deep link set to this DynamicLink.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public String getDomainUriPrefix() {
      return builderParameters.getString(KEY_DOMAIN_URI_PREFIX, "");
    }

    /**
     * Sets the Android parameters.
     *
     * @param androidParameters The AndroidParameters from {@link
     *     AndroidParameters.Builder#build()}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setAndroidParameters(@NonNull AndroidParameters androidParameters) {
      fdlParameters.putAll(androidParameters.parameters);
      return this;
    }

    /**
     * Sets the iOS parameters.
     *
     * @param iosParameters The IosParameters from {@link IosParameters.Builder#build()}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setIosParameters(@NonNull IosParameters iosParameters) {
      fdlParameters.putAll(iosParameters.parameters);
      return this;
    }

    /**
     * Sets the Google Analytics parameters.
     *
     * @param googleAnalyticsParameters The GoogleAnalyticsParameters from {@link
     *     GoogleAnalyticsParameters.Builder#build()}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setGoogleAnalyticsParameters(
        @NonNull GoogleAnalyticsParameters googleAnalyticsParameters) {
      fdlParameters.putAll(googleAnalyticsParameters.parameters);
      return this;
    }

    /**
     * Sets the iTunes Connect App Analytics parameters.
     *
     * @param itunesConnectAnalyticsParameters The ItunesConnectAnalyticsParameters from {@link
     *     ItunesConnectAnalyticsParameters.Builder#build()}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setItunesConnectAnalyticsParameters(
        @NonNull ItunesConnectAnalyticsParameters itunesConnectAnalyticsParameters) {
      fdlParameters.putAll(itunesConnectAnalyticsParameters.parameters);
      return this;
    }

    /**
     * Sets the social meta-tag parameters.
     *
     * @param socialMetaTagParameters The SocialMetaTagParameters from {@link
     *     SocialMetaTagParameters.Builder#build()}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setSocialMetaTagParameters(
        @NonNull SocialMetaTagParameters socialMetaTagParameters) {
      fdlParameters.putAll(socialMetaTagParameters.parameters);
      return this;
    }

    /**
     * Sets the navigation info parameters.
     *
     * @param navigationInfoParameters The NavigationInfoParameters from {@link
     *     NavigationInfoParameters.Builder#build()}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Builder setNavigationInfoParameters(
        @NonNull NavigationInfoParameters navigationInfoParameters) {
      fdlParameters.putAll(navigationInfoParameters.parameters);
      return this;
    }

    /**
     * Creates a Dynamic Link from the parameters.
     *
     * @throws IllegalArgumentException if the FDL domain is not set. Set with {@link
     *     #setDynamicLinkDomain(String)}.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public DynamicLink buildDynamicLink() {
      FirebaseDynamicLinksImpl.verifyDomainUriPrefix(builderParameters);
      return new DynamicLink(builderParameters);
    }

    /**
     * Creates a shortened Dynamic Link from the parameters.
     *
     * @throws IllegalArgumentException if the FDL domain and api key are not set. Set FDL domain
     *     with {@link Builder#setDynamicLinkDomain(String)}. Ensure that google-services.json file
     *     is setup for the app if the api key is not set.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Task<ShortDynamicLink> buildShortDynamicLink() {
      verifyApiKey();
      return firebaseDynamicLinksImpl.createShortDynamicLink(builderParameters);
    }

    /**
     * Creates a shortened Dynamic Link from the parameters.
     *
     * @param suffix The desired length of the Dynamic Link. One of {@link Suffix#UNGUESSABLE} or
     *     {@link Suffix#SHORT}.
     * @throws IllegalArgumentException if the FDL domain and api key are not set. Set FDL domain
     *     with {@link Builder#setDynamicLinkDomain(String)}. Ensure that google-services.json file
     *     is setup for the app if the api key is not set.
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @NonNull
    @Deprecated
    public Task<ShortDynamicLink> buildShortDynamicLink(@Suffix int suffix) {
      verifyApiKey();
      builderParameters.putInt(Builder.KEY_SUFFIX, suffix);
      return firebaseDynamicLinksImpl.createShortDynamicLink(builderParameters);
    }

    private void verifyApiKey() {
      if (builderParameters.getString(KEY_API_KEY) == null) {
        throw new IllegalArgumentException("Missing API key. Set with setApiKey().");
      }
    }
  }

  /**
   * Android parameters.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @Deprecated
  public static final class AndroidParameters {

    // AndroidInfo parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_ANDROID_PACKAGE_NAME = "apn";
    /** @hide */
    @VisibleForTesting public static final String KEY_ANDROID_FALLBACK_LINK = "afl";
    /** @hide */
    @VisibleForTesting public static final String KEY_ANDROID_MIN_VERSION_CODE = "amv";

    final Bundle parameters;

    private AndroidParameters(Bundle parameters) {
      this.parameters = parameters;
    }

    /**
     * Builder for Android parameters.
     *
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @Deprecated
    public static final class Builder {

      private final Bundle parameters;

      /**
       * Create Android parameters builder, using the package name of the calling app. The app must
       * be connected to your project from the Overview page of the Firebase console.
       *
       * @throws IllegalStateException if FirebaseApp has not been initialized correctly.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder() {
        if (FirebaseApp.getInstance() == null) {
          throw new IllegalStateException("FirebaseApp not initialized.");
        }
        parameters = new Bundle();
        parameters.putString(
            KEY_ANDROID_PACKAGE_NAME,
            FirebaseApp.getInstance().getApplicationContext().getPackageName());
      }

      /**
       * Create Android parameters builder.
       *
       * @param packageName The package name of the Android app to use to open the link. The app
       *     must be connected to your project from the Overview page of the Firebase console.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder(@NonNull String packageName) {
        parameters = new Bundle();
        parameters.putString(KEY_ANDROID_PACKAGE_NAME, packageName);
      }

      /**
       * Sets the link to open when the app isn't installed. Specify this to do something other than
       * install your app from the Play Store when the app isn't installed, such as open the mobile
       * web version of the content, or display a promotional page for your app.
       *
       * @param fallbackUrl The link to open on Android if the app is not installed.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public AndroidParameters.Builder setFallbackUrl(@NonNull Uri fallbackUrl) {
        parameters.putParcelable(KEY_ANDROID_FALLBACK_LINK, fallbackUrl);
        return this;
      }

      /**
       * Returns the link to open on Android if the app isn't installed.
       *
       * @return the link to open on Android if the app isn't installed.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public Uri getFallbackUrl() {
        Uri fallbackUrl = parameters.getParcelable(KEY_ANDROID_FALLBACK_LINK);
        if (fallbackUrl == null) {
          fallbackUrl = Uri.EMPTY;
        }
        return fallbackUrl;
      }

      /**
       * Sets the versionCode of the minimum version of your app that can open the link.
       *
       * @param minimumVersion The minimum version.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public AndroidParameters.Builder setMinimumVersion(int minimumVersion) {
        parameters.putInt(KEY_ANDROID_MIN_VERSION_CODE, minimumVersion);
        return this;
      }

      /**
       * Returns the minimum version of your app that can open the link.
       *
       * @return the minimum version of your app that can open the link.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public int getMinimumVersion() {
        return parameters.getInt(KEY_ANDROID_MIN_VERSION_CODE);
      }

      /**
       * Build AndroidParameters for use with {@link
       * DynamicLink.Builder#setAndroidParameters(AndroidParameters)}.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public AndroidParameters build() {
        return new AndroidParameters(parameters);
      }
    }
  }

  /**
   * iOS parameters.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @Deprecated
  public static final class IosParameters {

    // IosInfo parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_IOS_BUNDLE_ID = "ibi";
    /** @hide */
    @VisibleForTesting public static final String KEY_IOS_FALLBACK_LINK = "ifl";
    /** @hide */
    @VisibleForTesting public static final String KEY_IOS_CUSTOM_SCHEME = "ius";
    /** @hide */
    @VisibleForTesting public static final String KEY_IPAD_FALLBACK_LINK = "ipfl";
    /** @hide */
    @VisibleForTesting public static final String KEY_IPAD_BUNDLE_ID = "ipbi";
    /** @hide */
    @VisibleForTesting public static final String KEY_IOS_APP_STORE_ID = "isi";
    /** @hide */
    @VisibleForTesting public static final String KEY_IOS_MINIMUM_VERSION = "imv";

    final Bundle parameters;

    private IosParameters(Bundle parameters) {
      this.parameters = parameters;
    }

    /**
     * Builder for iOS parameters.
     *
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @Deprecated
    public static final class Builder {

      private final Bundle parameters;

      /**
       * Create iOS parameters builder.
       *
       * @param bundleId The bundle ID of the iOS app to use to open the link. The app must be
       *     connected to your project from the Overview page of the Firebase console.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder(@NonNull String bundleId) {
        parameters = new Bundle();
        parameters.putString(KEY_IOS_BUNDLE_ID, bundleId);
      }

      /**
       * Sets the link to open when the app isn't installed. Specify this to do something other than
       * install your app from the App Store when the app isn't installed, such as open the mobile
       * web version of the content, or display a promotional page for your app.
       *
       * @param fallbackUrl The link to open on iOS if the app is not installed.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters.Builder setFallbackUrl(@NonNull Uri fallbackUrl) {
        parameters.putParcelable(KEY_IOS_FALLBACK_LINK, fallbackUrl);
        return this;
      }

      /**
       * Sets the app's custom URL scheme, if defined to be something other than your app's bundle
       * ID.
       *
       * @param customScheme The app's custom URL scheme.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters.Builder setCustomScheme(@NonNull String customScheme) {
        parameters.putString(KEY_IOS_CUSTOM_SCHEME, customScheme);
        return this;
      }

      /**
       * Returns the app's custom URL scheme.
       *
       * @return the app's custom URL scheme.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getCustomScheme() {
        return parameters.getString(KEY_IOS_CUSTOM_SCHEME, "");
      }

      /**
       * Sets the link to open on iPads when the app isn't installed. Specify this to do something
       * other than install your app from the App Store when the app isn't installed, such as open
       * the web version of the content, or display a promotional page for your app. Overrides the
       * fallback link set by {@link IosParameters.Builder#setFallbackUrl(Uri)} on iPad.
       *
       * @param fallbackUrl The link to open on iPad if the app is not installed.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters.Builder setIpadFallbackUrl(@NonNull Uri fallbackUrl) {
        parameters.putParcelable(KEY_IPAD_FALLBACK_LINK, fallbackUrl);
        return this;
      }

      /**
       * Returns the link to open on iPad if the app is not installed.
       *
       * @return the link to open on iPad if the app is not installed.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public Uri getIpadFallbackUrl() {
        Uri fallbackUrl = parameters.getParcelable(KEY_IPAD_FALLBACK_LINK);
        if (fallbackUrl == null) {
          fallbackUrl = Uri.EMPTY;
        }
        return fallbackUrl;
      }

      /**
       * Sets the bundle ID of the iOS app to use on iPads to open the link. The app must be
       * connected to your project from the Overview page of the Firebase console.
       *
       * @param bundleId The iPad bundle ID of the app.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters.Builder setIpadBundleId(@NonNull String bundleId) {
        parameters.putString(KEY_IPAD_BUNDLE_ID, bundleId);
        return this;
      }

      /**
       * Returns the iPad bundle ID of the app.
       *
       * @return the iPad bundle ID of the app.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getIpadBundleId() {
        return parameters.getString(KEY_IPAD_BUNDLE_ID, "");
      }

      /**
       * Sets the App Store ID, used to send users to the App Store when the app isn't installed.
       *
       * @param appStoreId The App Store ID.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters.Builder setAppStoreId(@NonNull String appStoreId) {
        parameters.putString(KEY_IOS_APP_STORE_ID, appStoreId);
        return this;
      }

      /**
       * Returns the App Store ID, used to send users to the App Store when the app isn't installed
       *
       * @return the App Store ID, used to send users to the App Store when the app isn't installed.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       d.
       */
      @NonNull
      @Deprecated
      public String getAppStoreId() {
        return parameters.getString(KEY_IOS_APP_STORE_ID, "");
      }

      /**
       * Sets the minimum version of your app that can open the link.
       *
       * @param minimumVersion The minimum version.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters.Builder setMinimumVersion(@NonNull String minimumVersion) {
        parameters.putString(KEY_IOS_MINIMUM_VERSION, minimumVersion);
        return this;
      }

      /**
       * Returns the minimum version of your app that can open the link.
       *
       * @return the minimum version of your app that can open the link.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getMinimumVersion() {
        return parameters.getString(KEY_IOS_MINIMUM_VERSION, "");
      }

      /**
       * Build IosParameters for use with {@link
       * DynamicLink.Builder#setIosParameters(IosParameters)}.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public IosParameters build() {
        return new IosParameters(parameters);
      }
    }
  }

  /**
   * Google Analytics parameters.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @Deprecated
  public static final class GoogleAnalyticsParameters {

    // GooglePlayAnalytics parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_UTM_CAMPAIGN = "utm_campaign";
    /** @hide */
    @VisibleForTesting public static final String KEY_UTM_SOURCE = "utm_source";
    /** @hide */
    @VisibleForTesting public static final String KEY_UTM_MEDIUM = "utm_medium";
    /** @hide */
    @VisibleForTesting public static final String KEY_UTM_TERM = "utm_term";
    /** @hide */
    @VisibleForTesting public static final String KEY_UTM_CONTENT = "utm_content";

    Bundle parameters;

    private GoogleAnalyticsParameters(Bundle parameters) {
      this.parameters = parameters;
    }

    /**
     * Builder for Google Analytics parameters.
     *
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @Deprecated
    public static final class Builder {

      private final Bundle parameters;

      /**
       * Create Google Analytics parameters builder.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder() {
        parameters = new Bundle();
      }

      /**
       * Create Google Analytics parameters builder.
       *
       * @param source The campaign source; used to identify a search engine, newsletter, or other
       *     source.
       * @param medium The campaign medium; used to identify a medium such as email or
       *     cost-per-click (cpc).
       * @param campaign The campaign name; The individual campaign name, slogan, promo code, etc.
       *     for a product.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder(@NonNull String source, @NonNull String medium, @NonNull String campaign) {
        parameters = new Bundle();
        parameters.putString(KEY_UTM_SOURCE, source);
        parameters.putString(KEY_UTM_MEDIUM, medium);
        parameters.putString(KEY_UTM_CAMPAIGN, campaign);
      }

      /**
       * Sets the campaign source.
       *
       * @param source The campaign source; used to identify a search engine, newsletter, or other
       *     source.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public GoogleAnalyticsParameters.Builder setSource(@NonNull String source) {
        parameters.putString(KEY_UTM_SOURCE, source);
        return this;
      }

      /**
       * Returns the campaign source.
       *
       * @return the campaign source
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getSource() {
        return parameters.getString(KEY_UTM_SOURCE, "");
      }

      /**
       * Sets the campaign medium.
       *
       * @param medium The campaign medium; used to identify a medium such as email or
       *     cost-per-click (cpc).
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public GoogleAnalyticsParameters.Builder setMedium(@NonNull String medium) {
        parameters.putString(KEY_UTM_MEDIUM, medium);
        return this;
      }

      /**
       * Returns the campaign medium.
       *
       * @return the campaign medium.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getMedium() {
        return parameters.getString(KEY_UTM_MEDIUM, "");
      }

      /**
       * Sets the campaign name.
       *
       * @param campaign The campaign name; The individual campaign name, slogan, promo code, etc.
       *     for a product.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public GoogleAnalyticsParameters.Builder setCampaign(@NonNull String campaign) {
        parameters.putString(KEY_UTM_CAMPAIGN, campaign);
        return this;
      }

      /**
       * Returns the campaign name.
       *
       * @return the campaign name.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getCampaign() {
        return parameters.getString(KEY_UTM_CAMPAIGN, "");
      }

      /**
       * Sets the campaign term.
       *
       * @param term The campaign term; used with paid search to supply the keywords for ads.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public GoogleAnalyticsParameters.Builder setTerm(@NonNull String term) {
        parameters.putString(KEY_UTM_TERM, term);
        return this;
      }

      /**
       * Returns the campaign term.
       *
       * @return the campaign term.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getTerm() {
        return parameters.getString(KEY_UTM_TERM, "");
      }

      /**
       * Sets the campaign content.
       *
       * @param content The campaign content; used for A/B testing and content-targeted ads to
       *     differentiate ads or links that point to the same URL.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public GoogleAnalyticsParameters.Builder setContent(@NonNull String content) {
        parameters.putString(KEY_UTM_CONTENT, content);
        return this;
      }

      /**
       * Returns the campaign content.
       *
       * @return the campaign content.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getContent() {
        return parameters.getString(KEY_UTM_CONTENT, "");
      }

      /**
       * Build GoogleAnalyticsParameters for use with {@link
       * DynamicLink.Builder#setGoogleAnalyticsParameters(GoogleAnalyticsParameters)}.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public GoogleAnalyticsParameters build() {
        return new GoogleAnalyticsParameters(parameters);
      }
    }
  }

  /**
   * iTunes Connect App Analytics parameters.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @Deprecated
  public static final class ItunesConnectAnalyticsParameters {

    // ITunesConnectAnalytics parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_ITUNES_CONNECT_PT = "pt";
    /** @hide */
    @VisibleForTesting public static final String KEY_ITUNES_CONNECT_AT = "at";
    /** @hide */
    @VisibleForTesting public static final String KEY_ITUNES_CONNECT_CT = "ct";

    final Bundle parameters;

    private ItunesConnectAnalyticsParameters(Bundle parameters) {
      this.parameters = parameters;
    }

    /**
     * Builder for iTunes Connect App Analytics parameters.
     *
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @Deprecated
    public static final class Builder {

      private final Bundle parameters;

      /**
       * Create iTunes Connect App Analytics parameter builder.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder() {
        parameters = new Bundle();
      }

      /**
       * Sets the provider token.
       *
       * @param providerToken The provider token that enables analytics for Dynamic Links from
       *     within iTunes Connect.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public ItunesConnectAnalyticsParameters.Builder setProviderToken(
          @NonNull String providerToken) {
        parameters.putString(KEY_ITUNES_CONNECT_PT, providerToken);
        return this;
      }

      /**
       * Returns the provider token.
       *
       * @return the provider token.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getProviderToken() {
        return parameters.getString(KEY_ITUNES_CONNECT_PT, "");
      }

      /**
       * Sets the affiliate token.
       *
       * @param affiliateToken The affiliate token used to create affiliate-coded links.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public ItunesConnectAnalyticsParameters.Builder setAffiliateToken(
          @NonNull String affiliateToken) {
        parameters.putString(KEY_ITUNES_CONNECT_AT, affiliateToken);
        return this;
      }

      /**
       * Returns the affiliate token.
       *
       * @return the affiliate token.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getAffiliateToken() {
        return parameters.getString(KEY_ITUNES_CONNECT_AT, "");
      }

      /**
       * Sets the campaign token.
       *
       * @param campaignToken The campaign token that developers can add to any link in order to
       *     track sales from a specific marketing campaign.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public ItunesConnectAnalyticsParameters.Builder setCampaignToken(
          @NonNull String campaignToken) {
        parameters.putString(KEY_ITUNES_CONNECT_CT, campaignToken);
        return this;
      }

      /**
       * Returns the campaign token.
       *
       * @return the campaign token.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getCampaignToken() {
        return parameters.getString(KEY_ITUNES_CONNECT_CT, "");
      }

      /**
       * Build ItunesConnectAnalyticsParameters for use with {@link
       * DynamicLink.Builder#setItunesConnectAnalyticsParameters(ItunesConnectAnalyticsParameters)}.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public ItunesConnectAnalyticsParameters build() {
        return new ItunesConnectAnalyticsParameters(parameters);
      }
    }
  }

  /**
   * Social meta-tag parameters.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @Deprecated
  public static final class SocialMetaTagParameters {

    // SocialMetaTagInfo parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_SOCIAL_TITLE = "st";
    /** @hide */
    @VisibleForTesting public static final String KEY_SOCIAL_DESCRIPTION = "sd";
    /** @hide */
    @VisibleForTesting public static final String KEY_SOCIAL_IMAGE_LINK = "si";

    final Bundle parameters;

    private SocialMetaTagParameters(Bundle parameters) {
      this.parameters = parameters;
    }

    /**
     * Builder for social meta-tag parameters.
     *
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @Deprecated
    public static final class Builder {

      private final Bundle parameters;

      /**
       * Create social meta-tag parameter builder.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder() {
        parameters = new Bundle();
      }

      /**
       * Sets the meta-tag title.
       *
       * @param title The title to use when the Dynamic Link is shared in a social post.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public SocialMetaTagParameters.Builder setTitle(@NonNull String title) {
        parameters.putString(KEY_SOCIAL_TITLE, title);
        return this;
      }

      /**
       * Returns the meta-tag title.
       *
       * @return the meta-tag title.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getTitle() {
        return parameters.getString(KEY_SOCIAL_TITLE, "");
      }

      /**
       * Sets the meta-tag description.
       *
       * @param description The description to use when the Dynamic Link is shared in a social post.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public SocialMetaTagParameters.Builder setDescription(@NonNull String description) {
        parameters.putString(KEY_SOCIAL_DESCRIPTION, description);
        return this;
      }

      /**
       * Returns the meta-tag description.
       *
       * @return the meta-tag description.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public String getDescription() {
        return parameters.getString(KEY_SOCIAL_DESCRIPTION, "");
      }

      /**
       * Sets the meta-tag image link.
       *
       * @param imageUrl The URL to an image related to this link.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public SocialMetaTagParameters.Builder setImageUrl(@NonNull Uri imageUrl) {
        parameters.putParcelable(KEY_SOCIAL_IMAGE_LINK, imageUrl);
        return this;
      }

      /**
       * Returns the meta-tag image link.
       *
       * @return the meta-tag image link.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public Uri getImageUrl() {
        Uri imageUrl = parameters.getParcelable(KEY_SOCIAL_IMAGE_LINK);
        if (imageUrl == null) {
          imageUrl = Uri.EMPTY;
        }
        return imageUrl;
      }

      /**
       * Build SocialMetaTagParameters for use with {@link
       * DynamicLink.Builder#setSocialMetaTagParameters(SocialMetaTagParameters)}.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public SocialMetaTagParameters build() {
        return new SocialMetaTagParameters(parameters);
      }
    }
  }

  /**
   * Navigation info parameters.
   *
   * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
   *   service will shut down on August 25, 2025. For more information, see
   *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
   */
  @Deprecated
  public static final class NavigationInfoParameters {

    // NavigationInfo parameters.
    /** @hide */
    @VisibleForTesting public static final String KEY_FORCED_REDIRECT = "efr";

    final Bundle parameters;

    private NavigationInfoParameters(Bundle parameters) {
      this.parameters = parameters;
    }

    /**
     * Builder for navigation info parameters.
     *
     * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
     *   service will shut down on August 25, 2025. For more information, see
     *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
     */
    @Deprecated
    public static final class Builder {

      private final Bundle parameters;

      /**
       * Create navigation info parameter builder.
       *
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @Deprecated
      public Builder() {
        parameters = new Bundle();
      }

      /**
       * Sets whether to enable force redirecting or going to the app preview page. Defaults to
       * false.
       *
       * @param forcedRedirectEnabled If true, app preview page will be disabled and there will be a
       *     redirect to the FDL. If false, go to the app preview page.
       * @deprecated Firebase Dynamic Links is deprecated and should not be used in new projects. The
       *   service will shut down on August 25, 2025. For more information, see
       *   <a href="https://firebase.google.com/support/dynamic-links-faq">Dynamic Links deprecation documentation</a>.
       */
      @NonNull
      @Deprecated
      public NavigationInfoParameters.Builder setForcedRedirectEnabled(
          boolean forcedRedirectEnabled) {
        parameters.putInt(KEY_FORCED_REDIRECT, forcedRedirectEnabled ? 1 : 0);
        return this;
      }

      public boolean getForcedRedirectEnabled() {
        return parameters.getInt(KEY_FORCED_REDIRECT) == 1;
      }

      /**
       * Build NavigationInfoParameters for use with {@link
       * DynamicLink.Builder#setNavigationInfoParameters(NavigationInfoParameters)}.
       *
       * @deprecated
       */
      @NonNull
      @Deprecated
      public NavigationInfoParameters build() {
        return new NavigationInfoParameters(parameters);
      }
    }
  }
}
