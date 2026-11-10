# Firebase SQL Android SDK (`firebase-sql`)

Firebase SQL serves a Cloud SQL for PostgreSQL database through a
[PostgREST](https://docs.postgrest.org/en/stable/) API. This SDK lets an Android app read and write
that database with a fluent Kotlin API:

- **PostgREST queries in Kotlin:** `from`, `select`, `insert`, `update`, `upsert`, `delete`, `rpc`,
  filters such as `eq` and `ilike`, and modifiers such as `order`, `range`, and `single`. Each call
  maps to one part of a PostgREST request, so queries behave as described in the
  [PostgREST API reference](https://docs.postgrest.org/en/stable/references/api.html).
- **Firebase Authentication and Firebase App Check:** each request carries the ID token of the
  signed-in user and an App Check token, without any code in the app.
- **Typed results:** decode rows into `@Serializable` Kotlin classes, and catch a
  `FirebaseSqlException`, with the code and the message of the server, for every error response.
- **Reliable requests:** reads are retried after transient failures, each attempt has a time limit,
  and cancelling the coroutine cancels the request.

The API is Kotlin-only: it is built on Kotlin coroutines and kotlinx.serialization, and is not
designed to be called from Java.

## Quick start

1. Add the SDK, kotlinx.serialization, and Kotlin coroutines to the app, and apply the
   `org.jetbrains.kotlin.plugin.serialization` and `com.google.gms.google-services` plugins. The
   project ID comes from the `google-services.json` of the app.
2. Get the instance for your service with `Firebase.sql()`, then build and execute queries in a
   coroutine:

```kotlin
@Serializable
data class Product(
  val id: Long,
  val name: String,
  val price: Double,
  @SerialName("category_id") val categoryId: Long? = null,
)

val sql =
  Firebase.sql(SqlServiceSettings(serviceId = "my-service", location = "us-central1"))

// GET /products?select=*&category_id=eq.1&order=price.asc&limit=10
try {
  val products =
    sql.from("products").select().eq("category_id", 1).order("price").limit(10).execute()
  showProducts(products.decodeList<Product>())
} catch (e: FirebaseSqlException) {
  showError(e) // The server rejected the request.
} catch (e: IOException) {
  showOffline(e) // No response, for example because the device is offline.
}

// Insert a row and get it back.
val kiwi =
  sql
    .from("products")
    .insert(mapOf("name" to "Kiwi", "price" to 0.5, "category_id" to 1))
    .select()
    .single()
    .execute()
    .decodeSingle<Product>()
```

## Documentation

- [AGENTS.md](AGENTS.md): the full guide, written for developers and AI agents. It covers setting up
  the database and the project, models, settings, reads, writes, functions, errors, and retries.
- KDoc: every public class and method documents its behavior, the PostgREST request it produces,
  and its errors, with examples.

## Build and test

```bash
./gradlew :firebase-sql:spotlessApply
./gradlew :firebase-sql:check
```

When the public API changes, update `api.txt` with
`./gradlew :firebase-sql:generateApiTxtFile`.

## Run the tests against a live service

`CloudSqlEndToEndTest` runs the SDK on a device or emulator against a Firebase SQL service in
production, through `Firebase.sql()` like an app. The service must not enforce App Check,
because the tests send no App Check token. `testMultipleSchemas` and `testExplain` are ignored: the
service exposes only its own schema, and does not return execution plans.

### Prerequisites

```bash
brew install cloud-sql-proxy libpq
gcloud auth login
firebase login
```

`run_cloud_tests.sh` creates the test tables as your Google account, which needs the owner role of
the database of the service once. Run this from a directory with the `firebase.json` and
`dataconnect.yaml` of the service:

```bash
firebase dataconnect:sql:grant -R owner -E <your email> --service <service ID> --location <location>
```

### Configuration

The test resources are not hard-coded:

- **The project** comes from a `google-services.json` file. Set `FIREBASE_GOOGLE_SERVICES_PATH` to
  the file of the project of the service, or put it at the root of the repository. The build copies
  it into this directory with the package name of the module, so the file does not need to be
  edited. The copy is kept until it is deleted; `run_cloud_tests.sh` replaces it when
  `FIREBASE_GOOGLE_SERVICES_PATH` is set.
- **The service** comes from the environment variables `FIREBASE_SQL_SERVICE_ID` and
  `FIREBASE_SQL_LOCATION`. The Gradle build passes them to the tests as the instrumentation
  arguments `firebaseSqlServiceId` and `firebaseSqlLocation`.
- **The Cloud SQL instance and database** of the service are looked up by `run_cloud_tests.sh` with
  `firebase dataconnect:services:list`.

### Run the tests

When the test tables already exist in the database, run `connectedCheck` from the root of the
repository:

```bash
FIREBASE_SQL_SERVICE_ID=my-service FIREBASE_SQL_LOCATION=us-east4 ./gradlew :firebase-sql:connectedCheck
```

To create the test tables in the database first (or to create them and run `connectedCheck` in one
step), run [`run_cloud_tests.sh`](run_cloud_tests.sh):

```bash
FIREBASE_SQL_SERVICE_ID=my-service FIREBASE_SQL_LOCATION=us-east4 ./firebase-sql/run_cloud_tests.sh
```

### Run the tests from Android Studio

Click the run icon next to `CloudSqlEndToEndTest`. Android Studio creates an **Android Instrumented
Tests** run configuration, and the first run fails because the configuration does not name the
service yet. Open **Run > Edit Configurations**, select that configuration, and add the
**Instrumentation arguments** `firebaseSqlServiceId` and `firebaseSqlLocation` with the ID and
location of the service. The results then show up in the test tree of the **Run** window.

If the test tables do not exist yet, create them once from the terminal with
`RUN_TESTS=false ./firebase-sql/run_cloud_tests.sh`, with `FIREBASE_SQL_SERVICE_ID` and
`FIREBASE_SQL_LOCATION` set.

