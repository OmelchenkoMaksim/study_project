package com.example.study_project.yandex








/*


// до рефакторинга
object PiggyBank {

    private val listMoney = ArrayList<Money>() // список монеток/купюр
    private var isSmash: Boolean = false // свойство, определяющее, разбита ли копилка

    */
/**
     * putMoney(money: Money), с помощью этого метода можно положить новую монетку/купюру
     * в копилку и вывести сообщение «Добавлено в копилку $money». Если на момент
     * вызова метода копилка разбита, то нужно показать сообщение «Вы разбили копилку,
     * вы больше ничего положить туда не можете» и завершить выполнение метода.
     *//*

    fun putMoney(money: Money) {
        if (!isSmash) { // проверьте, не разбита ли копилка
            listMoney.add(money)
            println("Добавлено в копилку $money") // добавьте монетку в копилку
        } else {
            println("Вы разбили копилку, вы больше ничего положить туда не можете")
            return
        }
    }

    */
/**
     * shake(): Money? иногда копилку можно потрясти и оттуда точно выпадет монетка
     * (если есть, купюра выпасть не может). Выпавшая монетка должна быть удалена из копилки.
     * Если в копилке нет монеток, то вернуть null. Если на момент вызова метода копилка разбита,
     * то показать сообщение «Вы разбили копилку, больше оттуда ничего не вытрясти» и вернуть из метода null.
     *//*

    fun shake(): Money? {
        if (!isSmash) {  // проверьте, не разбита ли копилка
            return if (listMoney.find { it.isCoin } != null) {
                val iterator = listMoney.iterator()
                iterator.forEach {
                    if (it.isCoin) {
                        iterator.remove() // вытрясти монетку из копилки (если в копилке нет монетки, вернуть null). Помните, купюры вытрясти нельзя.
                        return it
                    }
                }
                null
            } else {
                null
            }
        } else {
            println("Вы разбили копилку, больше оттуда ничего не вытрясти")
            return null
        }
    }

    */
/**
     * smash(): ArrayList<Money>  выводит сообщение «Копилка разбита, вы достали оттуда: $moneys».
     * Также устанавливает индикатор (флаг), что копилка разбита — true и возвращает список всех монет/купюр пользователю.
     *//*

    fun smash(): ArrayList<Money> {
        println("Копилка разбита, вы достали оттуда: $listMoney")
        isSmash =
            true // установить флаг, что копилка разбита true, и вернуть все монетки, которые были в копилке
        return listMoney
    }
}

// создайте класс Money, который будет отражать сущность монетки/купюры с двумя полями: amount и isCoin
class Money private constructor(val amount: Float, val isCoin: Boolean) {

    // вы должны ограничить создание класса таким образом, чтобы можно было
    // создать только ограниченный набор номиналов (см. задание)
    companion object {
        // монетки номиналом: 10 копеек (0.1f), 50 копеек (0.5f) и 1 рубль (1f);
        val coins_10 = Money(amount = 0.1f, true)
        val coins_50 = Money(amount = 0.5f, true)
        val coins_100 = Money(amount = 1.0f, true)

        // купюры номиналом: 50, 100, 500 и 1000 рублей.
        val bill_50 = Money(amount = 50.0f, false)
        val bill_100 = Money(amount = 100.0f, false)
        val bill_500 = Money(amount = 500.0f, false)
        val bill_1000 = Money(amount = 1000.0f, false)
    }

    // переопределите метод toString() так, чтобы он возвращал строку типа "10 коп." или "1 руб.",
    // если это монетка, и "100 руб.", если это купюра
    override fun toString(): String {
        return if (isCoin && amount != 1.0f) {
            "${(amount * 100).toInt()} коп."
        } else {
            "${amount.toInt()} руб."
        }
    }
}


*/
