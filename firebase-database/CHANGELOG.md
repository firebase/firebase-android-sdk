# 19.1.1
- [fixed] Fixed a crash that occurred when we attempted to start a network
  connection during app shutdown (#672).

# 19.1.0
- [feature] Added support for the Firebase Database Emulator. To connect to
  the emulator, specify "http://<emulatorHost>/?ns=<projectId>" as your
  Database URL (via `FirebaseDatabase.getInstance(String)`).
  Note that if you are running the Database Emulator on "localhost" and
  connecting from an app that is running inside an Android Emulator, the
  emulator host will be "10.0.2.2" followed by its port.

# 18.0.1
- [changed] The SDK now reports the correct version number (via
  `FirebaseDatabase.getSdkVersion()`).

# 17.0.0
- [changed] Added `@RestrictTo` annotations to discourage the use of APIs that
  are not public. This affects internal APIs that were previously obfuscated
  and are not mentioned in our documentation.
- [changed] Improved error messages for certain Number types that are not
  supported by our serialization layer (#272).
- [internal] Updated the SDK initialization process and removed usages of
  deprecated method.
- [changed] Added missing nullability annotations for better Kotlin interop.
- [internal] Removed ``@PublicApi` annotations as they are no longer enforced
  and have no semantic meaning.

# 16.0.6  
- [fixed] Fixed an issue that could cause a NullPointerException during the
  initial handshake with the Firebase backend (#119).
