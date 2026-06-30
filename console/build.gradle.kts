import java.nio.charset.Charset

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaExec>().configureEach {
    // Match process output encoding to the host terminal encoding.
    val nativeEncoding = System.getProperty("native.encoding") ?: Charset.defaultCharset().name()
    if (!nativeEncoding.isNullOrBlank()) {
        systemProperty("file.encoding", nativeEncoding)
    }
}

val consoleMainClass = providers.gradleProperty("consoleMainClass")

tasks.register<JavaExec>("runConsoleMain") {
    group = "application"
    description = "Run console main() passed via -PconsoleMainClass"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(consoleMainClass)
    doFirst {
        require(consoleMainClass.isPresent) {
            "Specify -PconsoleMainClass=<fully.qualified.MainKt>"
        }
    }
}
