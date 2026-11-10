# Queries

How to read data with the `firebase-sql` SDK. `sql` is the `FirebaseSql` instance of
`Firebase.sql()`, and `Product` and `Category` are `@Serializable` models.

## Read rows

```kotlin
import com.google.firebase.sql.Order

// All the rows and columns.
val products = sql.from("products").select().execute().decodeList<Product>()

// Some columns, filters, order, and a limit.
val cheapFruit =
  sql
    .from("products")
    .select("id, name, price")
    .eq("category_id", 1)
    .lt("price", 2)
    .order("price", Order.DESCENDING)
    .limit(10)
    .execute()
    .decodeList<Product>()

// Exactly one row: single() fails with PGRST116 when there are 0 or several rows.
val product =
  sql.from("products").select().eq("id", 42).single().execute().decodeSingle<Product>()

// At most one row: null when there is none.
val maybe =
  sql.from("products").select().eq("name", "Kiwi").maybeSingle().execute()
    .decodeSingleOrNull<Product>()
```

`decodeSingle()` and `decodeSingleOrNull()` read one row: the object that `single()` or
`maybeSingle()` returns, or an array with exactly one row. They throw when the data has several
rows, instead of taking the first.

## Filters

Chained filters are combined with `AND`. Filters come after `select()`, `update()`, or `delete()`.
Write them before the modifiers, such as `order()` and `limit()`. A filter can still follow
`order()`, `limit()`, `range()`, `setHeader()`, and the other modifiers that return a
`FilterBuilder`, but not `single()`, `maybeSingle()`, `csv()`, `geojson()`, or `explain()`, which
return a `TransformBuilder`.

|                           Kotlin                           |           PostgREST            |              Meaning              |
|------------------------------------------------------------|--------------------------------|-----------------------------------|
| `eq("name", "Kiwi")`                                       | `name=eq.Kiwi`                 | equal                             |
| `neq("name", "Kiwi")`                                      | `name=neq.Kiwi`                | not equal                         |
| `gt("price", 10)`, `gte(...)`, `lt(...)`, `lte(...)`       | `price=gt.10`                  | greater, less                     |
| `like("name", "%apple%")`, `ilike(...)`                    | `name=like.%apple%`            | pattern, `ilike` ignores case     |
| `isIn("id", listOf(1, 2, 3))`                              | `id=in.(1,2,3)`                | in a list                         |
| `exact("deleted_at", null)`                                | `deleted_at=is.null`           | is `NULL`, `true`, or `false`     |
| `filterNot("deleted_at", FilterOperator.IS, null)`         | `deleted_at=not.is.null`       | negates an operator               |
| `contains("tags", listOf("sale"))`                         | `tags=cs.{sale}`               | the array contains all the values |
| `overlaps("tags", listOf("sale", "new"))`                  | `tags=ov.{sale,new}`           | the arrays have a common value    |
| `textSearch("fts", "fresh fruit", TextSearchType.PLAINTO)` | `fts=plfts.fresh fruit`        | full-text search                  |
| `or("price.lt.2,name.eq.Kiwi")`                            | `or=(price.lt.2,name.eq.Kiwi)` | any condition matches             |
| `filter("price", FilterOperator.GTE, 10)`                  | `price=gte.10`                 | any PostgREST operator            |
| `filter("price", FilterOperator.LT_ALL, listOf(2, 3))`     | `price=lt(all).{2,3}`          | less than every element of a list |

## Build a query step by step

Keep the query in one `var`, and assign the result of each call to it:

```kotlin
suspend fun search(maxPrice: Double?, cheapFirst: Boolean): List<Product> {
  var query = sql.from("products").select()
  if (maxPrice != null) query = query.lte("price", maxPrice)
  if (cheapFirst) query = query.order("price")
  return query.limit(20).execute().decodeList<Product>()
}
```

## Related tables

Select the columns of a related table through a foreign key with `table(columns)`. Add `!inner` to
keep only the rows that have a related row, and filter on its columns with `table.column`.

```kotlin
@Serializable data class ProductWithCategory(val id: Long, val name: String, val categories: Category?)

val fruit =
  sql
    .from("products")
    .select("id, name, categories!inner(id, name)")
    .eq("categories.name", "Fruit")
    .execute()
    .decodeList<ProductWithCategory>()
```

Response: `[{"id": 1, "name": "Kiwi", "categories": {"id": 1, "name": "Fruit"}}]`

## Count and pages

```kotlin
// The rows 0 to 19, and the total number of matching rows.
val result =
  sql.from("products").select(count = Count.EXACT).order("id").range(0, 19).execute()
val page = result.decodeList<Product>()
val total = result.countOrNull()

// Only the count, without the rows.
val count = sql.from("products").select(head = true, count = Count.EXACT).execute().countOrNull()
```
