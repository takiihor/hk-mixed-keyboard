package com.hkmixedkeyboard

import com.hkmixedkeyboard.util.Csv
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `plain fields split on commas and trim`() {
        assertEquals(listOf("我哋", "ogrp"), Csv.split(" 我哋 , ogrp "))
    }

    @Test
    fun `quoted field keeps its comma`() {
        assertEquals(listOf("a,b", "c"), Csv.split("\"a,b\",c"))
    }

    @Test
    fun `doubled quotes decode to one quote`() {
        assertEquals(listOf("say \"hi\"", "x"), Csv.split("\"say \"\"hi\"\"\",x"))
    }

    @Test
    fun `escape wraps only fields that need it and is round-trippable`() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"a\"\"b\"", Csv.escape("a\"b"))
        // round trip: escape then split returns the original single field
        val original = "a,\"b\""
        assertEquals(listOf(original), Csv.split(Csv.escape(original)))
    }
}
