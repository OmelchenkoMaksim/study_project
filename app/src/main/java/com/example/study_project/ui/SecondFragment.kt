package com.example.study_project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.study_project.R
import com.example.study_project.models.Person
import com.example.study_project.models.TransferObject


// у фрагментов всегда пустой конструктор (если что-то в него передать приложение упадет с ошибкой),
// constructor() я его явно указал что бы было ясно что он пустой, так вообще его не пишут
class SecondFragment constructor() : Fragment() {

    // lateinit позволяет нам сделать отложенную инициализацию
    // чтобы не делать наллабл тип Button?
    // так как при работе с nullable типом нужно постоянно проверять что там не нал
    lateinit var buttonToFirst: Button

    // кнопки для срабатывания функций
    lateinit var firstFunButton: Button
    lateinit var secondFunButton: Button
    lateinit var thirdFunButton: Button
    lateinit var fourthFunButton: Button


    lateinit var transferObject: TransferObject

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

        // тут мы находим все кнопки и инициализируем ими переменные
        buttonToFirst = requireView().findViewById<Button>(R.id.buttonToFirst)
        firstFunButton = requireView().findViewById<Button>(R.id.firstFunButton)
        secondFunButton = requireView().findViewById<Button>(R.id.secondFunButton)
        thirdFunButton = requireView().findViewById<Button>(R.id.thirdFunButton)
        fourthFunButton = requireView().findViewById<Button>(R.id.fourthFunButton)


        transferObject =
            arguments?.getSerializable("keyForFirstParameterInBundle") as TransferObject
        println(arguments.toString())

        // тут мы вешаем на каждую кнопку слушатель нажатий
        buttonToFirst.setOnClickListener {
            toFirstFragment()
        }
        firstFunButton.setOnClickListener {
            transferObject.functionOne.invoke()
        }
        secondFunButton.setOnClickListener {
            transferObject.functionTwo.invoke("Stub String", 0)
        }
        thirdFunButton.setOnClickListener {
            transferObject.functionThree.invoke(Person(), 0)
        }
        fourthFunButton.setOnClickListener {
            transferObject.functionFour.invoke(Person())
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
            val parcelToTheFragment: TransferObject = TransferObject(
                functionOne = param1,
                functionTwo = функцияКотораяПередаетсяВоФрагментВторымАргументом,
                functionThree = param3,
                functionFour = param4
            )

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
                    putSerializable(яКлючДляПервогоАргументаБандла, parcelToTheFragment)
//                    putSerializable(этоКлючДляВторогоПараметраБандла, funcOne)
//                    putSerializable(keyForThirdParameterInBundle, funcOne)
//                    putSerializable(keyForFourthParameterInBundle, funcOne)
                }
            }
        }


        /* Bundle представляет собой что-то вроде Map т.е. ассоциативный массив
        потому туда кладутся данные по ключам, по ним же и достаются,
        ключи лучше всегда иметь неизменяемые
        и класс String для этого идеально подходит
        т.к. он не мутабельный или immutable */
        const val яКлючДляПервогоАргументаБандла = "keyForFirstParameterInBundle" // пока что только один будем юзать
        const val этоКлючДляВторогоПараметраБандла = "этоКлючДляВторогоПараметраБандла"
        const val keyForThirdParameterInBundle = "keyForThirdParameterInBundle"
        const val keyForFourthParameterInBundle = "keyForFourthParameterInBundle"
    }

}
