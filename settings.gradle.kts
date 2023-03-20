pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// это имя проекта, оно не связано с именем приложения,
// да и в целом не является необходимым
// как я понимаю оно больше нужно для CI (это то чем девопсы занимаются)
rootProject.name = "Study Project"
// тут указано какие модули включены в приложение, у нас только один app
include (":app")