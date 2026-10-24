package com.semo.keyboard.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCorrectTest {

    @Test fun englishTypoIsFixed() {
        assertEquals("the", AutoCorrect.fix("teh"))
        assertEquals("tomorrow", AutoCorrect.fix("tommorow"))
        assertEquals("don't", AutoCorrect.fix("dont"))
    }

    @Test fun caseIsPreserved() {
        assertEquals("The", AutoCorrect.fix("Teh"))
        assertEquals("THE", AutoCorrect.fix("TEH"))
    }

    @Test fun correctWordsAreLeftAlone() {
        assertNull(AutoCorrect.fix("hello"))
        assertNull(AutoCorrect.fix("the"))
        assertNull(AutoCorrect.fix(""))
    }

    @Test fun veryLongWordsAreSkipped() {
        assertNull(AutoCorrect.fix("a".repeat(25)))
    }

    @Test fun arabicElongationIsSqueezed() {
        assertEquals("جميل", AutoCorrect.fix("جمييييل"))
    }

    @Test fun arabicDetection() {
        assertTrue(ArabicText.isArabicWord("كتاب"))
        assertFalse(ArabicText.isArabicWord("book"))
        assertFalse(ArabicText.isArabicWord("123"))
    }
}
