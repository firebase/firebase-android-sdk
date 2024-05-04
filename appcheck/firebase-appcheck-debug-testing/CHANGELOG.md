# Unreleased


# 18.0.0
* [changed] Bump internal dependencies

# 17.1.2
* [unchanged] Updated to keep [app_check] SDK versions aligned.

# 17.1.1
* [unchanged] Updated to keep [app_check] SDK versions aligned.

# 17.1.0
* [unchanged] Updated to keep [app_check] SDK versions aligned.

# 17.0.0
* [unchanged] Updated to keep [app_check] SDK versions aligned.

# 16.1.2
* [unchanged] Updated to keep [app_check] SDK versions aligned.

# 16.1.1
* [changed] Integrated the [app_check] Debug Testing SDK with Firebase
  components.
  (GitHub [#4436](//github.com/firebase/firebase-android-sdk/issues/4436){: .external})

# 16.1.0
* [unchanged] Updated to accommodate the release of the updated
  [app_check] Kotlin extensions library.

# 16.0.1
* [changed] Updated dependency of `play-services-basement` to its latest
  version (v18.1.0).

# 16.0.0
* [changed] [app_check] has exited beta and is now generally available for
  use.

# 16.0.0-beta06
* [fixed] Fixed a bug in the [app_check] token refresh flow when using a
  custom provider.

# 16.0.0-beta05
* [changed] Internal improvements.

# 16.0.0-beta04
* [changed] Improved error handling logic by minimizing the amount of requests
  that are unlikely to succeed.
* [fixed] Fixed heartbeat reporting.

# 16.0.0-beta03
* [changed] Added `X-Android-Package` and `X-Android-Cert` request headers to
  [app_check] network calls.

# 16.0.0-beta02
* [feature] Added [`getAppCheckToken()`](/docs/reference/android/com/google/firebase/appcheck/FirebaseAppCheck#getAppCheckToken(boolean)),
  [`AppCheckTokenListener`](/docs/reference/android/com/google/firebase/appcheck/FirebaseAppCheck.AppCheckListener),
  and associated setters and removers for developers to request and observe
  changes to the [app_check] token.

# 16.0.0-beta01
* [feature] Initial beta release of the [app_check] Debug Testing SDK with
  abuse reduction features.

