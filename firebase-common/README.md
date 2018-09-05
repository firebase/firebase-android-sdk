# Firebase Common
firebase-common contains the FirebaseApp, which is used to configure
the firebase sdks as well as the infrastructure that firebase sdks use
to discover and interact with other firebase sdks.

## Running tests.

Unit tests can be run by
```
$ ./gradlew :firebase-common:check
```

Integration tests are run by

```
$ ./gradlew :firebase-common:connectedCheck
```
