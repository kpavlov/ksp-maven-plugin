# KSP Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/me.kpavlov.ksp.maven/ksp-maven-plugin)](https://central.sonatype.com/artifact/me.kpavlov.ksp.maven/ksp-maven-plugin/)
[![Kotlin CI with Maven](https://github.com/kpavlov/ksp-maven-plugin/actions/workflows/maven.yml/badge.svg)](https://github.com/kpavlov/ksp-maven-plugin/actions/workflows/maven.yml)
[![Api Docs](https://img.shields.io/badge/api-docs-blue)](https://kpavlov.github.io/ksp-maven-plugin/api/)
![GitHub License](https://img.shields.io/github/license/kpavlov/ksp-maven-plugin)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2+-blueviolet.svg?logo=kotlin)](http://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-11+-red.svg?logo=jvm)](http://java.com)

A Maven plugin for running Kotlin Symbol Processing (KSP) on JVM projects.

## Overview

This plugin integrates [KSP (Kotlin Symbol Processing)](https://kotlinlang.org/docs/ksp-overview.html) into Maven builds, allowing you to process Kotlin source files with annotation processors that use the [KSP API](https://github.com/google/ksp/blob/main/docs/ksp2.md).

## Requirements

- Maven 3.6.0 or higher
- JDK 11 or higher
- Kotlin 2.2.21 or a compatible version

## Configuration

### Basic Configuration

Minimal setup with a custom KSP processor:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>me.kpavlov.ksp.maven</groupId>
            <artifactId>ksp-maven-plugin</artifactId>
            <version>[LATEST VERSION]</version>
            <executions>
                <execution>
                    <goals>
                        <goal>process</goal>
                    </goals>
                </execution>
            </executions>
            <!-- KSP processors are plugin dependencies, not project dependencies -->
            <dependencies>
                <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>my-ksp-processor</artifactId>
                    <version>1.0.0</version>
                </dependency>
            </dependencies>
        </plugin>

        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-plugin</artifactId>
            <version>2.2.21</version>
            <executions>
                <execution>
                    <id>compile</id>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                    <phase>compile</phase>
                </execution>
            </executions>
        </plugin>
    </plugins>
    <sourceDirectory>src/main/kotlin</sourceDirectory>
</build>
```

### Advanced Configuration

All available configuration options:

```xml
<plugin>
    <groupId>me.kpavlov.ksp.maven</groupId>
    <artifactId>ksp-maven-plugin</artifactId>
    <version>[LATEST VERSION]</version>
    <executions>
        <execution>
            <goals>
                <goal>process</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Main source directory to process (default: ${project.build.sourceDirectory}) -->
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>

        <!-- Additional source directories (default: none) -->
        <sourceDirs>
            <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
            <sourceDir>${project.basedir}/src/generated/kotlin</sourceDir>
        </sourceDirs>

        <!-- Output directory for generated Kotlin sources (default: ${project.build.directory}/generated-sources/ksp) -->
        <kotlinOutputDir>${project.build.directory}/generated-sources/ksp</kotlinOutputDir>

        <!-- Output directory for generated Java sources (default: ${project.build.directory}/generated-sources/ksp) -->
        <javaOutputDir>${project.build.directory}/generated-sources/ksp</javaOutputDir>

        <!-- Output directory for compiled classes (default: ${project.build.directory}/ksp-classes) -->
        <classOutputDir>${project.build.directory}/ksp-classes</classOutputDir>

        <!-- Output directory for resources (default: ${project.build.directory}/generated-resources/ksp) -->
        <resourceOutputDir>${project.build.directory}/generated-resources/ksp</resourceOutputDir>

        <!-- KSP output directory (default: ${project.build.directory}/ksp) -->
        <kspOutputDir>${project.build.directory}/ksp</kspOutputDir>

        <!-- Cache directory for incremental processing (default: ${project.build.directory}/ksp-cache) -->
        <cachesDir>${project.build.directory}/ksp-cache</cachesDir>

        <!-- Enable incremental processing (default: false) -->
        <incremental>true</incremental>

        <!-- Enable incremental compilation logging (default: false) -->
        <incrementalLog>true</incrementalLog>

        <!-- Kotlin language version (default: 2.2) -->
        <languageVersion>2.2</languageVersion>

        <!-- Kotlin API version (default: 2.2) -->
        <apiVersion>2.2</apiVersion>

        <!-- JVM default mode for interfaces (default: disable) -->
        <jvmDefaultMode>disable</jvmDefaultMode>

        <!-- KSP processor options as key-value pairs (default: none) -->
        <apOptions>
            <option1>value1</option1>
            <option2>value2</option2>
        </apOptions>

        <!-- Continue build on processing errors (default: false) -->
        <ignoreProcessingErrors>false</ignoreProcessingErrors>

        <!-- Treat all warnings as errors (default: false) -->
        <allWarningsAsErrors>false</allWarningsAsErrors>

        <!-- Map annotation arguments in Java sources (default: true) -->
        <mapAnnotationArgumentsInJava>true</mapAnnotationArgumentsInJava>

        <!-- Module name (default: ${project.artifactId}) -->
        <moduleName>my-module</moduleName>

        <!-- JVM target version (default: 11) -->
        <jvmTarget>17</jvmTarget>

        <!-- Skip KSP processing (default: false, can be set via -Dksp.skip=true) -->
        <skip>false</skip>

        <!-- Add generated sources to compilation (default: true) -->
        <addGeneratedSourcesToCompile>true</addGeneratedSourcesToCompile>

        <!-- Enable debug output (default: false) -->
        <debug>false</debug>
    </configuration>
    <!-- KSP processors are plugin dependencies, not project dependencies -->
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>my-ksp-processor</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</plugin>
```

## Build Phases

The plugin runs in the `generate-sources` phase by default:

1. KSP processors run and generate code
2. Generated sources are automatically added to the compilation source roots (when `addGeneratedSourcesToCompile` is `true`)
3. The Kotlin compiler compiles both original and generated sources

## Skipping KSP Processing

Skip KSP processing via command line:

```bash
mvn clean install -Dksp.skip=true
```

Or in your `pom.xml`:

```xml
<configuration>
    <skip>true</skip>
</configuration>
```

## Troubleshooting

### No processors found

If you see "No KSP processors found in dependencies":

1. Verify your processor is added as a **plugin dependency** (inside `<plugin><dependencies>` section), not as a project dependency
2. Check the processor JAR contains `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
3. Ensure the dependency scope is not `test`

### Compilation errors with generated code

If the Kotlin compiler can't find generated sources:

1. Verify `addGeneratedSourcesToCompile` is `true` (default)
2. Check that KSP plugin runs before Kotlin compilation
3. Verify output directories are correct

### Incremental compilation issues

If incremental compilation causes problems:

1. Disable it: `<incremental>false</incremental>`
2. Clean the cache: `mvn clean`
3. Delete `${project.build.directory}/ksp-cache`

### Debug output

Enable debug output to see detailed processing information:

```xml
<configuration>
    <debug>true</debug>
</configuration>
```

This will log:
- Found KSP processors
- Processor classloader details
- Processor providers
- KSP configuration
- Incremental changes (if enabled)

## Building the Plugin

### Using Make (recommended)

Build, verify, install the plugin and test with sample project:

```bash
make build
```

Format code:

```bash
make format
```

Run linting:

```bash
make lint
```

Generate API documentation:

```bash
make apidocs
```

Run all checks (format, lint, build):

```bash
make all
```

### Using Maven directly

Build and install the plugin:

```bash
mvn clean install
```

Test with the sample project:

```bash
cd sample-project
mvn clean compile
```

## License

[Apache License 2.0](LICENSE.txt)

## Contributing

Contributions are welcome! Please follow the Kotlin coding conventions and ensure all tests pass.

## Resources

- [KSP Documentation](https://kotlinlang.org/docs/ksp-overview.html)
- [KSP Command Line Reference](https://kotlinlang.org/docs/ksp-command-line.html)
- [Kotlin Maven Plugin](https://kotlinlang.org/docs/maven.html)
