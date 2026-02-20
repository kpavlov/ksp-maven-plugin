package com.example

import arrow.optics.optics

/**
 * A data class annotated with @optics.
 *
 * Arrow KSP processor generates Lens/Prism/Optional optics for each property.
 * The companion object is REQUIRED for the generated extension properties to attach to.
 *
 * ```
 * Generated (by arrow-optics-ksp-plugin):
 *   Person.name   : Lens<Person, String>
 *   Person.age    : Lens<Person, Int>
 *   Person.address: Lens<Person, Address>
 */
@optics
data class Person(
    val name: String,
    val age: Int,
    val address: Address,
) {
    companion object
}

/**
 * A nested data class also annotated with @optics.
 *
 * Generated (by arrow-optics-ksp-plugin):
 *   Address.street: Lens<Address, String>
 *   Address.city  : Lens<Address, String>
 */
@optics
data class Address(
    val street: String,
    val city: String,
) {
    companion object
}
