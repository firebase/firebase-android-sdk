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

import androidx.annotation.Keep
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.dynamiclinks.DynamicLink
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.PendingDynamicLinkData
import com.google.firebase.dynamiclinks.ShortDynamicLink
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent

/** Returns the [FirebaseDynamicLinks] instance of the default [FirebaseApp]. */
val Firebase.dynamicLinks: FirebaseDynamicLinks
    get() = FirebaseDynamicLinks.getInstance()

/** Returns the [FirebaseDynamicLinks] instance of a given [FirebaseApp]. */
fun Firebase.dynamicLinks(app: FirebaseApp): FirebaseDynamicLinks {
    return FirebaseDynamicLinks.getInstance(app)
}

/** Creates a [DynamicLink.AndroidParameters] object initialized using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.androidParameters(init: DynamicLink.AndroidParameters.Builder.() -> Unit) {
    val builder = DynamicLink.AndroidParameters.Builder()
    builder.init()
    setAndroidParameters(builder.build())
}

/** Creates a [DynamicLink.AndroidParameters] object initialized with the specified [packageName] and using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.androidParameters(packageName: String, init: DynamicLink.AndroidParameters.Builder.() -> Unit) {
    val builder = DynamicLink.AndroidParameters.Builder(packageName)
    builder.init()
    setAndroidParameters(builder.build())
}

/** Creates a [DynamicLink.IosParameters] object initialized with the specified [bundleId] and using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.iosParameters(bundleId: String, init: DynamicLink.IosParameters.Builder.() -> Unit) {
    val builder = DynamicLink.IosParameters.Builder(bundleId)
    builder.init()
    setIosParameters(builder.build())
}

/** Creates a [DynamicLink.GoogleAnalyticsParameters] object initialized using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.googleAnalyticsParameters(init: DynamicLink.GoogleAnalyticsParameters.Builder.() -> Unit) {
    val builder = DynamicLink.GoogleAnalyticsParameters.Builder()
    builder.init()
    setGoogleAnalyticsParameters(builder.build())
}

/** Creates a [DynamicLink.GoogleAnalyticsParameters] object initialized with the specified
 * [source], [medium], [campaign] and using the [init] function and sets it to the [DynamicLink.Builder]. */
fun DynamicLink.Builder.googleAnalyticsParameters(
    source: String,
    medium: String,
    campaign: String,
    init: DynamicLink.GoogleAnalyticsParameters.Builder.() -> Unit
) {
    val builder = DynamicLink.GoogleAnalyticsParameters.Builder(source, medium, campaign)
    builder.init()
    setGoogleAnalyticsParameters(builder.build())
}

/** Creates a [DynamicLink.ItunesConnectAnalyticsParameters] object initialized using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.itunesConnectAnalyticsParameters(init: DynamicLink.ItunesConnectAnalyticsParameters.Builder.() -> Unit) {
    val builder = DynamicLink.ItunesConnectAnalyticsParameters.Builder()
    builder.init()
    setItunesConnectAnalyticsParameters(builder.build())
}

/** Creates a [DynamicLink.SocialMetaTagParameters] object initialized using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.socialMetaTagParameters(init: DynamicLink.SocialMetaTagParameters.Builder.() -> Unit) {
    val builder = DynamicLink.SocialMetaTagParameters.Builder()
    builder.init()
    setSocialMetaTagParameters(builder.build())
}

/** Creates a [DynamicLink.NavigationInfoParameters] object initialized using the [init] function and sets it to the [DynamicLink.Builder] */
fun DynamicLink.Builder.navigationInfoParameters(init: DynamicLink.NavigationInfoParameters.Builder.() -> Unit) {
    val builder = DynamicLink.NavigationInfoParameters.Builder()
    builder.init()
    setNavigationInfoParameters(builder.build())
}

/** Creates a [DynamicLink] object initialized using the [init] function. */
fun FirebaseDynamicLinks.dynamicLink(init: DynamicLink.Builder.() -> Unit): DynamicLink {
    val builder = FirebaseDynamicLinks.getInstance().createDynamicLink()
    builder.init()
    return builder.buildDynamicLink()
}

/** Creates a [ShortDynamicLink] object initialized using the [init] function. */
fun FirebaseDynamicLinks.shortLinkAsync(init: DynamicLink.Builder.() -> Unit): Task<ShortDynamicLink> {
    val builder = FirebaseDynamicLinks.getInstance().createDynamicLink()
    builder.init()
    return builder.buildShortDynamicLink()
}

/** Creates a [ShortDynamicLink] object initialized using the [init] function. */
fun FirebaseDynamicLinks.shortLinkAsync(suffix: Int, init: DynamicLink.Builder.() -> Unit): Task<ShortDynamicLink> {
    val builder = FirebaseDynamicLinks.getInstance().createDynamicLink()
    builder.init()
    return builder.buildShortDynamicLink(suffix)
}

/** Destructuring declaration for [ShortDynamicLink] to provide shortLink. */
operator fun ShortDynamicLink.component1() = shortLink

/** Destructuring declaration for [ShortDynamicLink] to provide previewLink. */
operator fun ShortDynamicLink.component2() = previewLink

/** Destructuring declaration for [ShortDynamicLink] to provide warnings. */
operator fun ShortDynamicLink.component3(): List<ShortDynamicLink.Warning> = warnings

/** Destructuring declaration for [PendingDynamicLinkData] to provide link. */
operator fun PendingDynamicLinkData.component1() = link

/** Destructuring declaration for [PendingDynamicLinkData] to provide minimumAppVersion. */
operator fun PendingDynamicLinkData.component2() = minimumAppVersion

/** Destructuring declaration for [PendingDynamicLinkData] to provide clickTimestamp. */
operator fun PendingDynamicLinkData.component3() = clickTimestamp

internal const val LIBRARY_NAME: String = "fire-dl-ktx"

/** @suppress */
@Keep
class FirebaseDynamicLinksKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
