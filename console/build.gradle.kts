import java.nio.charset.Charset
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

val androidSdkDir: String = run {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    localProperties.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found. Set sdk.dir in local.properties")
}

val androidJar = file("$androidSdkDir/platforms/android-34/android.jar").also { jar ->
    require(jar.exists()) {
        "android.jar not found at ${jar.absolutePath}. Install Android SDK Platform 34."
    }
}

dependencies {
    // Stubs for compile + JVM main(); real Android APIs work only on device/emulator.
    implementation(files(androidJar))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
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
