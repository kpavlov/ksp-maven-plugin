package com.example

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeneratedCodeTest {
    @Test
    fun `generated greeting class should exist and work`() {
        // Verify the generated class exists and can be instantiated
        val greeting = TestClassGreeting()

        // Verify the greet method works
        val message = greeting.greet()
        assertEquals("Hello, Integration Test!", message)

        // Verify the companion object constant
        assertEquals("TestClass", TestClassGreeting.GENERATED_FOR)
    }

    @Test
    fun `original class should exist`() {
        val testClass = TestClass()
        assertNotNull(testClass)
    }
}
