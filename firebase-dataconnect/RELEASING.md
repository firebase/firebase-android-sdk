# Release Firebase Data Connect Android SDK

Created on May 30, 2024 by @dconeybe

Last updated June 25, 2024 by @dconeybe

This document outlines the steps for releasing _alpha_ versions of the Firebase Data Connect Android
SDK. At the time of writing (June 25, 2024) the Data Connect SDK lives in a branch named
`dataconnect`. Once the `dataconnect` branch is merged into the `master` branch, these steps will be
obsolete and will need to be updated.

### Set Release Date in FirebaseDataConnect.kt

Open [FirebaseDataConnect.kt](https://github.com/firebase/firebase-android-sdk/blob/dataconnect/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/FirebaseDataConnect.kt)
in a text editor and find the "Release Notes" section in the class documentation. There will be a
subsection named something like

```
##### 16.0.0-alpha05 (not yet released)
```

Replace "(not yet released)" with the anticipated release date, like this:

```
##### 16.0.0-alpha05 (June 24, 2024)
```

### Remove "-dev" Version Suffix in gradle.properties

Open [gradle.properties](https://github.com/firebase/firebase-android-sdk/blob/dataconnect/firebase-dataconnect/gradle.properties)
in a text editor and remove the `-dev` suffix from the version number.

For example, for the `16.0.0-alpha05` release, change

```
version=16.0.0-alpha05-dev
```

to

```
version=16.0.0-alpha05
```

### Commit the Changes to FirebaseDataConnect.kt and gradle.properties

Commit the Changes to `FirebaseDataConnect.kt` and `gradle.properties` in a commit with a message
like this:

```
Release firebase-dataconnect version 16.0.0-alpha05
```

Make note of the hash of this commit, as it will be used later as this will be the commit upon which
the release will be based.

### Add New Release Notes Section in FirebaseDataConnect.kt

Open [FirebaseDataConnect.kt](https://github.com/firebase/firebase-android-sdk/blob/dataconnect/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/FirebaseDataConnect.kt)
in a text editor and find the "Release Notes" section in the class documentation. Add a new
subsection for the new version, so that release notes can be added here and accumulated until the
next release.

For example, if the next release would be `16.0.0-alpha06`, add this section:

```
##### 16.0.0-alpha06 (not yet released)
```

### Bump Version and Add "-dev" Suffix in gradle.properties

Open [gradle.properties](https://github.com/firebase/firebase-android-sdk/blob/dataconnect/firebase-dataconnect/gradle.properties)
in a text editor and bump the version number and add the `-dev` suffix from the version number.

For example, if the next release would be `16.0.0-alpha06`, change:

```
version=16.0.0-alpha05
```

to

```
version=16.0.0-alpha06-dev
```

The philosophy is that at all times during development the version in `gradle.properties` reflects
the next anticipated version number with `-dev` appended. Only for a single commit will the `-dev`
suffix be removed, and that will be the commit that is actually released. So, in a perfect world,
there would be no intervening commits between the one that _removes_ the `-dev` suffix and the one
that bumps the version and adds back the `-dev` suffix.

### Commit the Changes to FirebaseDataConnect.kt and gradle.properties

Commit the Changes to `FirebaseDataConnect.kt` and `gradle.properties` in a commit with a message
like this:

```
Bump firebase-dataconnect version to 16.0.0-alpha06-dev and add "unreleased" section to release notes.
```

### Merge the Release Commit into the Release Branch

Note the hash of the commit made in a previous step that removed the `-dev` suffix from the version
number in `gradle.properties`. This commit will hereafter be referred to as the "release commit".

Check out the `dataconnect-rel` branch. Merge in the "release commit". This can be done by running
these commands:

```
git fetch
git checkout --detach remotes/origin/dataconnect-rel
# Replace the hash below with that of your "release commit"
git merge 58e1ebd3ac7e243878e5ddba6bea02d6788059ff
```

The merge should be clean. If it is not, you will need to investigate.

To ensure the merge went as expected, run this diff command:

```
# Replace the hash below with that of your "release commit"
git diff 58e1ebd3ac7e243878e5ddba6bea02d6788059ff..HEAD
```

The output should be exactly:

```
diff --git a/release.json b/release.json
new file mode 100644
index 000000000..168311d92
--- /dev/null
+++ b/release.json
@@ -0,0 +1,6 @@
+{
+    "name": "dataconnect-release",
+    "libraries": [
+        ":firebase-dataconnect"
+    ]
+}
```

This `release.json` file is used by the release process, and specifies that only
firebase-dataconnect should be released. If the diff is anything other than this, investigate.

If everything looks good, push up the changes by running this command:

```
git push origin HEAD:dataconnect-rel
```

### Run the "Build Release Artifacts" Action

In a web browser, go to
https://github.com/firebase/firebase-android-sdk/actions/workflows/build-release-artifacts.yml
and click the "Run Workflow" button, Select the `dataconnect-rel` branch, and click the
"Run Workflow" button.

This will start a new workflow within a few seconds and the workflow will take up to 15 minutes
to complete (but typically completes in about 2 minutes). Note the URL of the workflow, for example
https://github.com/firebase/firebase-android-sdk/actions/runs/9279002255.

Wait for the action to complete.

### Test the "Build Release Artifacts" Output

To test the built artifact, we run the "connectors" integration test. This requires the following
local modifications to Git repository to use the release artifacts rather than the source code in
the Git repository.

Make sure you have the `dataconnect-rel` branch checked out at HEAD, and make the following changes:

* `subprojects.cfg`: Remove `firebase-dataconnect`
* `build.gradle`: Replace `mavenLocal()` with
`mavenLocal { url 'file:///.../firebase-dataconnect/m2' }`,
replacing `...` with the absolute path to the local checkout of the Git repository.
* `firebase-dataconnect/firebase-dataconnect.gradle.kts`: Delete this file.
* `firebase-dataconnect/androidTestutil/androidTestutil.gradle.kts`,
`firebase-dataconnect/connectors/connectors.gradle.kts`,
`firebase-dataconnect/testutil/testutil.gradle.kts`: Change the line
`api(project(":firebase-dataconnect"))` to
`api("com.google.firebase:firebase-dataconnect:16.0.0-alpha04")` in each file (using the
version number of the release that you want to test)
* `firebase-dataconnect/connectors/connectors.gradle.kts`: add the line
`id("copy-google-services")` into the "plugins" section.
* Copy `firebase-dataconnect/google-services.json` to
`firebase-dataconnect/connectors/google-services.json` and replace
`com.google.firebase.dataconnect` with `com.google.firebase.dataconnect.connectors`.

Now, create a new directory `firebase-dataconnect/m2`. Download the "release_artifacts" from the
completed "Build Release Artifacts" action into this directory. The download link can be found at
the URL of the action
(e.g. https://github.com/firebase/firebase-android-sdk/actions/runs/9279002255).

Run these two commands to extract the build artifacts into this directory:

```
unzip release_artifacts.zip
unzip m2repository.zip
```

This should create a directory that matches the release that you want to test, for example

```
com/google/firebase/firebase-dataconnect/16.0.0-alpha05
```

Finally, run the "connectors" integration tests as you normally would, which can be done from the
command line like this in the top-level directory of the repository:

```
./gradlew :firebase-dataconnect:connectors:connectedDebugAndroidTest
```

Make sure to have the Android, Auth, and Data Connect emulators running for the tests to use.

### Deploy Release to Maven

Deploying the release to Maven must be done by the Android Core team since they are the only people
with permissions to deploy. To do this, post a message to the chat room
"The REAL Firebase Android Contrib" asking for the release, and providing the URL of the
"Build Release Artifacts" action. Here is an example:

> Can you please publish 16.0.0-alpha05 of firebase-dataconnect: https://github.com/firebase/firebase-android-sdk/actions/runs/9647143328

(e.g. https://chat.google.com/room/AAAAlocx6vc/sE8gyvofp5E)

The artifact will usually be posted on the same day and will be visible in Google's Maven server,
for example: https://maven.google.com/web/index.html#com.google.firebase:firebase-dataconnect:16.0.0-alpha05

### Announce the New Version

Announce the newly-release Android SDK version in the Google Group and Chat Room for the Data
Connect gated preview participants.

Here are examples from the `16.0.0-alpha05` release:
* https://groups.google.com/a/google.com/g/firebase-data-connect-eap-external/c/_xrxTAr4bxU
* https://chat.google.com/room/AAAAkBgPAcc/ouTpxUCdMc4

### Update the DevSite

Open a changelist to change the devsite to use the new version in sample code.
Here is an example: cl/646679734

### That's It!

And, you're done. The new version is released. Congratulations.
