package me.kpavlov.ksp.maven

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TestEnvironment {
    val projectDir: String = System.getProperty("user.dir")
    val testProjectsDir: Path = setupTestDirectory()

    private fun setupTestDirectory(): Path {
        // Create test projects under target/it
        val dir =
            Paths.get(
                projectDir,
                "target",
                "it",
                currentTimeString(),
            )
        Files.createDirectories(dir)
        return dir
    }

    private fun currentTimeString(): String {
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH-mm-ss")
        return now.format(formatter)
    }
}
