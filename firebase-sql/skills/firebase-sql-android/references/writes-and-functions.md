# Writes and functions

How to change data and call PostgreSQL functions with the `firebase-sql` SDK. `sql` is the
`FirebaseSql` instance of `Firebase.sql()`, and `Product` and `Category` are `@Serializable`
models.

## Write data

```kotlin
// Insert one row, and get it back with the values that the database filled.
val inserted =
  sql
    .from("products")
    .insert(mapOf("name" to "Kiwi", "price" to 0.5, "category_id" to 1))
    .select()
    .single()
    .execute()
    .decodeSingle<Product>()

// Insert several rows.
sql
  .from("products")
  .insert(listOf(mapOf("name" to "Lime", "price" to 0.3), mapOf("name" to "Plum", "price" to 0.4)))
  .execute()

// Insert, or update the row with the same primary key.
sql.from("categories").upsert(Category(id = 1, name = "Fruit")).execute()

// Update the rows that match the filters, and get them back.
val updated =
  sql
    .from("products")
    .update(mapOf("price" to 0.6))
    .eq("id", 42)
    .select()
    .execute()
    .decodeList<Product>()

// Delete the rows that match the filters.
sql.from("products").delete().eq("id", 42).execute()
```

- **Always filter `update()` and `delete()`.** Without a filter, they change every row that the user
  can access. Some filters match every row when their list is empty: `notIn()`, `likeAll()`,
  `ilikeAll()`, `filter()` with an `_ALL` operator, and `filterNot()` with `IN` or an `_ANY`
  operator.
- `maxAffected(1)` makes an `update()` or `delete()` fail, and change nothing, when it would change
  more than 1 row. `limit()` and `range()` do not limit the rows that an `update()` or a `delete()`
  changes, so the SDK throws an `IllegalStateException` for them there.
- A write does not return rows unless you add `select()`.
- To write a `@Serializable` object, as in the `upsert()` above, import the extension function, for
  example `import com.google.firebase.sql.builders.upsert`.

## Call PostgreSQL functions

```kotlin
// CREATE FUNCTION add_numbers(a int, b int) RETURNS int ...
val sum = sql.rpc("add_numbers", mapOf("a" to 1, "b" to 2)).execute().decodeAs<Int>()

// A function that returns rows can be filtered and ordered like a table.
// CREATE FUNCTION products_under(max_price numeric) RETURNS SETOF products ...
val cheap =
  sql
    .rpc("products_under", mapOf("max_price" to 5))
    .eq("category_id", 1)
    .order("price")
    .execute()
    .decodeList<Product>()

// A function that does not change data can be called with GET (RpcMethod.HEAD returns no body).
val sumWithGet =
  sql
    .rpc("add_numbers", mapOf("a" to 1, "b" to 2), method = RpcMethod.GET)
    .execute()
    .decodeAs<Int>()
```
