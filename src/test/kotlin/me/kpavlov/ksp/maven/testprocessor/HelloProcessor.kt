package me.kpavlov.ksp.maven.testprocessor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate

/**
 * A simple KSP processor for testing.
 * Generates a greeting class for each class annotated with @GenerateHello.
 */
class HelloProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenerateHello::class.qualifiedName!!)
        val validSymbols = symbols.filter { it.validate() }

        validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                processClass(classDeclaration)
            }

        return emptyList()
    }

    private fun processClass(classDeclaration: KSClassDeclaration) {
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val generatedClassName = "${className}Greeting"

        // Get annotation parameter
        val annotation = classDeclaration.annotations.first {
            it.shortName.asString() == "GenerateHello"
        }
        val name = annotation.arguments
            .firstOrNull { it.name?.asString() == "name" }
            ?.value?.toString() ?: "World"

        logger.info("Generating $generatedClassName for $className with name=$name")

        // Generate the greeting class
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(true, classDeclaration.containingFile!!),
            packageName = packageName,
            fileName = generatedClassName
        )

        file.bufferedWriter().use { writer ->
            writer.write("""
                package $packageName

                /**
                 * Generated greeting class for $className
                 */
                class $generatedClassName {
                    fun greet(): String = "Hello, $name!"

                    companion object {
                        const val GENERATED_FOR = "$className"
                    }
                }
            """.trimIndent())
        }
    }
}
