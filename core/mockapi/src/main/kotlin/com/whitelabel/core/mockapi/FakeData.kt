package com.whitelabel.core.mockapi

import java.time.Instant

/** What the in-memory backend starts with: the same seeded users and password as the real one. */
object FakeData {
    const val PASSWORD = "Password123!"
    const val ACCESS_TTL_SECONDS = 900L
    const val ITEM_COUNT = 25
    val CATEGORIES = listOf("Books", "Games", "Music", "Travel")

    internal val SEEDED_AT: Instant = Instant.parse("2025-12-01T09:30:00Z")

    internal data class User(
        val id: String,
        val email: String,
        var password: String,
        var firstName: String?,
        var lastName: String?,
        val createdAt: Instant,
        var theme: String = "SYSTEM",
        var language: String = "en",
        val notifications: MutableMap<String, Boolean> = mutableMapOf("push" to true, "email" to true, "marketing" to false),
    )

    internal data class Item(
        val id: String,
        val title: String,
        val description: String?,
        val category: String,
        val imageUrl: String?,
        val createdAt: Instant,
    )

    internal fun users(): List<User> = listOf(
        User(uuid(101), "alice@shared.test", PASSWORD, "Alice", "Anderson", SEEDED_AT),
        User(uuid(102), "bob@shared.test", PASSWORD, "Bob", "Brown", SEEDED_AT),
    )

    /** `Item 1`…`Item 25`, categories in rotation, one day apart (newest is `Item 25`). */
    internal fun items(): List<Item> = (1..ITEM_COUNT).map { n ->
        Item(
            id = itemId(n),
            title = "Item $n",
            description = if (n % 5 == 0) null else "Sample item $n",
            category = CATEGORIES[(n - 1) % CATEGORIES.size],
            imageUrl = null,
            createdAt = SEEDED_AT.plusSeconds(86_400L * n),
        )
    }

    /** The id of seeded `Item [n]`, e.g. for a deep link in a test. */
    fun itemId(n: Int): String = uuid(300 + n)

    internal fun uuid(n: Int): String = "01950000-0000-7000-8000-%012d".format(n)
}
