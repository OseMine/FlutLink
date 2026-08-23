package com.flutcloud.flutlink.data

import org.junit.Test

class ParserDebugTest {
    @Test
    fun dump() {
        val body = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/remote.php/dav/files/admin/</d:href>
    <d:propstat>
      <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
    </d:propstat>
  </d:response>
</d:multistatus>"""
        val p = MiniXmlParser(body)
        var e = p.next()
        var i = 0
        while (e != MiniXmlParser.END_DOCUMENT && i++ < 60) {
            val label = when (e) {
                MiniXmlParser.START_TAG -> "START<${p.name}>"
                MiniXmlParser.END_TAG -> "END</${p.name}>"
                MiniXmlParser.TEXT -> "TEXT[${p.text.take(40)}]"
                else -> "?"
            }
            println("EVENT $i: $label")
            e = p.next()
        }
    }
}
