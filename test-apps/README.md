# Test apps

This experimental directory contains apps that are used to smoke test Firebase Android projects.

## Prerequisites

- npm must be installed
- [Firebase CLI](https://github.com/firebase/firebase-tools/search?q=storage&unscoped_q=storage) needs to be installed.

## Setup
- At the root of the firebase android sdk repo, run the following command to publish all repos to the build dir.
  ```
  ./gradlew publishAllToBuildDir
  ```
  
- From the [firebase console](https://console.firebae.com) for your project, create four Android apps with the following package names

  ```
  com.google.firebase.testapps.database
  com.google.firebase.testapps.storage
  com.google.firebase.testapps.functions
  com.google.firebase.testapps.firestore
  ```

- Download the google-services.json and copy into the /test-apps directory

- Start the android emulator

- From the /test-apps dir, run the tests

  ```
  ./gradlew connectedCheck -PtestBuildType=<release|debug> -PfirebaseProjectId=<your_project_id> -PfirebaseToken=<your_firebase_token> -Pm2Repository=${PWD}/../build/m2repository/
  ```
