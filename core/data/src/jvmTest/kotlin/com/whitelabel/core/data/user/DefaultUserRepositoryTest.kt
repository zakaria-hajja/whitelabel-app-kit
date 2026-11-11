package com.whitelabel.core.data.user

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.Result
import com.whitelabel.core.data.USER_JSON
import com.whitelabel.core.data.bodyText
import com.whitelabel.core.data.mockClient
import com.whitelabel.core.data.ok
import com.whitelabel.core.domain.User
import java.io.File
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DefaultUserRepositoryTest {

    @TempDir
    lateinit var dir: File

    private suspend fun <R> withCache(block: suspend (UserCache) -> R): R {
        val job = Job()
        return try {
            block(UserCache(File(dir, "user.json").toOkioPath(), CoroutineScope(Dispatchers.IO + job)))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `refresh caches the fetched user`() = runTest {
        withCache { cache ->
            val repository = DefaultUserRepository(mockClient { ok(USER_JSON) }, cache)
            assertThat(repository.refresh()).isEqualTo(Result.Success(Unit))
            assertThat(repository.user.first()).isEqualTo(User("u1", "ada@example.com", "Ada", null))
        }
    }

    @Test fun `cached user survives a new repository instance`() = runTest {
        withCache { cache -> DefaultUserRepository(mockClient { ok(USER_JSON) }, cache).refresh() }
        val user = withCache { cache -> DefaultUserRepository(mockClient { error("offline") }, cache).user.first() }
        assertThat(user).isEqualTo(User("u1", "ada@example.com", "Ada", null))
    }

    @Test fun `clearing a name sends an explicit null`() = runTest {
        var body: String? = null
        withCache { cache ->
            DefaultUserRepository(mockClient { request -> body = request.bodyText; ok(USER_JSON) }, cache).update(firstName = null, lastName = "Doe")
        }
        assertThat(body).isEqualTo("""{"firstName":null,"lastName":"Doe"}""")
    }

    @Test fun `update caches the returned user`() = runTest {
        withCache { cache ->
            val updated = USER_JSON.replace("\"Ada\"", "\"Grace\"")
            val repository = DefaultUserRepository(mockClient { ok(updated) }, cache)
            assertThat(repository.update(firstName = "Grace", lastName = null)).isEqualTo(Result.Success(Unit))
            assertThat(repository.user.first()?.firstName).isEqualTo("Grace")
        }
    }
}
