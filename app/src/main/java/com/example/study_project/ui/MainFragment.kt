package com.example.study_project.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.study_project.R
import com.example.study_project.models.Person

// Тот фрагмент, который видит пользователь при запуске этого приложения
class MainFragment : Fragment() {

    // обычно сверху мы показываем какие кнопки, текстовки и прочие вьюхи
    // есть на этом экране и кому мы хотим добавить дополнительную логику работы,
    // к примеру текст First Fragment я менять не собираюсь, и взаимодействовать с ним
    // тоже, потому тут он мне не нужен, а вот у кнопки я добавлю логику
    lateinit var buttonToSecond: Button

    // основная задача метода onCreateView показать в каком макете / лейауте будет
    // расположен наш фрагмент, тут это R.layout.fragment_main
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main, container, false)

    // В этом методе мы пишем всю логику работы нашего фрагмента
    // в нем view уже не null т.к. все элементы интерфейса созданы
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // requireView как и view это сущности интерфейса,
        // через них мы находим и можем работать с любой кнопкой, текстом и прочими вьюхами
        buttonToSecond = requireView().findViewById(R.id.buttonToSecond)


        // тут мы ставим слушателя нажатий на кнопку,
        // все что в фигурных скобках происходит при каждом нажатии
        buttonToSecond.setOnClickListener {
            startSecondFragment() // срабатывает метод открывающий второй фрагмент
        }

    }


    // все что дальше без override это обычные методы класса, пишите таких сколько угодно


    // метод описывает логику старта второго фрагмента
    private fun startSecondFragment() {
        // тут и дальше, пишу названия русскими буквами, просто что бы показать что так тоже можно,
        // НИКОГДА так не делайте, если в вашем гитхаб такое увидят на работу точно не позовут! :)
        val инстансВторогоФрагментаСПараметрами = SecondFragment.newInstance(
            param1 = ::functionToTransferAsFirstArgument,
            функцияКотораяПередаетсяВоФрагментВторымАргументом = { яСтрокаДляЭтойФункции: String,
                                                                   яЦелоеЧисло: Int ->
                Log.e("myLog", {}.javaClass.enclosingMethod.name)
                Log.e("myLog", "$яСтрокаДляЭтойФункции $яЦелоеЧисло")
            },
            param3 = ::функцияКотораяЛетитПрямоВБандлТретьимАргументом,
            param4 = ::`serial-Или-Нет-Вот-IDontKnow`
        )

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(
                R.id.mainActivityContainer,
                инстансВторогоФрагментаСПараметрами
            )
            // добавить в Бэк Стэк позволяет нам ходить назад с помощью кнопки назад и метода popBackStack:)
            // без добавления фрагмента в бэк стэк при нажатии кнопки назад эта программа просто закроется
            .addToBackStack("SecondFragment")
            .commit()
    }

    // тут ниже в логировщике я использую такую конструкцию
    // {}.javaClass.enclosingMethod.name
    // выглядит страшно, но боятся не надо -
    // она всего-навсего говорит как называется метод где ее применил
    // если убрать .name в конце, то она еще и расскажет в каком пакете находится

    fun functionToTransferAsFirstArgument(): Int {
        Log.e("myLog", "oneTwo from Main")
        Log.e("myLog", {}.javaClass.enclosingMethod.name)
        return 2
    }

    fun oneTwoThree(str: String, num: Int): Unit {
        Log.e("myLog", {}.javaClass.enclosingMethod.name)
        println("Я Принт ЛАЙН !!! из меода ${{}.javaClass.enclosingMethod.name}")
    }

    fun функцияКотораяЛетитПрямоВБандлТретьимАргументом(per: Person, num: Int): String {
        Log.e("myLog", {}.javaClass.enclosingMethod.toString())
        return ""
    }

    fun `serial-Или-Нет-Вот-IDontKnow`(per: Person): Person {
        Log.e("myLog", {}.javaClass.enclosingMethod.name)
        return Person(name = per.name, age = per.age)
    }


    // статический объект который видно в общем пространстве имен
    // Примечание 1: это значит что ты можешь в рамках этого модуля обратиться к нему где хочешь
    // Примечание 2: все это приложение расположено в одном модуле app
    companion object {
        // статическая функция которая создает нестатический экземпляр этого фрагмента,
        // ВАЖНО: можно создать этот фрагмент и без нее, просто вызвав MainFragment()
        // там где хотим, в отличие от фрагмента SecondFragment куда мы хотим передать данные,
        // если бы мы хотели передать данные в этот MainFragment() то нам также без этой
        // статики было бы не обойтись, а тут она избыточна, просто оставил для сравнения
        // с созданием второго фрагмента SecondFragment
        fun newInstance() = MainFragment()
    }
}