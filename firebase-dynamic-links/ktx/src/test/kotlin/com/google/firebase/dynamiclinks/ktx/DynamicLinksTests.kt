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
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.PendingDynamicLinkData
import com.google.firebase.dynamiclinks.ShortDynamicLink
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
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
        val exampleLink = "https://example.com"
        val exampleDomainUriPrefix = "https://example.page.link"

        val dynamicLinkKtx = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse(exampleLink)
            domainUriPrefix = exampleDomainUriPrefix
        }

        val dynamicLink = FirebaseDynamicLinks.getInstance().createDynamicLink()
                .setLink(Uri.parse(exampleLink))
                .setDomainUriPrefix(exampleDomainUriPrefix)
                .buildDynamicLink()

        assertThat(dynamicLinkKtx.uri).isEqualTo(dynamicLink.uri)
    }

    @Test
    fun `androidParameters type-safe builder extension works`() {
        val fallbackLink = "https://android.com"
        val minVersion = 19
        val packageName = "com.example.android"

        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            androidParameters {
                minimumVersion = minVersion
                fallbackUrl = Uri.parse(fallbackLink)
            }
        }

        val anotherDynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            androidParameters(packageName) {
                minimumVersion = minVersion
                fallbackUrl = Uri.parse(fallbackLink)
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("amv")?.toInt()).isEqualTo(minVersion)
        assertThat(dynamicLink.uri.getQueryParameter("afl")).isEqualTo(fallbackLink)

        assertThat(anotherDynamicLink.uri.getQueryParameter("amv")?.toInt()).isEqualTo(minVersion)
        assertThat(anotherDynamicLink.uri.getQueryParameter("afl")).isEqualTo(fallbackLink)
        assertThat(anotherDynamicLink.uri.getQueryParameter("apn")).isEqualTo(packageName)
    }

    @Test
    fun `iosParameters type-safe builder extension works`() {
        val iosAppStoreId = "123456789"
        val iosMinimumVersion = "1.0.1"
        val iosBundleId = "com.example.ios"

        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            iosParameters(iosBundleId) {
                appStoreId = iosAppStoreId
                minimumVersion = iosMinimumVersion
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("ibi")).isEqualTo(iosBundleId)
        assertThat(dynamicLink.uri.getQueryParameter("imv")).isEqualTo(iosMinimumVersion)
        assertThat(dynamicLink.uri.getQueryParameter("isi")).isEqualTo(iosAppStoreId)
    }

    @Test
    fun `googleAnalyticsParameters type-safe builder extension works`() {
        val campaignTerm = "Example Term"
        val campaignContent = "Example Content"
        val campaignSource = "Twitter"
        val campaignMedium = "Social"
        val campaignName = "Example Promo"

        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            googleAnalyticsParameters(campaignSource, campaignMedium, campaignName) {
                term = campaignTerm
                content = campaignContent
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("utm_content")).isEqualTo(campaignContent)
        assertThat(dynamicLink.uri.getQueryParameter("utm_term")).isEqualTo(campaignTerm)
        assertThat(dynamicLink.uri.getQueryParameter("utm_source")).isEqualTo(campaignSource)
        assertThat(dynamicLink.uri.getQueryParameter("utm_medium")).isEqualTo(campaignMedium)
        assertThat(dynamicLink.uri.getQueryParameter("utm_campaign")).isEqualTo(campaignName)
    }

    @Test
    fun `itunesConnectAnalyticsParameters type-safe builder extension works`() {
        val ct = "example-campaign"
        val pt = "123456"

        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            itunesConnectAnalyticsParameters {
                providerToken = pt
                campaignToken = ct
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("pt")).isEqualTo(pt)
        assertThat(dynamicLink.uri.getQueryParameter("ct")).isEqualTo(ct)
    }

    @Test
    fun `socialMetaTagParameters type-safe builder extension works`() {
        val socialTitle = "Example Title"
        val socialDescription = "This link works whether the app is installed or not!"

        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            socialMetaTagParameters {
                title = socialTitle
                description = socialDescription
            }
        }

        assertThat(dynamicLink.uri.getQueryParameter("st")).isEqualTo(socialTitle)
        assertThat(dynamicLink.uri.getQueryParameter("sd")).isEqualTo(socialDescription)
    }

    @Test
    fun `navigationInfoParameters type-safe builder extension works`() {
        val forcedRedirect = true

        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            link = Uri.parse("https://example.com")
            domainUriPrefix = "https://example.page.link"
            navigationInfoParameters {
                forcedRedirectEnabled = true
            }
        }

        val efr = Integer.parseInt(dynamicLink.uri.getQueryParameter("efr")!!) == 1
        assertThat(efr).isEqualTo(forcedRedirect)
    }

    @Test
    fun `ShortDynamicLink destructure declaration works`() {
        val fakeWarning = object : ShortDynamicLink.Warning {
            override fun getMessage() = "Warning"
            override fun getCode() = "warning"
        }

        val expectedShortLink = Uri.parse("https://example.com")
        val expectedPreviewLink = Uri.parse("https://example.com/preview")
        val expectedWarnings = mutableListOf<ShortDynamicLink.Warning>(fakeWarning)

        val mockShortDynamicLink = mock(ShortDynamicLink::class.java)
        `when`(mockShortDynamicLink.shortLink).thenReturn(expectedShortLink)
        `when`(mockShortDynamicLink.previewLink).thenReturn(expectedPreviewLink)
        `when`(mockShortDynamicLink.warnings).thenReturn(expectedWarnings)

        val (shortLink, previewLink, warnings) = mockShortDynamicLink

        assertThat(shortLink).isEqualTo(expectedShortLink)
        assertThat(previewLink).isEqualTo(expectedPreviewLink)
        assertThat(warnings).isEqualTo(expectedWarnings)
    }

    @Test
    fun `PendingDynamicLinkData destructure declaration works`() {
        val expectedLink = Uri.parse("https://example.com")
        val expectedMinAppVersion = 30
        val expectedTimestamp = 172947600L

        val mockPendingData = mock(PendingDynamicLinkData::class.java)
        `when`(mockPendingData.link).thenReturn(expectedLink)
        `when`(mockPendingData.minimumAppVersion).thenReturn(expectedMinAppVersion)
        `when`(mockPendingData.clickTimestamp).thenReturn(expectedTimestamp)

        val (link, minAppVersion, timestamp) = mockPendingData

        assertThat(link).isEqualTo(expectedLink)
        assertThat(minAppVersion).isEqualTo(expectedMinAppVersion)
        assertThat(timestamp).isEqualTo(expectedTimestamp)
    }
}
