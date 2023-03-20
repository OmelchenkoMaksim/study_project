package com.example.study_project.yandex


fun main() {
    val action1 = selectAction(1)
    println(action1(8, 5))    // 13

    val action2 = selectAction(2)
    println(action2(8, 5))    // 3

    val action3 = selectAction(3)
    println(action3(8, 5))    // 40
}

/**
 * по ключу выбираем функцию
 */
fun selectAction(key: Int): (Int, Int) -> Int =
    // определение возвращаемого результата
    when (key) {
        // тут мы видим что явной передачи параметров нет
        // и может быть не ясно откуда они берутся
        //
        1 -> ::sum
        2 -> ::subtract
        3 -> ::multiply
        else -> ::empty
    }


fun empty(a: Int, b: Int): Int {
    return 0
}

fun sum(a: Int, b: Int): Int {
    return a + b
}

fun subtract(a: Int, b: Int): Int {
    return a - b
}

fun multiply(a: Int, b: Int): Int {
    return a * b
}