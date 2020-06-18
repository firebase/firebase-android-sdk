# FCM Tokens may change.

## What happened?

The latest Cloud Messaging SDK i.e firebase-messaging:20.1.1 updates introduce a transitive dependency on the [Firebase Installations SDK](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com), a new infrastructure service for Firebase. Different to before, the Firebase Installations SDK supports [multiple projects in your application](https://firebase.google.com/docs/projects/multiprojects). 

If you use a Firebase application instance different to the default instance, the FCM registration tokens of installed instances of your applications may change one time after the migration.

Please note that if you only use the default Firebase application instance, your applications will not be affected.

## What do I need to do?

If your applications upload new FCM registration tokens to your application server after a client's FCM registration token changes, your applications are prepared and you don't have to do anything.

If you think you are not prepared, please feel free to reach out to us in order to discuss mitigation options.

Please note that if you do not upgrade your Firebase SDKs, your applications will not be affected until you do.

## How do I find out if my applications are affected?

To use FCM your application has to initialize an instance of the Firebase application (a.k.a. Firebase Common library), class name FirebaseApp. If you use a [custom name during initialization of the Firebase application](https://github.com/firebase/firebase-android-sdk/blob/1e43a8e5988a99338921f9d10b1635ec99e78bdc/firebase-common/src/main/java/com/google/firebase/FirebaseApp.java#L282-L285) your application might be affected.

## Duplicate FCM push notifications

If you determine that FCM registration tokens of installed instances of your application will change and your application server does **not** deduplicate FCM registration token, your end-users may end up receiving duplicate FCM push notifications. The best way to prevent this situation is to deduplicate FCM registration token by your end-user ID in your application server.

If this solution is not feasible for you, please [reach out to support](https://firebase.google.com/support/contact?utm_source=email&utm_medium=email&utm_campaign=firebase-installations-api-restrictions-problem), as we have ways to help you prevent duplicated FCM push notifications.

If you have any questions, [reach out to support](https://firebase.google.com/support/contact?utm_source=email&utm_medium=email&utm_campaign=firebase-installations-api-restrictions-problem) for more assistance.
