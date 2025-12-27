package com.example

import me.kpavlov.ksp.maven.testprocessor.GenerateHello

@GenerateHello(name = "Mega Sample")
class Sample {
    fun sayHello() = println("Hello from Sample")
}
