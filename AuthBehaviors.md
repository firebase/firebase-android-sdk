# Data Connect Auth and App Check Behaviors

## 1. App check enforcing - placeholder token provided

GrpcBidiFlow: ClientCall.Listener.onClose() called with Status{
code=16 (UNAUTHENTICATED),
cause=null,
description="Request is missing required authentication credential. Expected OAuth 2 access token, login cookie or other valid authentication credential. See https://developers.google.com/identity/sign-in/web/devconsole-project."
}

## 2. App check enforcing - no token provided

(same as "1. App check enforcing - placeholder token provided")

GrpcBidiFlow: ClientCall.Listener.onClose() called with Status{
code=16 (UNAUTHENTICATED),
cause=null,
description="Request is missing required authentication credential. Expected OAuth 2 access token, login cookie or other valid authentication credential. See https://developers.google.com/identity/sign-in/web/devconsole-project."
}

## 3. App check enforcing - expired token provided

## 4. App check NOT enforcing - enforcing enabled during streaming connection

## 5. Auth token expires during streaming of `@auth(level: USER_ANON)` query

## 6. No auth token initiates streaming of `@auth(level: USER_ANON)` query

## 7. Expired auth token initiates streaming of `@auth(level: USER_ANON)` query

