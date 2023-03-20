package com.example.study_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.study_project.R
import com.example.study_project.models.Person


// у фрагментов всегда пустой конструктор (если что-то в него передать приложение упадет с ошибкой),
// constructor() я его явно указал что бы было ясно что он пустой, так вообще его не пишут
class SecondFragment constructor() : Fragment() {

    // lateinit позволяет нам сделать отложенную инициализацию
    // чтобы не делать наллабл тип Button?
    // так как при работе с nullable типом нужно постоянно проверять что там не нал
    lateinit var buttonToFirst: Button

    // основная задача метода onCreateView показать в каком макете / лейауте будет
    // расположен наш фрагмент, тут это R.layout.fragment_second
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    // В этом методе мы пишем всю логику работы нашего фрагмента
    // в нем view уже не null т.к. все элементы интерфейса созданы
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonToFirst = requireView().findViewById<Button>(R.id.buttonToFirst)
        buttonToFirst.setOnClickListener {
            toFirstFragment()
        }
    }

    private fun toFirstFragment() {
        // popBackStack метод, который делает ровно тоже самое что и кнопка назад
        requireActivity().supportFragmentManager.popBackStack()
    }


    // статический объект который видно в общем пространстве имен
    // Примечание 1: это значит что ты можешь в рамках этого модуля обратиться к нему где хочешь
    // Примечание 2: все это приложение расположено в одном модуле app
    companion object {
        // статическая функция которая создает нестатический экземпляр этого фрагмента
        fun newInstance(
            param1: () -> Int,
            функцияКотораяПередаетсяВоФрагментВторымАргументом: (String, Int) -> Unit,
            param3: (Person, Int) -> String,
            param4: (Person) -> Person
        ): SecondFragment {

            // Между фрагментами можно передать данные используя Bundle,
            // а в бандл можно поместить всякие примитивы и еще Serializable и Parcelable.
            // то есть мы можем засунуть в него все что угодно,
            // достаточно указать что класс наследует Serializable,
            // в данном случае класс я решил не создавать, а создать
            // анонимный объект - объект без явного класса (но под капотом класс все таки есть)
            // и тут я просто указываю что этот объект наследует нужный для бандла интерфейс
            val funcOne = object : java.io.Serializable {
                // ЭТО ПОЛЯ АНОНИМНОГО ОБЪЕКТА
                // такие же как и у обычного, потому могут быть хоть private.
                // Вообще все что происходит внутри фигурных скобок анонимного объекта это
                // по сути описание его класса
                // только в этом случае у класса всего один экземпляр
                val functionInsideAnonObjectFirst = param1

            }

            // scope функция apply позволяет создать экземпляр класса и сразу что-то с ним сделать
            // в данном случае мы передаем аргументы при создании фрагмента
            // ЭТО ЛУЧШИЙ СПОСОБ ПЕРЕДАТЬ ДАННЫЕ ВО ФРАГМЕНТ
            // т.к. передавать в конструктор фрагмента ничего нельзя
            return SecondFragment().apply {

                // а вот и тот самый аргумент который принимает в себя Bundle,
                // который мы так же модифицируем при создании с помощью apply
                arguments = Bundle().apply {
                    // и вот здесь наконец происходит магия:
                    // мы помещаем сюда данные которые сделали Serializable,
                    // удобство этого подхода в том что от Serializable можно
                    // наследовать любой класс и ничего при этом не переопределять
                    // т.к. Serializable это маркерный интерфейс - интерфейс без методов
                    putSerializable(keyForFifthParameterInBundle, funcOne)
                    putSerializable(этоКлючДляВторогоПараметраБандла, funcOne)

                }
            }
        }


        /* Bundle представляет собой что-то вроде Map т.е. ассоциативный массив
        потому туда кладутся данные по ключам, по ним же и достаются,
        ключи лучше всегда иметь неизменяемые
        и класс String для этого идеально подходит
        т.к. он не мутабельный или immutable */
        const val keyForFirstParameterInBundle = "keyForFirstParameterInBundle"
        const val этоКлючДляВторогоПараметраБандла = "этоКлючДляВторогоПараметраБандла"
        const val keyForThirdParameterInBundle = "keyForThirdParameterInBundle"
        const val keyForFourthParameterInBundle = "keyForFourthParameterInBundle"
        const val keyForFifthParameterInBundle = "keyForFifthParameterInBundle"
    }

}