package com.example

import me.kpavlov.ksp.maven.testprocessor.GenerateHello

@GenerateHello(name = "Test Sample")
class TestSample {
    fun help() = println("Hello from Test Sample")
}
