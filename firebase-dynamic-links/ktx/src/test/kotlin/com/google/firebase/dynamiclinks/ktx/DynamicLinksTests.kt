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

package com.google.firebase.dynamiclinks.ktx

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

const val EXISTING_APP = "existing"

abstract class BaseTestCase {
    @Before
    fun setUp() {
        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .build()
        )

        Firebase.initialize(
                RuntimeEnvironment.application,
                FirebaseOptions.Builder()
                        .setApplicationId(APP_ID)
                        .setApiKey(API_KEY)
                        .setProjectId("123")
                        .build(),
                EXISTING_APP
        )
    }

    @After
    fun cleanUp() {
        FirebaseApp.clearInstancesForTest()
    }
}

@RunWith(RobolectricTestRunner::class)
class DynamicLinksTests : BaseTestCase() {

    @Test
    fun `Firebase#dynamicLinks should delegate to FirebaseDynamicLinks#getInstance()`() {
        assertThat(Firebase.dynamicLinks).isSameInstanceAs(FirebaseDynamicLinks.getInstance())
    }

    @Test
    fun `Firebase#dynamicLinks should delegate to FirebaseDynamicLinks#getInstance(FirebaseApp)`() {
        val app = Firebase.app(EXISTING_APP)
        assertThat(Firebase.dynamicLinks(app))
                .isSameInstanceAs(FirebaseDynamicLinks.getInstance(app))
    }

    @Test
    fun `Firebase#dynamicLinks#createDynamicLink`() {
        val link = "https://example.com"
        val domainUriPrefix = "https://example.page.link"

        val dynamicLinkKtx = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse(link))
            setDomainUriPrefix(domainUriPrefix)
        }

        val dynamicLink = FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(link))
                .setDomainUriPrefix(domainUriPrefix)
                .buildDynamicLink()

        assertThat(dynamicLinkKtx.uri).isEqualTo(dynamicLink.uri)
    }

    @Test
    fun `androidParameters type-safe builder extension works`() {
        val fallbackUrl = "https://android.com"
        val minimumVersion = 19
        val packageName = "com.example.android"

        val dynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            androidParameters {
                setMinimumVersion(minimumVersion)
                setFallbackUrl(Uri.parse(fallbackUrl))
            }
        }

        val anotherDynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            androidParameters(packageName) {
                setMinimumVersion(minimumVersion)
                setFallbackUrl(Uri.parse(fallbackUrl))
            }
        }

        assertThat(Integer.parseInt(dynamicLink.uri.getQueryParameter("amv"))).isEqualTo(minimumVersion)
        assertThat(dynamicLink.uri.getQueryParameter("afl")).isEqualTo(fallbackUrl)

        assertThat(Integer.parseInt(anotherDynamicLink.uri.getQueryParameter("amv"))).isEqualTo(minimumVersion)
        assertThat(anotherDynamicLink.uri.getQueryParameter("afl")).isEqualTo(fallbackUrl)
        assertThat(anotherDynamicLink.uri.getQueryParameter("apn")).isEqualTo(packageName)
    }

    @Test
    fun `iosParameters type-safe builder extension works`() {
        val appStoreId = "123456789"
        val minimumVersion = "1.0.1"
        val bundleId = "com.example.ios"

        val dynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            iosParameters(bundleId) {
                setAppStoreId(appStoreId)
                setMinimumVersion(minimumVersion)
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("ibi")).isEqualTo(bundleId)
        assertThat(dynamicLink.uri.getQueryParameter("imv")).isEqualTo(minimumVersion)
        assertThat(dynamicLink.uri.getQueryParameter("isi")).isEqualTo(appStoreId)
    }

    @Test
    fun `googleAnalyticsParameters type-safe builder extension works`() {
        val term = "Example Term"
        val content = "Example Content"
        val source = "Twitter"
        val medium = "Social"
        val campaign = "Example Promo"

        val dynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            googleAnalyticsParameters(source, medium, campaign) {
                setTerm(term)
                setContent(content)
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("utm_content")).isEqualTo(content)
        assertThat(dynamicLink.uri.getQueryParameter("utm_term")).isEqualTo(term)
        assertThat(dynamicLink.uri.getQueryParameter("utm_source")).isEqualTo(source)
        assertThat(dynamicLink.uri.getQueryParameter("utm_medium")).isEqualTo(medium)
        assertThat(dynamicLink.uri.getQueryParameter("utm_campaign")).isEqualTo(campaign)
    }

    @Test
    fun `itunesConnectAnalyticsParameters type-safe builder extension works`() {
        val campaignToken = "example-campaign"
        val providerToken = "123456"

        val dynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            itunesConnectAnalyticsParameters {
                setProviderToken(providerToken)
                setCampaignToken(campaignToken)
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("pt")).isEqualTo(providerToken)
        assertThat(dynamicLink.uri.getQueryParameter("ct")).isEqualTo(campaignToken)
    }

    @Test
    fun `socialMetaTagParameters type-safe builder extension works`() {
        val title = "Example Title"
        val description = "This link works whether the app is installed or not!"

        val dynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            socialMetaTagParameters {
                setTitle(title)
                setDescription(description)
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("st")).isEqualTo(title)
        assertThat(dynamicLink.uri.getQueryParameter("sd")).isEqualTo(description)
    }

    @Test
    fun `navigationInfoParameters type-safe builder extension works`() {
        val forcedRedirect = true

        val dynamicLink = Firebase.dynamicLinks.createDynamicLink {
            setLink(Uri.parse("https://example.com"))
            setDomainUriPrefix("https://example.page.link")
            navigationInfoParameters {
                setForcedRedirectEnabled(forcedRedirect)
            }
        }

        val efr = Integer.parseInt(dynamicLink.uri.getQueryParameter("efr")) == 1
        assertThat(efr).isEqualTo(forcedRedirect)
    }
}
