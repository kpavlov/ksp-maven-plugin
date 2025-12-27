package com.example

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeneratedTestCodeTest {
    @Test
    fun `generated greeting class should exist and work`() {
        // Verify the generated class exists and can be instantiated
        val greeting = TestSampleGreeting()

        // Verify the greet method works
        val message = greeting.greet()
        assertEquals("Hello, Test Sample!", message)

        // Verify the companion object constant
        assertEquals("TestSample", TestSampleGreeting.GENERATED_FOR)
    }
}
