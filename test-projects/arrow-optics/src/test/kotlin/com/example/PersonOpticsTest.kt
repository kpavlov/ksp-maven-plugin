package com.example

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that arrow-optics-ksp-plugin successfully generated optics for Person and Address.
 *
 * If these tests fail to compile, the KSP processor did not generate the optics.
 * Check:
 *  1. arrow-optics is in project <dependencies> (compile scope), not only in plugin <dependencies>
 *  2. The @optics annotation is applied and companion object is present
 *  3. Generated sources dir (target/generated-sources/ksp) is on the compile source path
 */
class PersonOpticsTest {
    @Test
    fun `Lens modifies name`() {
        val person = Person(name = "john", age = 30, address = Address("Main St", "New York"))

        // Person.name is a generated Lens<Person, String>
        val updated = Person.name.modify(person) { it.replaceFirstChar(Char::uppercaseChar) }

        assertEquals("John", updated.name)
        assertEquals(30, updated.age)
    }

    @Test
    fun `Lens replaces age`() {
        val person = Person(name = "John", age = 30, address = Address("Main St", "New York"))

        val updated = Person.age.set(person, 31)

        assertEquals(31, updated.age)
        assertEquals("John", updated.name)
    }

    @Test
    fun `composed Lens modifies nested city`() {
        val person = Person(name = "John", age = 30, address = Address("Main St", "New York"))

        // Compose Person.address (Lens<Person,Address>) with Address.city (Lens<Address,String>)
        val cityLens = Person.address compose Address.city
        val updated = cityLens.set(person, "London")

        assertEquals("London", updated.address.city)
        assertEquals("Main St", updated.address.street)
    }

    @Test
    fun `Lens get reads property`() {
        val person = Person(name = "Alice", age = 25, address = Address("Elm St", "Boston"))

        assertEquals("Alice", Person.name.get(person))
        assertEquals("Boston", Address.city.get(person.address))
    }
}
