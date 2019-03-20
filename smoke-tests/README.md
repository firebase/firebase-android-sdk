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
lives in these flavors. Each flavor has a single activity with the test methods.
Test methods are annotated with `SmokeTest`. The Android plugin adds additional,
compound flavors, such as `androidTestDatabase`. These should only contain a
test class that extends `SmokeTestBase`. The are no formal test methods in these
classes.

# Test Workflow

Android instrumentation uses two APKs: one for the app and the other for tests.
As we want to test building as a third-party consumer, the test code and
Firebase dependencies all belong in the application APK. This can be optionally
obfsucated like a real application.

To simplify the tests, all test code is one source tree. The test source tree is
presently required for bootstrapping the testing procedure and does not house
anything related to the test methods. The test class reflectively finds and
executes the test methods from the application APK. This may be simplified in
the future.
