# KSP Maven Plugin

A Maven plugin for running Kotlin Symbol Processing (KSP) on JVM projects.

## Overview

This plugin integrates KSP (Kotlin Symbol Processing) into Maven builds, allowing you to process Kotlin source files
with annotation processors that use the KSP API.

## Requirements

- Maven 3.6.0 or higher
- JDK 11 or higher
- Kotlin 2.2.10 or compatible version

## Installation

Add the plugin to your `pom.xml`:

```xml

<build>
    <plugins>
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>ksp-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>process</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Configuration

### Basic Configuration

```xml

<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>ksp-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>process</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Enable incremental processing (default: true) -->
        <incremental>true</incremental>

        <!-- JVM target version (default: 11) -->
        <jvmTarget>17</jvmTarget>

        <!-- Module name (default: ${project.artifactId}) -->
        <moduleName>my-module</moduleName>
    </configuration>
</plugin>
```

### Advanced Configuration

```xml

<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>ksp-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>process</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Source directories -->
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <sourceDirs>
            <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
            <sourceDir>${project.basedir}/src/generated/kotlin</sourceDir>
        </sourceDirs>

        <!-- Output directories -->
        <kotlinOutputDir>${project.build.directory}/generated-sources/ksp</kotlinOutputDir>
        <javaOutputDir>${project.build.directory}/generated-sources/ksp</javaOutputDir>
        <resourceOutputDir>${project.build.directory}/generated-resources/ksp</resourceOutputDir>
        <classOutputDir>${project.build.directory}/ksp-classes</classOutputDir>

        <!-- Incremental processing -->
        <incremental>true</incremental>
        <cachesDir>${project.build.directory}/ksp-cache</cachesDir>

        <!-- Processor options -->
        <apOptions>
            <room.schemaLocation>${project.basedir}/schemas</room.schemaLocation>
            <room.incremental>true</room.incremental>
            <customOption>customValue</customOption>
        </apOptions>

        <!-- Additional compiler arguments -->
        <compilerArgs>
            <arg>-Xopt-in=kotlin.RequiresOptIn</arg>
        </compilerArgs>

        <!-- Add generated sources to compilation -->
        <addGeneratedSourcesToCompile>true</addGeneratedSourcesToCompile>

        <!-- Skip KSP processing -->
        <skip>false</skip>
    </configuration>
</plugin>
```

## Configuration Parameters

| Parameter                      | Type               | Default                                              | Description                                   |
|--------------------------------|--------------------|------------------------------------------------------|-----------------------------------------------|
| `sourceDirectory`              | File               | `${project.build.sourceDirectory}`                   | Main source directory to process              |
| `sourceDirs`                   | List<File>         | -                                                    | Additional source directories                 |
| `kotlinOutputDir`              | File               | `${project.build.directory}/generated-sources/ksp`   | Output directory for generated Kotlin sources |
| `javaOutputDir`                | File               | `${project.build.directory}/generated-sources/ksp`   | Output directory for generated Java sources   |
| `classOutputDir`               | File               | `${project.build.directory}/ksp-classes`             | Output directory for compiled classes         |
| `resourceOutputDir`            | File               | `${project.build.directory}/generated-resources/ksp` | Output directory for resources                |
| `kspOutputDir`                 | File               | `${project.build.directory}/ksp`                     | KSP output directory                          |
| `cachesDir`                    | File               | `${project.build.directory}/ksp-cache`               | Cache directory for incremental processing    |
| `incremental`                  | boolean            | `true`                                               | Enable incremental processing                 |
| `apOptions`                    | Map<String,String> | -                                                    | KSP processor options (key-value pairs)       |
| `compilerArgs`                 | List<String>       | -                                                    | Additional compiler arguments                 |
| `moduleName`                   | String             | `${project.artifactId}`                              | Module name                                   |
| `jvmTarget`                    | String             | `11`                                                 | JVM target version                            |
| `skip`                         | boolean            | `false`                                              | Skip KSP processing                           |
| `addGeneratedSourcesToCompile` | boolean            | `true`                                               | Add generated sources to compilation          |
| `kspVersion`                   | String             | `2.2.10-2.0.2`                                       | KSP version to use                            |
| `kotlinVersion`                | String             | `2.2.10`                                             | Kotlin version to use                         |

## Usage Examples

### Example 1: Room Database

```xml

<dependencies>
    <!-- Kotlin -->
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-stdlib</artifactId>
        <version>2.2.21</version>
    </dependency>

    <!-- Room Runtime -->
    <dependency>
        <groupId>androidx.room</groupId>
        <artifactId>room-runtime</artifactId>
        <version>2.6.1</version>
    </dependency>

    <!-- Room KSP Processor -->
    <dependency>
        <groupId>androidx.room</groupId>
        <artifactId>room-compiler</artifactId>
        <version>2.6.1</version>
        <scope>provided</scope>
    </dependency>

    <!-- KSP Dependencies -->
    <dependency>
        <groupId>com.google.devtools.ksp</groupId>
        <artifactId>symbol-processing-api</artifactId>
        <version>2.2.10-2.0.2</version>
    </dependency>
    <dependency>
        <groupId>com.google.devtools.ksp</groupId>
        <artifactId>symbol-processing-cmdline</artifactId>
        <version>2.2.10-2.0.2</version>
    </dependency>
</dependencies>

<build>
<plugins>
    <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>ksp-maven-plugin</artifactId>
        <version>1.0.0</version>
        <executions>
            <execution>
                <goals>
                    <goal>process</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
            <apOptions>
                <room.schemaLocation>${project.basedir}/schemas</room.schemaLocation>
                <room.incremental>true</room.incremental>
            </apOptions>
        </configuration>
    </plugin>

    <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-maven-plugin</artifactId>
        <version>2.2.10</version>
        <executions>
            <execution>
                <id>compile</id>
                <phase>compile</phase>
                <goals>
                    <goal>compile</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</plugins>
</build>
```

### Example 2: Custom KSP Processor

```xml

<dependencies>
    <!-- Your custom KSP processor -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>my-ksp-processor</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>

    <!-- KSP Dependencies -->
    <dependency>
        <groupId>com.google.devtools.ksp</groupId>
        <artifactId>symbol-processing-api</artifactId>
        <version>2.2.10-2.0.2</version>
    </dependency>
    <dependency>
        <groupId>com.google.devtools.ksp</groupId>
        <artifactId>symbol-processing-cmdline</artifactId>
        <version>2.2.10-2.0.2</version>
    </dependency>
</dependencies>

<build>
<plugins>
    <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>ksp-maven-plugin</artifactId>
        <version>1.0.0</version>
        <executions>
            <execution>
                <goals>
                    <goal>process</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
            <apOptions>
                <myProcessor.option1>value1</myProcessor.option1>
                <myProcessor.option2>value2</myProcessor.option2>
            </apOptions>
        </configuration>
    </plugin>
</plugins>
</build>
```

### Example 3: Multi-Module Project

```xml
<!-- Parent POM -->
<project>
    <properties>
        <kotlin.version>2.2.10</kotlin.version>
        <ksp.version>2.2.10-2.0.2</ksp.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.devtools.ksp</groupId>
                <artifactId>symbol-processing-api</artifactId>
                <version>${ksp.version}</version>
            </dependency>
            <dependency>
                <groupId>com.google.devtools.ksp</groupId>
                <artifactId>symbol-processing-cmdline</artifactId>
                <version>${ksp.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>ksp-maven-plugin</artifactId>
                    <version>1.0.0</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>process</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>

        <!-- Child Module POM -->
<project>
<parent>
    <groupId>com.example</groupId>
    <artifactId>parent</artifactId>
    <version>1.0.0</version>
</parent>

<artifactId>module-with-ksp</artifactId>

<dependencies>
    <dependency>
        <groupId>com.google.devtools.ksp</groupId>
        <artifactId>symbol-processing-api</artifactId>
    </dependency>
    <dependency>
        <groupId>com.google.devtools.ksp</groupId>
        <artifactId>symbol-processing-cmdline</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>ksp-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
</project>
```

## Build Phases

The plugin is designed to run in the `generate-sources` phase by default, which means:

1. KSP processors run first and generate code
2. Generated sources are automatically added to the compilation source roots
3. The Kotlin compiler compiles both original and generated sources

## Skipping KSP Processing

You can skip KSP processing using:

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

If you see "No KSP processors found in dependencies", ensure:

1. Your processor dependency is in the project dependencies
2. The processor JAR contains `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
3. The dependency scope is not `test`

### Compilation errors with generated code

If the Kotlin compiler can't find generated sources:

1. Ensure `addGeneratedSourcesToCompile` is `true` (default)
2. Check that the KSP plugin runs before the Kotlin compilation
3. Verify output directories are correct

### Incremental compilation issues

If incremental compilation causes problems:

1. Try disabling it: `<incremental>false</incremental>`
2. Clean the cache directory: `mvn clean`
3. Delete `${project.build.directory}/ksp-cache`

## Building the Plugin

To build and install the plugin locally:

```bash
cd ksp-maven-plugin
mvn clean install
```

## License

This plugin is part of the Kotlin ecosystem and follows the same license terms.

## Contributing

Contributions are welcome! Please follow the standard Kotlin contribution guidelines.

## Resources

- [KSP Documentation](https://kotlinlang.org/docs/ksp-overview.html)
- [KSP Command Line Reference](https://kotlinlang.org/docs/ksp-command-line.html)
- [Kotlin Maven Plugin](https://kotlinlang.org/docs/maven.html)
