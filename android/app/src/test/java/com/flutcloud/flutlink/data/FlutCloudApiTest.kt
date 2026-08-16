package com.flutcloud.flutlink.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the OCS meta parser in [FlutCloudApi]. */
class FlutCloudApiTest {

    private val api = FlutCloudApi(OkHttpClient())

    @Test
    fun `parseOcs returns data for a successful response`() {
        val body = """{"ocs":{"meta":{"status":"ok","statuscode":100,"message":"OK"},"data":{"id":"admin","display-name":"Admin"}}}"""

        val (data, error) = api.parseOcs(body)

        assertNull(error)
        assertNotNull(data)
        assertEquals("admin", data!!.jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parseOcs accepts numeric statuscode as success`() {
        val body = """{"ocs":{"meta":{"status":"failure","statuscode":200},"data":null}}"""

        val (data, error) = api.parseOcs(body)

        assertNull(error)
        assertTrue(data is JsonNull)
    }

    @Test
    fun `parseOcs returns the failure message`() {
        val body = """{"ocs":{"meta":{"status":"failure","statuscode":998,"message":"Wrong user or password."},"data":null}}"""

        val (data, error) = api.parseOcs(body)

        assertTrue(data is JsonNull)
        assertEquals("Wrong user or password.", error)
    }

    @Test
    fun `parseOcs falls back to a generic message`() {
        val body = """{"ocs":{"meta":{"status":"failure","statuscode":404},"data":null}}"""

        val (data, error) = api.parseOcs(body)

        assertTrue(data is JsonNull)
        assertEquals("Unknown OCS error", error)
    }

    @Test
    fun `parseOcs rejects invalid json`() {
        val (data, error) = api.parseOcs("not json at all")

        assertNull(data)
        assertEquals("Invalid OCS response", error)
    }
}