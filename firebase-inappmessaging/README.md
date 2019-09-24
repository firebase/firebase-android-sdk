# Firebase In-App Messaging SDK
[Firebase In-App Messaging](https://firebase.google.com/docs/in-app-messaging/) helps you engage
users who are actively using your app by sending them targeted and contextual messages that nudge
them to complete key in-app actions - like beating a game level, buying an item, or subscribing to
content.

The FIAM SDK manages the non-ui logic for FIAM - including fetching new eligible messages from the server, and triggering FIAM messages.

## Running Tests
Unit tests:
`../gradlew :firebase-inappmessaging:test`

Integration tests, requiring a running and connected device (emulator or real):
`../gradlew :firebase-inappmessaging:connectedAndroidTest`

The best way to the FIAM sdks is via the fiamui-app in this repo - you can run the test, or use
Firebase Test Lab to run a series of UI tests. See fiamui-app/scripts for more details