package com.example.study_project.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TransferObject(
    // в эти переменные будут помещены функции из аргументов
    val functionOne: () -> Int,
    val functionTwo: (String, Int) -> Unit,
    val functionThree: (Person, Int) -> String,
    val functionFour: (Person) -> Person
) : Parcelable
//  : java.io.Serializable
// если раскомментировать строку выше и убрать @Parcelize и  : Parcelable
// то будет использован родной джавовый механизм Серилизации
// он медленнее более продвинутого Parcelable
// Parcelable требует описать реализацию своих методов - но тут все работает автоматически
// благодаря плагину  id("kotlin-parcelize")  - его можно найти в файле build.gradle.kts уровня модуля app

// Serializable и Parcelable нужны для передачи данных между фрагментами с помощью Bundle