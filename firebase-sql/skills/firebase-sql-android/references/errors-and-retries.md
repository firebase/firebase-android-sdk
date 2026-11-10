# Errors and retries

How the `firebase-sql` SDK reports errors, and how it retries, times out, and cancels requests.

## Handle errors

```kotlin
try {
  show(sql.from("products").select().execute().decodeList<Product>())
} catch (e: FirebaseSqlException) {
  // The server answered with an HTTP error.
  Log.e(TAG, "HTTP ${e.status} ${e.code}: ${e.message} ${e.hint}")
} catch (e: IOException) {
  // No HTTP response: no network, or a timeout.
  Log.e(TAG, "No response", e)
}
```

- `FirebaseSqlException` is only for the errors of the server, and for the errors that the SDK finds
  in a successful response. Other errors are thrown as they are:
  - no HTTP response: the `IOException` of the network, or an `InterruptedIOException` for a
    timeout;
  - no Firebase Authentication token for the signed-in user: the error of Firebase Authentication,
    and the request is not sent;
  - data that does not fit the type: the `SerializationException` of kotlinx.serialization, from
    `decodeList()`, `decodeSingle()`, `decodeSingleOrNull()`, and `decodeAs()`.
- `decodeSingle()` throws a `NoSuchElementException` when there is no row. `decodeSingle()` and
  `decodeSingleOrNull()` throw an `IllegalArgumentException` for several rows. `decodeList()`,
  `decodeSingle()`, and `decodeSingleOrNull()` throw an `IllegalStateException` for a response
  without body, for example a mutation without `select()`.
- `FirebaseSqlException.category`, a `FirebaseSqlExceptionCategory`, tells where an error comes
  from: `POSTGREST` (PostgREST or PostgreSQL), `PLATFORM` (the Firebase SQL service, for example an
  invalid token), or `CLIENT` (the SDK found the error in a successful response, for example a
  `dryRun()` whose rollback the server did not confirm). Later versions can add categories, so
  give a `when` expression over the category an `else` branch.
- `IllegalArgumentException` and `IllegalStateException` mean a programming error, for example
  invalid settings, a reserved header (`ReservedHeaderException`), or a call on a terminated instance.

|    Code    |   Status   |                                Meaning                                |
|------------|------------|-----------------------------------------------------------------------|
| `PGRST205` | 404        | The table does not exist, or the API does not see it yet.             |
| `42703`    | 400        | A column does not exist.                                              |
| `23505`    | 409        | A unique constraint is violated, for example a duplicate primary key. |
| `23503`    | 409        | A foreign key constraint is violated.                                 |
| `42501`    | 401 or 403 | The user has no permission for the table.                             |
| `PGRST116` | 406        | `single()` found 0 or several rows.                                   |
| `PGRST124` | 400        | A write would change more rows than `maxAffected()` allows.           |

## Retries, timeouts, and cancellation

- **Retries:** a `GET` or `HEAD` request, for example `select()`, is sent again up to 3 times, after
  about 1, 2, and 4 seconds (25% more or less at random), when the network fails or the server
  answers 502 or 503. The other errors, such as 429 or 504, are not retried, and the `Retry-After`
  header is ignored. Requests that can change data are never retried. Call `noRetry()` to send one
  request only once.
- **Rejected tokens:** when the server answers `401` because it rejects the Firebase Authentication
  or App Check token of a request, the SDK refreshes the tokens and sends the request once more,
  whatever its method, even with `noRetry()`. Do not add your own retry for `401`. A `401` for a
  database permission error, `42501`, is returned as it is: sign the user in, or allow the access
  to the table in the database.
- **Timeouts:** each attempt of a request can take up to 100 seconds, longer than the 90 seconds
  after which the server cancels a request. A request that times out is not sent again, and throws
  an `InterruptedIOException`, which is an `IOException`. To give up sooner, wrap the call in
  `withTimeoutOrNull`, for example
  `withTimeoutOrNull(5_000) { sql.from("products").select().execute() }`.
- **Cancellation:** cancel the coroutine that calls `execute()` to cancel the request. The server
  may still have applied a write that timed out or was cancelled, so read the data again before you
  repeat the write.
