package com.example.study_project.yandex

// задачка на понимание работы дата классов и коллекций
data class DogPerson(
    val poroda: String = "Дворняга",
) {
    var name: String = "Шарик"
}

val dogSet = hashSetOf(
    DogPerson(),
    DogPerson().apply {
        name = "Валера"
    },
    DogPerson().apply {
        name = "Валера"
    },
    DogPerson().apply {
        name = "Rex"
    },
    DogPerson(poroda = "Овчарка").apply {
        name = "Молли"
    }
)

fun main() {

    println("----")
    println("----")
    println("----")
    println("----")

    println(dogSet.size)

    dogSet.forEach { println(it.name) }

    println("----")
    println("----")
    println("----")
    println("----")
}
