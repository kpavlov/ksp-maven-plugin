package me.kpavlov.ksp.maven.testprocessor

/**
 * Annotation to trigger code generation
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateHello(val name: String = "World")
