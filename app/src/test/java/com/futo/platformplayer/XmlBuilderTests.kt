package com.futo.platformplayer


import com.futo.platformplayer.builders.XMLBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class XMLBuilderTest {

    @Test
    fun testXmlHeader() {
        val builder = XMLBuilder()
        builder.writeXmlHeader("1.0", "UTF-8")
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", builder.build().trim())
    }

    @Test
    fun testTagClosed() {
        val builder = XMLBuilder()
        builder.tagClosed("exampleTag", mapOf("attribute1" to "value1"))
        assertEquals("<exampleTag attribute1=\"value1\"/>", builder.build().trim())
    }

    @Test
    fun testTag() {
        val builder = XMLBuilder()
        builder.tag("exampleTag", mapOf("attribute1" to "value1")) {
            it.value("inside exampleTag")
        }

        assertEquals("<exampleTag attribute1=\"value1\">\n   inside exampleTag\n</exampleTag>", builder.build().trim())
    }

    @Test
    fun testValueTag() {
        val builder = XMLBuilder()
        builder.valueTag("exampleTag", mapOf("attribute1" to "value1"), "inside exampleTag")
        assertEquals("<exampleTag attribute1=\"value1\">inside exampleTag</exampleTag>", builder.build().trim())
    }

    @Test
    fun testValue() {
        val builder = XMLBuilder()
        builder.value("inside exampleTag")
        assertEquals("inside exampleTag", builder.build().trim())
    }
}