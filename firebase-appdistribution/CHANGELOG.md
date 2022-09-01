# 16.0.0-beta03
* [feature] The [appdistro] SDK has been split into two libraries:

  * `firebase-appdistribution-api` - The API-only library<br>
    This new API-only library is functional only when the full
    [appdistro] SDK implementation (`firebase-appdistribution`) is present.
    `firebase-appdistribution-api` can be included in all
    [build variants](https://developer.android.com/studio/build/build-variants){: .external}.

  * `firebase-appdistribution` - The full SDK implementation<br>
    This full SDK implementation is optional and should only be included in
    pre-release builds.

  Visit the documentation to learn how to
  [add these SDKs](/docs/app-distribution/set-up-alerts?platform=android#add-appdistro)
  to your Android app.

## Kotlin
* [removed] The Kotlin extensions library `firebase-appdistribution-ktx`
  has been removed. All its functionality has been moved to the new API-only
  library: `firebase-appdistribution-api-ktx`.

# 16.0.0-beta02
* [fixed] Fixed a bug that prevented testers from signing in when the app had
an underscore in the package name.

* [fixed] Fixed a UI bug where the APK download notification displayed the
incorrect error message.

* [changed] Internal improvements to tests.

## Kotlin
The Kotlin extensions library transitively includes the base
`firebase-app-distribution` library. The Kotlin extensions library has no
additional updates.

# 16.0.0-beta01
* [feature] The [appdistro] Android SDK is now available in beta. You
  can use this SDK to notify testers in-app when a new test build is available.
  To learn more, visit the
  [[appdistro] reference documentation](/docs/reference/android/com/google/firebase/appdistribution/package-summary).

## Kotlin
The [appdistro] Android library with Kotlin extensions is now available in
beta. The Kotlin extensions library transitively includes the base
`firebase-app-distribution` library. To learn more, visit the
[[appdistro] KTX reference documentation](/docs/reference/kotlin/com/google/firebase/appdistribution/ktx/package-summary).

