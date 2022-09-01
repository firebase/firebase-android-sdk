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
With the removal of the Kotlin extensions library
`firebase-appdistribution-ktx`, its functionality has been moved to the new
API-only library: `firebase-appdistribution-api-ktx`.

This new Kotlin extensions library transitively includes the
`firebase-appdistribution-api` library. The Kotlin extensions library has no
additional updates.
