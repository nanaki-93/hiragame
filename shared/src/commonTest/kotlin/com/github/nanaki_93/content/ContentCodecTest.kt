package com.github.nanaki_93.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContentCodecTest {
    @Test fun strictDocumentDecoding() {
        val empty = """{"formatVersion":1,"contentVersion":1,"topics":[],"entries":[]}"""
        assertEquals(emptyList(), ContentCodec.decodeCatalog(empty).topics)
        assertFailsWith<Exception> { ContentCodec.decodeCatalog(empty.replace("\"topics\":[]", "\"topics\":[],\"topics\":[]")) }
        assertFailsWith<Exception> { ContentCodec.decodeCatalog(empty.replace("\"topics\":[]", "\"topics\":[],\"top\\u0069cs\":[]")) }
        assertFailsWith<Exception> { ContentCodec.decodeCatalog(empty.replace("\"contentVersion\":1", "\"contentVersion\":0")) }
        assertFailsWith<Exception> { ContentCodec.decodeCatalog("[]") }
    }
}
