# API-Key restrictions may need to be updated with new version of Firebase Android SDKs.

## What happened?

The following SDKs updates introduce a dependency on the [Firebase Installations API](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com), a new infrastructure service for Firebase:

- Analytics
- Cloud Messaging
- Remote Config
- In-App Messaging
- A/B Testing
- Performance Monitoring
- ML Kit
- Instance ID

As a result, API restrictions you may have applied to API keys used by your Firebase applications may have to be updated to allow your apps to call the [Firebase Installations API](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com).

## What do I need to do?

Before upgrading your application(s) to the latest SDK version, please **make sure that the API key(s) used in your application(s) are whitelisted for the Firebase Installations API:**

1. **Open** the [Google Cloud Platform Console](https://console.cloud.google.com/apis/credentials?folder).
1. **Choose** the project you use for your application(s).
1. **Open**  `APIs & Services` and **select** `Credentials`.
1. **Click** `Edit API Key` (pencil icon) for the API key in question.
1. **Scroll down** to the `API restrictions` section.
1. From the dropdown menu, **add** the `Firebase Installations API` to the list of permitted APIs, and click `Save`.
1. If the radio button shows `Don't restrict key`, you may be looking at the wrong API key. \
   You can check which API key is used for the Firebase Installations API by looking at the [service usage page for your project](https://console.cloud.google.com/apis/api/firebaseinstallations.googleapis.com/credentials).

**Note**: **Verify** your fix by checking if you can see successful `200` requests increasing on the [Firebase Installations API request metrics page](https://console.cloud.google.com/apis/api/firebaseinstallations.googleapis.com/metrics). \
**Note**: If you cannot find the Firebase Installations API in the list of APIs, you might first have to enable the API for your project (to do so [click here](https://console.cloud.google.com/apis/library/firebaseinstallations.googleapis.com)).
