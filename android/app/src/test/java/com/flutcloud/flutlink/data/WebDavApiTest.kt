package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.WebDavEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the hand-written WebDAV multistatus parser. */
class WebDavApiTest {

    private val basePath = "/remote.php/dav/files/admin"

    @Test
    fun `parses a multistatus document`() {
        val body = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/admin/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/admin/Photos/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getlastmodified>Thu, 13 Aug 2026 12:00:00 GMT</d:getlastmodified>
                    <d:getetag>"abcdef"</d:getetag>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/admin/resources/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/admin/Parts/Data.bin</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>2048</d:getcontentlength>
                    <d:getlastmodified>Wed, 12 Aug 2026 08:00:00 GMT</d:getlastmodified>
                    <d:getetag>"xyz"</d:getetag>
                    <d:getcontenttype>application/octet-stream</d:getcontenttype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        assertEquals(3, entries.size)

        val photos = entries.first { it.name == "Photos" }
        assertTrue(photos.isDir)
        assertEquals("/Photos", photos.path)
        assertEquals("\"abcdef\"", photos.etag)
        assertFalse(photos.isResource)
        assertFalse(photos.isPart)

        val resources = entries.first { it.name == "resources" }
        assertTrue(resources.isResource)
        assertFalse(resources.isPart)
        assertNull(resources.linkTarget)
        assertEquals("/parts", resources.pairedPath)

        val data = entries.first { it.name == "Data.bin" }
        assertFalse(data.isDir)
        assertEquals(2048L, data.size)
        assertTrue(data.isPart)
        assertEquals("/Parts/Data.bin", data.path)
        assertEquals("/resources/Data.bin", data.pairedPath)
        assertEquals("/resources/Data.bin", data.linkTarget)
        assertEquals("application/octet-stream", data.contentType)
    }

    @Test
    fun `parses absolute hrefs`() {
        val body = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>https://host/remote.php/dav/files/admin/My%20Folder/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>http://host:8080/remote.php/dav/files/admin/Data.bin</d:href>
                <d:propstat>
                  <d:prop><d:getcontentlength>10</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        assertEquals(2, entries.size)
        val folder = entries.first { it.isDir }
        assertEquals("My Folder", folder.name)
        assertEquals("/My Folder", folder.path)
        val file = entries.first { !it.isDir }
        assertEquals("Data.bin", file.name)
        assertEquals("/Data.bin", file.path)
        assertEquals(10L, file.size)
    }

    @Test
    fun `does not match a longer base path prefix`() {
        val body = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/admin2/foo.txt</d:href>
                <d:propstat>
                  <d:prop><d:getcontentlength>1</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        assertEquals(1, entries.size)
        assertEquals("foo.txt", entries[0].name)
        assertEquals("/remote.php/dav/files/admin2/foo.txt", entries[0].path)
    }

    @Test
    fun `decodes percent-encoded names`() {
        val body = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/admin/My%20Folder/Bericht%20%26%20Co.pdf</d:href>
                <d:propstat>
                  <d:prop><d:getcontentlength>7</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        assertEquals(1, entries.size)
        assertEquals("Bericht & Co.pdf", entries[0].name)
        assertEquals("/My Folder/Bericht & Co.pdf", entries[0].path)
    }

    @Test
    fun `keeps literal plus signs intact`() {
        val body = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/admin/a+b.txt</d:href>
                <d:propstat>
                  <d:prop><d:getcontentlength>1</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        assertEquals(1, entries.size)
        assertEquals("a+b.txt", entries[0].name)
    }

    @Test
    fun `parses a no-prefix namespace document`() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/remote.php/dav/files/admin/Notes/</href>
                <propstat>
                  <prop>
                    <resourcetype><collection/></resourcetype>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        assertEquals(1, entries.size)
        assertTrue(entries[0].isDir)
        assertEquals("/Notes", entries[0].path)
    }

    @Test
    fun `classifies resources and parts case-insensitively`() {
        val body = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/admin/resources/Team/Plan.md</d:href>
                <d:propstat><d:prop><d:getcontentlength>3</d:getcontentlength></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/admin/RESOURCES/virtual.txt</d:href>
                <d:propstat><d:prop><d:getcontentlength>1</d:getcontentlength></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = WebDavApi.parseMultistatus(body, basePath)

        val plan = entries.first { it.name == "Plan.md" }
        assertTrue(plan.isResource)
        assertFalse(plan.isPart)
        assertEquals("/parts/Team/Plan.md", plan.linkTarget)
        assertEquals("/parts/Team/Plan.md", plan.pairedPath)

        val virtual = entries.first { it.name == "virtual.txt" }
        assertTrue(virtual.isResource)
    }
}