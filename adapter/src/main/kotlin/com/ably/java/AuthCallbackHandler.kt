package com.ably.java

import io.ably.lib.rest.Auth.TokenCallback
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class AuthCallbackHandler {

    val httpClient: HttpClient = HttpClient {}
    val json = Json

    fun createTokenRequest(authCallbackId: String): TokenCallback {
        return TokenCallback { tokenParams ->
            runBlocking {
                val responseBody = httpClient.post("http://localhost:3000/auth-callback") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("jsonrpc", JsonPrimitive("2.0"))
                            put("id", generateId())
                            put("method", "authCallback")
                            put("params", buildJsonObject {
                                put("authCallbackId", authCallbackId)
                                if (tokenParams != null) {
                                    put("tokenParams", tokenParams.toJsonElement())
                                }
                            })
                        }.toString()
                    )
                }.bodyAsText()
                val responseJson = json.parseToJsonElement(responseBody)
                val result = responseJson.jsonObject["result"]!!.jsonObject
                val type = result["type"]!!.jsonPrimitive.content
                val tokenResponse = result["response"]!!
                return@runBlocking when (type) {
                    "TokenRequest" -> tokenResponse.asTokenRequest()
                    "TokenDetails" -> tokenResponse.asTokenDetails()
                    "string" -> tokenResponse.asString()
                    else -> throw IllegalStateException("Unexpected token type: $type")
                }
            }
        }
    }
}
