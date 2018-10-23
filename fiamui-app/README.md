# FIAMUI-App SDK
This module contains a test-app for Firebase In-App Messaging's Display SDK 
(see the firebase-inappmessaging-display module).

[Firebase In-App Messaging](https://firebase.google.com/docs/in-app-messaging/) helps you engage 
users who are actively using your app by sending them targeted and contextual messages that nudge 
them to complete key in-app actions - like beating a game level, buying an item, or subscribing to 
content.

The FIAM Display SDK gives you more control over your in-app messages you send, allowing you to 
customize typeface, colors, transitions, corner radii, and more.

## Running Tests
Unit tests:
`../gradlew :fiamui-app:test`

Integration tests, requiring a running and connected device (emulator or real):
`../gradlew :fiamui-app:connectedAndroidTest`

The best way to test is via the fiamui-app in this repo - you can run the test, or use 
Firebase Test Lab to run a series of UI tests. See fiamui-app/scripts for more details