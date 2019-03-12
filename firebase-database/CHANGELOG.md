# Unreleased
- [changed] Added `@RestrictTo` annotations to discourage the use of APIs that
  are not public. This affects internal APIs that were previously obfuscated
  and are not mentioned in our documentation.

# 16.0.6  
- [fixed] Fixed an issue that could cause a NullPointerException during the
  initial handshake with the Firebase backend (#119).
