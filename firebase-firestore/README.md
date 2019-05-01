# firebase-firestore

This is the Cloud Firestore component of the Firebase Android SDK.

Cloud Firestore is a flexible, scalable database for mobile, web, and server
development from Firebase and Google Cloud Platform. Like Firebase Realtime
Database, it keeps your data in sync across client apps through realtime
listeners and offers offline support for mobile and web so you can build
responsive apps that work regardless of network latency or Internet
connectivity. Cloud Firestore also offers seamless integration with other
Firebase and Google Cloud Platform products, including Cloud Functions.

All Gradle commands should be run from the source root (which is one level up
from this folder). See the README.md in the source root for instructions on
publishing/testing Cloud Firestore.

## Testing

After importing the project into Android Studio and building successfully
for the first time, Android Studio will delete the run configuration xml files
in `./idea/runConfigurations`. Undo these changes with the command:

```
$ git checkout .idea/runConfigurations
```
