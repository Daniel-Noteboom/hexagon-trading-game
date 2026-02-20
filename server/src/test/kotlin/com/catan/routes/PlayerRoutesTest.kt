package com.catan.routes

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.*

class PlayerRoutesTest {

    @Test
    fun `POST players register with displayName returns 200 with playerId and token`() = testApp { client, _, _ ->
        val response = client.post("/players/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Alice"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RegisterResponse>()
        assertTrue(body.playerId.isNotBlank())
        assertTrue(body.sessionToken.isNotBlank())
        assertEquals("Alice", body.displayName)
    }

    @Test
    fun `POST players register without displayName returns 400`() = testApp { client, _, _ ->
        val response = client.post("/players/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(""))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST players register trims whitespace`() = testApp { client, _, _ ->
        val response = client.post("/players/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("  Bob  "))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RegisterResponse>()
        assertEquals("Bob", body.displayName)
    }
}
