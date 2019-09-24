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
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.dynamiclinks.DynamicLink
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

/** Returns a [DynamicLink.AndroidParameters] object initialized using the [init] function. */
fun androidParameters(
        init: DynamicLink.AndroidParameters.Builder.() -> Unit
) : DynamicLink.AndroidParameters {
    val builder = DynamicLink.AndroidParameters.Builder()
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.AndroidParameters] object initialized with the specified [packageName]
 * and using the [init] function. */
fun androidParameters(
        packageName: String,
        init: DynamicLink.AndroidParameters.Builder.() -> Unit
) : DynamicLink.AndroidParameters {
    val builder = DynamicLink.AndroidParameters.Builder(packageName)
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.IosParameters] object initialized with the specified [bundleId]
 * and using the [init] function. */
fun iosParameters(
        bundleId: String,
        init: DynamicLink.IosParameters.Builder.() -> Unit
) : DynamicLink.IosParameters {
    val builder = DynamicLink.IosParameters.Builder(bundleId)
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.GoogleAnalyticsParameters] object initialized using the
 *  [init] function. */
fun googleAnalyticsParameters(
        init: DynamicLink.GoogleAnalyticsParameters.Builder.() -> Unit
) : DynamicLink.GoogleAnalyticsParameters {
    val builder = DynamicLink.GoogleAnalyticsParameters.Builder()
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.GoogleAnalyticsParameters] object initialized with the specified
 * [source], [medium], [campaign] and using the [init] function. */
fun googleAnalyticsParameters(
        source: String,
        medium: String,
        campaign: String,
        init: DynamicLink.GoogleAnalyticsParameters.Builder.() -> Unit
) : DynamicLink.GoogleAnalyticsParameters {
    val builder = DynamicLink.GoogleAnalyticsParameters.Builder(source, medium, campaign)
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.ItunesConnectAnalyticsParameters] object initialized using
 *  the [init] function. */
fun itunesConnectAnalyticsParameters(
        init: DynamicLink.ItunesConnectAnalyticsParameters.Builder.() -> Unit
) : DynamicLink.ItunesConnectAnalyticsParameters {
    val builder = DynamicLink.ItunesConnectAnalyticsParameters.Builder()
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.SocialMetaTagParameters] object initialized using
 *  the [init] function. */
fun socialMetaTagParameters(
        init: DynamicLink.SocialMetaTagParameters.Builder.() -> Unit
) : DynamicLink.SocialMetaTagParameters {
    val builder = DynamicLink.SocialMetaTagParameters.Builder()
    builder.init()
    return builder.build()
}

/** Returns a [DynamicLink.NavigationInfoParameters] object initialized using
 *  the [init] function. */
fun navigationInfoParameters(
        init: DynamicLink.NavigationInfoParameters.Builder.() -> Unit
) : DynamicLink.NavigationInfoParameters {
    val builder = DynamicLink.NavigationInfoParameters.Builder()
    builder.init()
    return builder.build()
}

/** Creates a [DynamicLink] object initialized using the [init] function. */
fun createDynamicLink(init: DynamicLink.Builder.() -> Unit) : DynamicLink {
    val builder = FirebaseDynamicLinks.getInstance().createDynamicLink()
    builder.init()
    return builder.buildDynamicLink()
}

/** Creates a [ShortDynamicLink] object initialized using the [init] function. */
fun createShortDynamicLink(init: DynamicLink.Builder.() -> Unit) : Task<ShortDynamicLink> {
    val builder = FirebaseDynamicLinks.getInstance().createDynamicLink()
    builder.init()
    return builder.buildShortDynamicLink()
}

/** Creates a [ShortDynamicLink] object initialized using the [init] function. */
fun createShortDynamicLink(
        suffix: Int,
        init: DynamicLink.Builder.() -> Unit
) : Task<ShortDynamicLink> {
    val builder = FirebaseDynamicLinks.getInstance().createDynamicLink()
    builder.init()
    return builder.buildShortDynamicLink(suffix)
}

internal const val LIBRARY_NAME: String = "fire-dl-ktx"

/** @suppress */
@Keep
class FirebaseDynamicLinksRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
