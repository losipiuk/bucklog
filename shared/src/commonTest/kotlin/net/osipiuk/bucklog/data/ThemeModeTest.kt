package net.osipiuk.bucklog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {
    @Test fun defaultAndUnknownValuesFollowSystem() {
        for (stored in listOf(null, "", "future-mode", "system")) {
            val mode = ThemeMode.fromStored(stored)
            assertFalse(mode.isDark(false))
            assertTrue(mode.isDark(true))
        }
    }

    @Test fun explicitChoicesOverrideBothSystemModes() {
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.fromStored("light").isDark(systemDark))
            assertTrue(ThemeMode.fromStored("dark").isDark(systemDark))
        }
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.storedValue)) }
    }
}
