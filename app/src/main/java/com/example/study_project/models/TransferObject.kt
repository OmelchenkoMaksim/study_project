package com.example.study_project.models

import android.util.Log

data class TransferObject(
    // в эти переменные будут помещены функции из аргументов
    val functionOne: () -> Int ,
    val functionTwo: (String, Int) -> Unit,
    val functionThree: (Person, Int) -> String,
    val functionFour: (Person) -> Person
) : java.io.Serializable