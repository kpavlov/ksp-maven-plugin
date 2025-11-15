package com.example

import me.kpavlov.ksp.maven.testprocessor.GenerateHello

@GenerateHello(name = "Integration Test")
class TestClass {
    fun sayHello() = println("Hello from TestClass")
}
