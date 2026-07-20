package com.example.study_project.tasks.fresh

// понять что делает эта функция и и узнать ее

public inline fun <T, R> Iterable<T>.secretFunction_01(
    secretLambda: (T) -> R,
): List<R> {
    return secretFunction_02(
        ArrayList<R>(collectionSizeOrDefault(10)),
         secretLambda,
    )
}

/*

Что происходит внутри первой функции
Первая функция сама почти ничего не делает. Она:

Создаёт новый ArrayList<R>.
Передаёт его во вторую функцию.
Передаёт туда же лямбду.
Возвращает заполненный список.

Число 10 не добавляет десять элементов. Оно только резервирует внутреннее место приблизительно под десять элементов.
Зачем это нужно?
Если заранее известно, что будет 100 элементов, можно сразу выделить необходимое место и реже расширять внутренний массив.
*/

public inline fun <T, R, C : MutableCollection<in R>>
        Iterable<T>.secretFunction_02(
    destination: C,
    secretLambda: (T) -> R,
): C {
    for (item in this) {
        destination.add(secretLambda(item))
    }

    return destination
}

/*
Главное отличие:
map   → сам создаёт новую коллекцию
mapTo → получает готовую коллекцию
Официальное описание mapTo: преобразовать каждый исходный элемент и добавить результаты в переданный destination.

this
Поскольку функция объявлена как extension: Iterable<T>.secretFunction2
this — коллекция, на которой вызвали функцию.

*/

/*
Что могут дополнительно спросить на интервью
а)
Меняет ли map исходную коллекцию?
Нет

б)
Сколько раз вызывается лямбда?
По одному разу для каждого элемента

в)
Сложность
Для N элементов:
Время:  O(N)
Память: O(N)
Потому что нужно:

пройти по всем элементам;
создать новый список результатов.

г)
Что будет с пустой коллекцией?
val result = emptyList<Int>().map { it * 2 }
println(result) // []
Лямбда ни разу не вызовется.

д)
Может ли измениться тип элементов?

Да, это одна из главных целей map:
List<Int> → List<String>



*/


/*

Главный ответ:

secretFunction  = map
secretFunction2 = mapTo

Официально map применяет функцию преобразования к каждому элементу коллекции и возвращает список результатов,
а mapTo складывает результаты в переданную изменяемую коллекцию.

*/

/*

ОРИГИНАЛЫ

public inline fun <T, R> Iterable<T>.map(
    transform: (T) -> R,
): List<R> {
    return mapTo(
        ArrayList<R>(collectionSizeOrDefault(10)),
        transform,
    )
}

public inline fun <T, R, C : MutableCollection<in R>>
        Iterable<T>.mapTo(
    destination: C,
    transform: (T) -> R,
): C {
    for (item in this) {
        destination.add(transform(item))
    }

    return destination
}

*/


/*

Что делает map
map берёт каждый элемент исходной коллекции, преобразует его через лямбду и кладёт результат в новый список.

fun main() {
    val numbers = listOf(1, 2, 3)

    val doubledNumbers: List<Int> = numbers.map { number ->
        number * 2
    }

    println(doubledNumbers)
}



Внутри это приблизительно равно такому обычному коду:

fun main() {
    val numbers = listOf(1, 2, 3)
    val doubledNumbers = ArrayList<Int>()

    for (number in numbers) {
        val transformedNumber = number * 2
        doubledNumbers.add(transformedNumber)
    }

    println(doubledNumbers)
}

То есть map — это удобная форма цикла:

взять элемент
→ преобразовать
→ добавить результат
→ перейти к следующему




Разбираем первую функцию по частям

0) Сабж:
public inline fun <T, R> Iterable<T>.secretFunction(
    secretLambda: (T) -> R,
): List<R>

1)
public
Функция доступна из других классов и модулей.
В Kotlin public является видимостью по умолчанию, поэтому обычно его не пишут

2)
inline
Функция принимает лямбду и вызывается для каждой операции map.
inline разрешает компилятору подставить код функции и лямбды непосредственно в место вызова.
Это часто позволяет избежать создания отдельного объекта лямбды и дополнительного вызова функции, хотя увеличивает размер сгенерированного кода.

3)
<T, R>
Это generic-параметры.
T — тип исходного элемента.
R — тип результата преобразования.

val numbers: List<Int> = listOf(1, 2, 3)
val strings: List<String> = numbers.map { number ->
    "Число: $number"
}
Здесь:
T = Int
R = String

Вызов можно мысленно представить так:
map<Int, String>

4)
Iterable<T>.
Это extension-функция — функция-расширение для Iterable<T>.
Поэтому она вызывается через точку:
numbers.secretFunction { number ->
    number * 2
}
Iterable означает, что объект можно перебирать циклом:
for (item in collection) {
    ...
}

5)
Лямбда (T) -> R
secretLambda: (T) -> R

означает:
получает T
возвращает R

Например:
val transform: (Int) -> String = { number ->
    "Number: $number"
}

Здесь лямбда:

принимает Int;
возвращает String.

Она подходит под:
(Int) -> String


Особенности второй функции:

1)
Появился третий тип: C
C = тип результирующей коллекции

Например, можно передать ArrayList:
val destination = arrayListOf<String>()

val result: ArrayList<String> = listOf(1, 2, 3)
    .mapTo(destination) { number ->
        "Number=$number"
    }

2)
C : MutableCollection<in R>
Для начала можно читать так:

C должна быть изменяемой коллекцией, в которую разрешено добавлять значения типа R.
Функции нужен MutableCollection, потому что внутри вызывается:
destination.add(...)

В обычный List добавлять нельзя, а в MutableList можно!

3)
Что означает in R
in означает, что коллекция является потребителем значений типа R.
Она может принимать:

непосредственно R;
либо значения через коллекцию более общего типа.

Например, результат лямбды — String, но destination может быть MutableList<Any>:
val destination: MutableList<Any> = mutableListOf()

listOf(1, 2, 3).mapTo(destination) { number ->
    "Number=$number"
}

println(destination)

Это безопасно, потому что MutableList<Any> умеет принимать String:
String является Any

Для начального уровня достаточно запомнить:
MutableCollection<in R>
=
коллекция, куда можно безопасно добавлять R

*/

/*
Как правильно ответить интервьюеру

Хороший ответ примерно на 20–30 секунд:

Первая функция — это map, вторая — mapTo. map создаёт новый ArrayList, затем вызывает mapTo. mapTo проходит по исходному Iterable,
применяет лямбду (T) -> R к каждому элементу и добавляет результат в destination. T — исходный тип, R — результирующий тип, C — тип изменяемой коллекции.
Сложность — O(n) по времени и O(n) дополнительной памяти для map.

Более сильный ответ:

collectionSizeOrDefault(10) нужен для предварительного выбора capacity ArrayList: если размер коллекции известен, список сразу резервирует нужное место.
mapTo также позволяет выбрать destination, например MutableList, ArrayList или MutableSet.
*/

/*
Итоговая шпаргалка 🧠

secretFunction  = map
secretFunction2 = mapTo

T → тип входного элемента
R → тип выходного элемента
C → тип destination

map:
Iterable<T> + (T) → R
=
List<R>

Алгоритм:
создать destination
→ пройти по элементам
→ применить transform
→ добавить результат
→ вернуть destination

Сложность:
время O(n)
память O(n)
*/

fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int =
    if (this is Collection<*>) this.size else default
