package com.edu.quickaside

import com.edu.quickaside.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppDestinationTest {
    @Test
    fun managementDestinationsMatchTheAcceptedInformationArchitecture() {
        val labels = AppDestination.entries.map { it.label }

        assertEquals(listOf("Inicio", "Pendientes", "Listas", "Memoria"), labels)
        assertFalse("Capture must remain an action, not a destination", labels.contains("Captura"))
    }
}
