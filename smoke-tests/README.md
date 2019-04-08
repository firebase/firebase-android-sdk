# Firebase Smoke Test Suite

This directory contains smoke tests for Firebase on Android. These tests are
intended to verify the integrations between different components and versions of
Firebase. The tests should not include additional overhead, such as user
interfaces. However, the tests should strive to remain similar to real use
cases. As such, these tests run on devices or emulators (no Robolectric). This
is a work in progress, and the following list shows what is complete:

- [ ] Create first set of tests to replace old test apps.
- [ ] Reliably run smoke tests on CI.
- [ ] Support version matrices.
- [ ] Extend to collect system health metrics.

# Project Structure

This Gradle project is split into flavors for each Firebase product. Test code
lives in these flavors. Each flavor has one or more test classes in the app APK.
These are regular JUnit tests.

There is only one testing variant, `androidTest`. This contains the
`SmokeTestSuite` JUnit test runner. This custom runner finds the test classes by
searching for metadata in the application's Android manifest. Therefore, a
metadata tag named `com.google.firebase.testing.classes` is needed in each
application variant with a comma-separated list of test class names. The
`androidTest` variant does not need to be modified by test authors.

# Test Workflow

Android instrumentation uses two APKs: one for the app and the other for tests.
As we want to test building as a third-party consumer, the test code and
Firebase dependencies all belong in the application APK. This can be optionally
obfsucated like a real application.

The Android instrumentation runner loads one test class, `SmokeTests`. This is
executed by the `SmokeTestSuite`. `SmokeTests` is simply a placeholder class to
bootstrap the logic contained in the suite runner, which then delegates to the
test runners for the classes referenced by the Android manifest.
