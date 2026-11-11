package com.whitelabel.core.testing

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import assertk.assertions.messageContains
import assertk.assertions.single
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class OpenApiSpecTest {

    @Test fun `the health example is valid against its own schema`() {
        val body = OpenApiSpec.responseExample("/api/v1/health", "get", 200)
        assertThat(OpenApiSpec.responseErrors("/api/v1/health", "get", 200, body)).isEmpty()
    }

    @Test fun `a body missing a required field is reported`() {
        val body = """{"success":true,"data":{"tokens":{"accessToken":"a","tokenType":"Bearer","expiresIn":900}},"meta":{"requestId":"r"}}"""
        assertThat(OpenApiSpec.responseErrors("/api/v1/auth/refresh", "post", 200, body)).single().contains("refreshToken")
    }

    @Test fun `a request body is checked against its schema`() {
        assertThat(OpenApiSpec.requestErrors("/api/v1/auth/login", "post", """{"email":"a@b.co","password":"x"}""")).isEmpty()
        assertThat(OpenApiSpec.requestErrors("/api/v1/auth/login", "post", """{"email":"a@b.co"}""")).isNotEmpty()
    }

    @Test fun `an unknown path fails naming it`() {
        assertFailure { OpenApiSpec.responseExample("/api/v1/nope", "get", 200) }.messageContains("/api/v1/nope")
    }

    @Test fun `every response example in the spec validates`() {
        val problems = OpenApiSpec.operations().flatMap { (path, method) ->
            OpenApiSpec.documentedStatuses(path, method).mapNotNull { status ->
                OpenApiSpec.responseExampleOrNull(path, method, status)?.let { example ->
                    OpenApiSpec.responseErrors(path, method, status, example).map { "$method $path $status: $it" }
                }
            }.flatten()
        }
        assertThat(problems).isEmpty()
        assertThat(OpenApiSpec.operations()).isNotEmpty()
    }
}

class TemplateTest {
    @Test fun `an id path matches its template`() {
        assertThat(OpenApiSpec.templateFor("/api/v1/catalog/abc-123")).isEqualTo("/api/v1/catalog/{id}")
        assertThat(OpenApiSpec.templateFor("/api/v1/catalog")).isEqualTo("/api/v1/catalog")
        assertThat(OpenApiSpec.templateFor("/api/v1/nope")).isNull()
    }
}
