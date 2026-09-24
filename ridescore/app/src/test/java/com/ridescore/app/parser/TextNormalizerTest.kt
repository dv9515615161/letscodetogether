package com.ridescore.app.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `rupee symbol variants all become one symbol`() {
        assertEquals("₹45", TextNormalizer.normalize("₹45"))
        assertEquals("₹ 45", TextNormalizer.normalize("Rs. 45"))
        assertEquals("₹ 45", TextNormalizer.normalize("Rs 45"))
        assertEquals("₹ 45", TextNormalizer.normalize("INR 45"))
        assertEquals("₹ 45", TextNormalizer.normalize("₹ 45"))
    }

    @Test
    fun `thousands separators are removed but decimals are kept`() {
        assertEquals("₹1250", TextNormalizer.normalize("₹1,250"))
        assertEquals("₹180000", TextNormalizer.normalize("₹1,80,000"))
        assertEquals("₹128.55", TextNormalizer.normalize("₹128.55"))
    }

    @Test
    fun `non ascii digits are mapped onto 0-9`() {
        assertEquals("₹४५".let { TextNormalizer.normalize("₹45") }, TextNormalizer.normalize("₹45"))
        assertEquals("₹45", TextNormalizer.normalize("₹४५"))
    }

    @Test
    fun `whitespace is collapsed and case is folded`() {
        assertEquals("pickup 1.8 km", TextNormalizer.normalize("  Pickup\t\t1.8   KM  "))
    }

    @Test
    fun `ocr repair fixes digits next to a rupee sign`() {
        assertEquals("₹45", TextNormalizer.normalize("₹4S", ocr = true))
        assertEquals("₹15", TextNormalizer.normalize("₹1S", ocr = true))
        assertEquals("₹105", TextNormalizer.normalize("₹1O5", ocr = true))
    }

    @Test
    fun `ocr repair refuses to turn an all-letter run into a number`() {
        // "lS" has no real digit to anchor the repair, so RideScore leaves it
        // unreadable rather than inventing a fare out of two letters.
        assertEquals("₹ls", TextNormalizer.normalize("₹lS", ocr = true))
    }

    @Test
    fun `ocr repair fixes digits in front of a unit`() {
        assertEquals("pickup 1.8 km", TextNormalizer.normalize("Pickup l.8 km", ocr = true))
        assertEquals("trip 5.9 km", TextNormalizer.normalize("Trip S.9 km", ocr = true))
        assertEquals("trip time 12 mins", TextNormalizer.normalize("Trip time l2 mins", ocr = true))
    }

    @Test
    fun `ocr repair leaves ordinary words alone`() {
        assertEquals("nallagandla", TextNormalizer.normalize("Nallagandla", ocr = true))
        assertEquals("kondapur metro", TextNormalizer.normalize("Kondapur Metro", ocr = true))
        assertEquals("1.8 kms", TextNormalizer.normalize("1.8 kms", ocr = true))
    }

    @Test
    fun `accessibility text is never ocr repaired`() {
        assertEquals("₹4s", TextNormalizer.normalize("₹4S", ocr = false))
    }
}
