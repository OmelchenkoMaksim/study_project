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

tasks.register<JavaExec>("runTheDog") {
    group = "application"
    description = "Run TheDog main()"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.study_project.yandex._02_TheDogKt")
}
