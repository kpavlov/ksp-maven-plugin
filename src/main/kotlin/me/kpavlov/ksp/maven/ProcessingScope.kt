package me.kpavlov.ksp.maven

import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import java.io.File

/**
 * Defines the scope (main or test) for KSP processing.
 */
enum class ProcessingScope {
    MAIN {
        override fun getSourceDirectory(project: MavenProject): File =
            File(project.build.sourceDirectory)

        override fun getClasspathElements(project: MavenProject): List<String> =
            project.compileClasspathElements

        override fun addCompileSourceRoot(
            project: MavenProject,
            path: String,
        ) {
            project.addCompileSourceRoot(path)
        }

        override fun addResource(
            project: MavenProject,
            resource: Resource,
        ) {
            project.addResource(resource)
        }

        override fun getDefaultKotlinOutputDir(buildDirectory: String): File =
            File("$buildDirectory/generated-sources/ksp")

        override fun getDefaultJavaOutputDir(buildDirectory: String): File =
            File("$buildDirectory/generated-sources/ksp")

        override fun getDefaultClassOutputDir(buildDirectory: String): File =
            File("$buildDirectory/ksp-classes")

        override fun getDefaultResourceOutputDir(buildDirectory: String): File =
            File("$buildDirectory/generated-resources/ksp")

        override fun getDefaultKspOutputDir(buildDirectory: String): File =
            File("$buildDirectory/ksp")

        override fun getDefaultCachesDir(buildDirectory: String): File =
            File("$buildDirectory/ksp-cache")
    },

    TEST {
        override fun getSourceDirectory(project: MavenProject): File =
            File(project.build.testSourceDirectory)

        override fun getClasspathElements(project: MavenProject): List<String> =
            project.testClasspathElements

        override fun addCompileSourceRoot(
            project: MavenProject,
            path: String,
        ) {
            project.addTestCompileSourceRoot(path)
        }

        override fun addResource(
            project: MavenProject,
            resource: Resource,
        ) {
            project.addTestResource(resource)
        }

        override fun getDefaultKotlinOutputDir(buildDirectory: String): File =
            File("$buildDirectory/generated-test-sources/ksp")

        override fun getDefaultJavaOutputDir(buildDirectory: String): File =
            File("$buildDirectory/generated-test-sources/ksp")

        override fun getDefaultClassOutputDir(buildDirectory: String): File =
            File("$buildDirectory/ksp-test-classes")

        override fun getDefaultResourceOutputDir(buildDirectory: String): File =
            File("$buildDirectory/generated-test-resources/ksp")

        override fun getDefaultKspOutputDir(buildDirectory: String): File =
            File("$buildDirectory/ksp-test")

        override fun getDefaultCachesDir(buildDirectory: String): File =
            File("$buildDirectory/ksp-test-cache")
    };

    /**
     * Returns the source directory for this processing scope.
     *
     * For [MAIN] scope, returns the main source directory (e.g., `src/main/kotlin`).
     * For [TEST] scope, returns the test source directory (e.g., `src/test/kotlin`).
     *
     * Example:
     * ```kotlin
     * val sourceDir = ProcessingScope.MAIN.getSourceDirectory(project)
     * // Returns: File("src/main/kotlin")
     * ```
     *
     * @param project the Maven project
     * @return the source directory for this scope
     */
    abstract fun getSourceDirectory(project: MavenProject): File

    /**
     * Returns the classpath elements for compilation in this processing scope.
     *
     * For [MAIN] scope, returns compile classpath elements.
     * For [TEST] scope, returns test classpath elements (includes main + test dependencies).
     *
     * Example:
     * ```kotlin
     * val classpath = ProcessingScope.TEST.getClasspathElements(project)
     * // Returns: ["/path/to/classes", "/path/to/dep1.jar", "/path/to/dep2.jar"]
     * ```
     *
     * @param project the Maven project
     * @return list of classpath element paths
     */
    abstract fun getClasspathElements(project: MavenProject): List<String>

    /**
     * Adds a compile source root to the Maven project for this processing scope.
     *
     * For [MAIN] scope, adds to main compile source roots.
     * For [TEST] scope, adds to test compile source roots.
     *
     * Example:
     * ```kotlin
     * ProcessingScope.MAIN.addCompileSourceRoot(project, "target/generated-sources/ksp")
     * // Main sources now include generated sources
     * ```
     *
     * @param project the Maven project
     * @param path the source root path to add
     */
    abstract fun addCompileSourceRoot(
        project: MavenProject,
        path: String,
    )

    /**
     * Adds a resource to the Maven project for this processing scope.
     *
     * For [MAIN] scope, adds to main resources.
     * For [TEST] scope, adds to test resources.
     *
     * Example:
     * ```kotlin
     * val resource = Resource().apply {
     *     directory = "target/generated-resources/ksp"
     * }
     * ProcessingScope.MAIN.addResource(project, resource)
     * ```
     *
     * @param project the Maven project
     * @param resource the resource to add
     */
    abstract fun addResource(
        project: MavenProject,
        resource: Resource,
    )

    /**
     * Returns the default Kotlin output directory for this processing scope.
     *
     * Example:
     * ```kotlin
     * val kotlinDir = ProcessingScope.MAIN.getDefaultKotlinOutputDir("target")
     * // Returns: File("target/generated-sources/ksp")
     * ```
     *
     * @param buildDirectory the build directory path (typically "target")
     * @return the default Kotlin output directory
     */
    abstract fun getDefaultKotlinOutputDir(buildDirectory: String): File

    /**
     * Returns the default Java output directory for this processing scope.
     *
     * Example:
     * ```kotlin
     * val javaDir = ProcessingScope.TEST.getDefaultJavaOutputDir("target")
     * // Returns: File("target/generated-test-sources/ksp")
     * ```
     *
     * @param buildDirectory the build directory path (typically "target")
     * @return the default Java output directory
     */
    abstract fun getDefaultJavaOutputDir(buildDirectory: String): File

    /**
     * Returns the default class output directory for this processing scope.
     *
     * Example:
     * ```kotlin
     * val classDir = ProcessingScope.MAIN.getDefaultClassOutputDir("target")
     * // Returns: File("target/ksp-classes")
     * ```
     *
     * @param buildDirectory the build directory path (typically "target")
     * @return the default class output directory
     */
    abstract fun getDefaultClassOutputDir(buildDirectory: String): File

    /**
     * Returns the default resource output directory for this processing scope.
     *
     * Example:
     * ```kotlin
     * val resourceDir = ProcessingScope.TEST.getDefaultResourceOutputDir("target")
     * // Returns: File("target/generated-test-resources/ksp")
     * ```
     *
     * @param buildDirectory the build directory path (typically "target")
     * @return the default resource output directory
     */
    abstract fun getDefaultResourceOutputDir(buildDirectory: String): File

    /**
     * Returns the default KSP output directory for this processing scope.
     *
     * This is the root directory where KSP writes its internal outputs.
     *
     * Example:
     * ```kotlin
     * val kspDir = ProcessingScope.MAIN.getDefaultKspOutputDir("target")
     * // Returns: File("target/ksp")
     * ```
     *
     * @param buildDirectory the build directory path (typically "target")
     * @return the default KSP output directory
     */
    abstract fun getDefaultKspOutputDir(buildDirectory: String): File

    /**
     * Returns the default caches directory for this processing scope.
     *
     * KSP uses this directory to cache incremental compilation data.
     *
     * Example:
     * ```kotlin
     * val cacheDir = ProcessingScope.MAIN.getDefaultCachesDir("target")
     * // Returns: File("target/ksp-cache")
     * ```
     *
     * @param buildDirectory the build directory path (typically "target")
     * @return the default caches directory
     */
    abstract fun getDefaultCachesDir(buildDirectory: String): File
}
