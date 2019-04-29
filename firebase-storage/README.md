# firebase-storage

This is the Cloud Storage for Firebase component of the Firebase Android SDK.

Cloud Storage for Firebase is a powerful, simple, and cost-effective object storage service built
for Google scale. The Firebase SDKs for Cloud Storage add Google security to file uploads and
downloads for your Firebase apps, regardless of network quality. You can use our SDKs to store
images, audio, video, or other user-generated content. On the server, you can use Google Cloud
Storage, to access the same files.

All Gradle commands should be run from the source root (which is one level up
from this folder). See the README.md in the source root for instructions on
publishing/testing Firebase Storage.

## Unit Tests

The Firebase Storage Unit tests exercise the public API endpoints and mock the associated network 
activity. For each test, the unit tests verify all network traffic and state changes by comparing
them to pre-recorded activity. The network traffic as well as the expected states are defined in the
resource files included under `src/test/resources/activitylogs`.

To add new tests, use the the test app provided under `../firebase-storage-app`. You
can run this test app in a simulator or on a device. The app's various test cases can be activated
through the UI and record all network traffic and state transitions. After each test run, the
captured network traffic and all state changes are written to the app's local storage directory. You 
can use this captured state to add additional unit tests for the storage client.

The app reads both from the network and from the device. To run existing test cases, you need to
upload the files under `src/test/resources/assets` to the Storage bucket of your Firebase project. 
You can use [gsutil](https://cloud.google.com/storage/docs/gsutil) or the 
[Firebase Console](https://console.firebase.google.com) for this. Furthermore, to make these file
available locally, they should also be copied to device's local storage. The expected Storage 
location is shown when you run a test that requires a local file (e.g. an upload tests).

When a test run finishes, the test app displays the location of the test output. These files need to
be copied to `src/test/resources/activitylogs` before the can be used in unit tests. Note that the 
captured network traffic in these files includes your Authentication Tokens and your Project ID,
which need to be replaced.

To supply the fake authentication token used by the Unit test runner, replace any value for the
`x-firebase-gmpid` request property with `fooey`. The expected header format is:

```
setRequestProperty:x-firebase-gmpid,fooey
```

Similarly, replace any Firebase Storage URLs (such as 
`https://firebasestorage.googleapis.com/v0/b/{PROJECT}.appspot.com/o/...`) with 
`https://firebasestorage.googleapis.com/v0/b/fooey.appspot.com/o/...`

