# Dynamic Links Kotlin Extensions

## Getting Started

To use the Dynamic Links Android SDK with Kotlin Extensions, add the following
to your app's `build.gradle` file:

```groovy
// See maven.google.com for the latest versions
// This library transitively includes the firebase-dynamic-links library
implementation 'com.google.firebase:firebase-dynamic-links-ktx:$VERSION'
```

## Features

### Get an instance of FirebaseDynamicLinks

**Kotlin**
```kotlin
val dynamicLinks = FirebaseDynamicLinks.getInstance()
val anotherDynamicLinks = FirebaseDynamicLinks.getInstance(FirebaseApp.getInstance("myApp"))
```

**Kotlin + KTX**
```kotlin
val dynamicLinks = Firebase.dynamicLinks
val anotherDynamicLinks = Firebase.dynamicLinks(Firebase.app("myApp"))
```

### Create a Dynamic Link from parameters

**Kotlin**
```kotlin
val dynamicLink = FirebaseDynamicLinks.getInstance().createDynamicLink()
        .setLink(Uri.parse("https://www.example.com/"))
        .setDomainUriPrefix("https://example.page.link")
        .setAndroidParameters(
                DynamicLink.AndroidParameters.Builder("com.example.android")
                        .setMinimumVersion(16)
                        .build())
        .setIosParameters(
                DynamicLink.IosParameters.Builder("com.example.ios")
                        .setAppStoreId("123456789")
                        .setMinimumVersion("1.0.1")
                        .build())
        .setGoogleAnalyticsParameters(
                DynamicLink.GoogleAnalyticsParameters.Builder()
                        .setSource("orkut")
                        .setMedium("social")
                        .setCampaign("example-promo")
                        .build())
        .setItunesConnectAnalyticsParameters(
                DynamicLink.ItunesConnectAnalyticsParameters.Builder()
                        .setProviderToken("123456")
                        .setCampaignToken("example-promo")
                        .build())
        .setSocialMetaTagParameters(
                DynamicLink.SocialMetaTagParameters.Builder()
                        .setTitle("Example of a Dynamic Link")
                        .setDescription("This link works whether the app is installed or not!")
                        .build())
        .buildDynamicLink()
```

**Kotlin + KTX**
```kotlin
val dynamicLink = Firebase.dynamicLinks.dynamicLink {
    link = Uri.parse("https://www.example.com/")
    domainUriPrefix = "https://example.page.link"
    androidParameters("com.example.android") {
        minimumVersion = 16
    }
    iosParameters("com.example.ios") {
        appStoreId = "123456789"
        minimumVersion = "1.0.1"
    }
    googleAnalyticsParameters {
        source = "orkut"
        medium = "social"
        campaign = "example-promo"
    }
    itunesConnectAnalyticsParameters {
        providerToken = "123456"
        campaignToken = "example-promo"
    }
    socialMetaTagParameters {
        title = "Example of a Dynamic Link"
        description = "This link works whether the app is installed or not!"
    }
}
```

### Shorten a long Dynamic Link

**Kotlin**
```kotlin
FirebaseDynamicLinks.getInstance().createDynamicLink()
        .setLongLink(Uri.parse("https://example.page.link/?link=" +
                "https://www.example.com/&apn=com.example.android&ibn=com.example.ios"))
        .buildShortDynamicLink()
        .addOnSuccessListener { result ->
            // Short link created
            val shortLink = result.shortLink
            val flowchartLink = result.previewLink
        }
        .addOnFailureListener {
            // Error
            // ...
        }
```

**Kotlin + KTX**
```kotlin
Firebase.dynamicLinks.shortLinkAsync {
    longLink = Uri.parse("https://example.page.link/?link=" +
        "https://www.example.com/&apn=com.example.android&ibn=com.example.ios")
}.addOnSuccessListener { result ->
    // Short link created
    val shortLink = result.shortLink
    val flowchartLink = result.previewLink
}.addOnFailureListener {
    // Error
    // ...
}
```

### Create a Dynamic Link with a shorter link suffix

**Kotlin**
```kotlin
val shortLinkTask = FirebaseDynamicLinks.getInstance().createDynamicLink()
        // ...
        .buildShortDynamicLink(ShortDynamicLink.Suffix.SHORT)
```

**Kotlin + KTX**
```kotlin
val shortLinkTask = Firebase.dynamicLinks.shortLinkAsync(ShortDynamicLink.Suffix.SHORT) {
    // ...
}
```
