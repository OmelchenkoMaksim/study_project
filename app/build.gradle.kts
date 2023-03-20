plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.study_project"
    // compileSdk это версия сдк на которой вы пишете приложение
    // и например если у вас не скачена сдк 33 (SDK Manager)
    // то у вас не будет документации к классам Андроид типа Активити
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.study_project"
        // при попытке установить приложение на смартфон с версией Андроид ниже
        // уровня minSdk, например на 23 Api будет ошибка и оно не установится
        minSdk = 24
        // это версия Андроид под которую приложение разрабатывается,
        // но оно будет работать на всех версиях выше minSdk
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// это подключение зависимостей для этого модуля (app)
// сюда мы подключаем все дополнительные библиотеки
// например для загрузки изображений - Glide,
// для инверсии зависимостей - Dagger,
// для современной многопоточности - Coroutines,
// а так же если бы у приложения было несколько модулей
// то зависимость для других модулей так же бы указывалась тут
dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
}