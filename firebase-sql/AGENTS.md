# Working on the Firebase SQL Android SDK (`firebase-sql`)

This guide is for AI agents and developers who change the code of this module. It adds the details
of `firebase-sql` to the [repository guide](../AGENTS.md), which covers the setup, the build, and
the conventions of every SDK in this repository. To write app code that uses the SDK, use the
[`firebase-sql-android` skill](skills/firebase-sql-android/SKILL.md) instead.

## What the SDK does

A Firebase SQL service serves a Cloud SQL for PostgreSQL database through a
[PostgREST](https://docs.postgrest.org/en/stable/references/api.html) API. In the SDK, a builder
chain such as `sql.from("products").select().eq("id", 1)` describes one PostgREST request.
`execute()` sends it with the Firebase Authentication and Firebase App Check tokens of the app,
retries reads after transient failures, and returns an `OperationResult`, or throws a
`FirebaseSqlException` when the server answers with an error.

## Layout

- `src/main/kotlin/com/google/firebase/sql/`:
  - The public API: `FirebaseSql`, `Firebase.sql()` in `Sql.kt`, `SqlServiceSettings`,
    `OperationResult`, the exceptions, and the classes of constants.
  - `FirebaseSqlComponent.kt`: creates and reuses the `FirebaseSql` instances of one `FirebaseApp`,
    and closes them when that app is deleted. `FirebaseSqlRegistrar.kt` registers it.
  - `builders/`: `QueryBuilder`, `FilterBuilder`, and `TransformBuilder`, which
    `OperationBuilderImpl` implements. `PostgrestSyntax.kt` formats the values of the query string.
  - `core/`: `OperationsExecutor` (headers, tokens, retries, timeouts, and cancellation),
    `NetworkClient` (OkHttp and error responses), the token managers, `QueryRequest`, and the JSON
    encoding of values.
- `src/test/`: unit tests, which run with Robolectric. `MockServerEndToEndTest` runs the whole SDK
  against a `MockWebServer`.
- `src/androidTest/`: `CloudSqlEndToEndTest`, which runs against a live service, and the SQL of its
  tables in `src/androidTest/sql/`.
- `api.txt`: the public API, which the build checks.

## Checks

Run these commands from the root of the repository after a change:

```bash
./gradlew :firebase-sql:spotlessApply
./gradlew :firebase-sql:check
```

- When the public API changes, update `api.txt` with `./gradlew :firebase-sql:generateApiTxtFile`.
- When a change is visible to developers, add an entry to [CHANGELOG.md](CHANGELOG.md), as the
  repository guide describes.

## Conventions

- **Public API:** `FirebaseSql`, `SqlServiceSettings`, `OperationResult`, `FirebaseSqlException`,
  `FirebaseSqlExceptionCategory`, `ReservedHeaderException`, `Count`, `Order`, `TextSearchType`,
  `RpcMethod`, `FilterOperator`, `ExplainFormat`, `builders/QueryBuilder`, `builders/FilterBuilder`,
  and `builders/TransformBuilder`. The other classes are internal.
- **Kotlin only:** the API is built on coroutines and kotlinx.serialization, and is not designed to
  be called from Java. `execute()` is a suspend function.
- **Public interfaces:** `FirebaseSql` and the three builders are marked
  `@SubclassOptInRequired(InternalForInheritanceFirebaseApi::class)`, so that apps can't implement
  them without an opt-in, and minor versions can add methods to them. The module opts in for its
  own implementations in `firebase-sql.gradle.kts`. Give a new public interface the same
  annotation.
- **Public constants:** `Count`, `FilterOperator`, `FirebaseSqlExceptionCategory`, `RpcMethod`,
  `TextSearchType`, and `ExplainFormat` are classes with a private constructor and their constants
  in the companion object, not enums, so that minor versions can add constants. Each constant holds
  the value that the SDK sends in `rawValue`, which `toString()` returns. Give a new set of
  constants the same form. Only a set that can never grow, such as `Order`, is an enum.
- **Documentation:** every public declaration has KDoc that a reader can use without other
  documentation: what it does, the PostgREST request that it produces, its errors, and examples
  that compile. KDoc and comments describe the behavior itself, and do not refer to other SDKs;
  PostgREST and PostgreSQL are fine. When a change affects how apps use the SDK, update the
  [`firebase-sql-android` skill](skills/firebase-sql-android/SKILL.md) too.

## Run the tests against a live service

`CloudSqlEndToEndTest` runs the SDK on a device or emulator against a Firebase SQL service in
production, through `Firebase.sql()` like an app. The service must not enforce App Check, because
the tests send no App Check token. `testMultipleSchemas` is ignored, because the service exposes
only its own schema. The dry-run tests and `testExplain` need the settings that `e2e_setup.sql`
puts in `postgrest.pre_config()`.

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
- **The service** comes from the required Gradle properties `targetServiceId` and
  `targetLocation`, which the build compiles into `BuildConfig`. `run_cloud_tests.sh` reads the
  environment variables `FIREBASE_SQL_SERVICE_ID` and `FIREBASE_SQL_LOCATION`, and passes them on.
- **The Cloud SQL instance and database** of the service are looked up by `run_cloud_tests.sh` with
  `firebase dataconnect:services:list`.

### Run the tests

When the test tables already exist in the database, run `connectedCheck` from the root of the
repository:

```bash
./gradlew :firebase-sql:connectedCheck -PtargetServiceId=my-service -PtargetLocation=us-east4
```

To create the test tables in the database first, or to create them and run `connectedCheck` in one
step, run [`run_cloud_tests.sh`](run_cloud_tests.sh):

```bash
FIREBASE_SQL_SERVICE_ID=my-service FIREBASE_SQL_LOCATION=us-east4 ./firebase-sql/run_cloud_tests.sh
```

### Run the tests from Android Studio

Add the Gradle properties to your `~/.gradle/gradle.properties`, which is not part of the
repository:

```properties
targetServiceId=my-service
targetLocation=us-east4
```

Then click the run icon next to `CloudSqlEndToEndTest`. The results show up in the test tree of the
**Run** window.

If the test tables do not exist yet, create them once from the terminal with
`RUN_TESTS=false ./firebase-sql/run_cloud_tests.sh`, with `FIREBASE_SQL_SERVICE_ID` and
`FIREBASE_SQL_LOCATION` set.

### Run the tests on CI

The **Instrumentation Tests (:firebase-sql)** job of [`ci_tests.yml`](../.github/workflows/ci_tests.yml)
runs `deviceCheck` for every pull request that affects this module, and after every merge to
`main`. This module enables `testLab`, so `deviceCheck` runs `CloudSqlEndToEndTest` on a Firebase
Test Lab device. Pull requests from forks skip the job, because they cannot read the secrets. The
project comes from the `INTEG_TESTS_GOOGLE_SERVICES` secret, and the service from the
`FIREBASE_SQL_SERVICE_ID` and `FIREBASE_SQL_LOCATION` secrets, which the job passes as the
`targetServiceId` and `targetLocation` Gradle properties.

CI does not create the test tables. When a change adds to `src/androidTest/sql/`, apply it to the
database of the CI service with `RUN_TESTS=false ./firebase-sql/run_cloud_tests.sh`, so that CI
passes.
