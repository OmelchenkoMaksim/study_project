

package com.example.study_project.tasks.fresh

/*

Приложение-заметочник: понять и оптимизировать код

Условие

Приложение — заметочник.

В коде представлена функциональность списка заметок:

заметки загружаются из API;

список можно фильтровать по тегу;

при нажатии на заметку загружается её полная модель;

после загрузки открывается экран деталей.

Требуется:

Разобраться, как работает код.

Найти архитектурные, логические и корутинные проблемы.

Предложить более корректную и поддерживаемую реализацию.

Разрешается изменять структуру классов и слоёв.

Исходный код:
*/


// Приложение — заметочник.
// В коде представлена функциональность списка заметок.
// Список можно фильтровать по тегу.

// ui слой


//
//data class NoteUI(
//    val id: Int,
//    val title: String,
//    val content: String,
//    val formattedDate: String,
//    val tags: List<String>,
//)
//
//class NoteFragment {
//
//    val viewModel by viewModels<NotesViewModel>()
//
//    // ...
//
//    fun onTagClicked(tag: String) {
//        viewModel.filterNotesByTag(tag)
//    }
//
//    fun observeClickedNote() {
//        viewModel.clickedNote.observe(viewLifecycleOwner) { note ->
//            note?.let {
//                openNoteDetailFragment(it)
//            }
//        }
//    }
//
//    private fun openNoteDetailFragment(note: Note) {
//        val fragment = NoteDetailFragment().apply {
//            arguments = Bundle().apply {
//                putInt("noteId", note.id)
//                putString("noteTitle", note.title)
//                putString("noteContent", note.content)
//                putString("noteDate", note.creationDate)
//            }
//        }
//
//        requireActivity().supportFragmentManager
//            .beginTransaction()
//            .replace(R.id.fragment_container, fragment)
//            .addToBackStack(null)
//            .commit()
//    }
//}
//
//// presentation слой
//
//class NotesViewModel @Inject constructor(
//    private val repository: NotesRepository,
//    private val filterNotesUseCase: FilterNotesUseCase,
//) : ViewModel() {
//
//    private val _filteredNotes = MutableLiveData<List<NoteUI>>()
//    val filteredNotes: LiveData<List<NoteUI>> = _filteredNotes
//
//    private val _clickedNote = MutableLiveData<NoteUI>()
//    val clickedNote: LiveData<NoteUI> = _filteredNotes
//
//    fun filterNotesByTag(tag: String?) {
//        _filteredNotes.value = filterNotesUseCase
//            .execute(tag)
//            .map { it.toUIModel() }
//    }
//
//    fun onNoteClicked(id: Int) = viewModelScope.launch {
//        val note = withContext(Dispatchers.IO) {
//            repository.getNoteById(id).toDomainModel()
//        }
//
//        _clickedNote.value = note
//    }
//
//    private fun Note.toUIModel(): NoteUI {
//        val dateFormat = SimpleDateFormat(
//            "dd/MM/yyyy",
//            Locale.getDefault(),
//        )
//        val formattedDate = dateFormat.format(creationDate)
//
//        return NoteUI(
//            id = id,
//            title = title,
//            content = content,
//            formattedDate = formattedDate,
//            tags = tags,
//        )
//    }
//}
//
//// domain слой
//
//data class Note(
//    val id: Int,
//    val title: String,
//    val content: String,
//    val creationDate: Date,
//    val tags: List<String>,
//)
//
//@Singleton
//class FilterNotesUseCase @Inject constructor(
//    private val repository: NotesRepository,
//    private val scope: CoroutineScope,
//) {
//    private var tag: String = ""
//
//    /*
//     * Строки этой функции на исходном скриншоте были обрезаны.
//     * Ниже — реконструкция по её использованию во ViewModel.
//     */
//    fun execute(tag: String?): List<Note> {
//        this.tag = tag.orEmpty()
//
//        return runBlocking(scope.coroutineContext) {
//            repository.getNotes().map { it.toDomainModel() }
//        }
//    }
//
//    fun isNoteShouldBeDisplayed(note: NoteNetwork): Boolean {
//        return tag == null || note.tags.contains(tag)
//    }
//
//    fun NoteNetwork.toDomainModel(): Note {
//        return Note(
//            id = id,
//            title = title,
//            content = content,
//            creationDate = creationDate,
//            tags = tags,
//        )
//    }
//}
//
//// data слой
//
//@Serializable
//data class NoteNetwork(
//    @SerialName("id")
//    val id: Int,
//
//    @SerialName("title")
//    val title: String,
//
//    @SerialName("content")
//    val content: String,
//
//    @SerialName("created_at")
//    @Serializable(with = DateSerializer::class)
//    val creationDate: Date,
//
//    @SerialName("tags")
//    val tags: List<String>,
//)
//
//@Singleton
//class NotesRepository @Inject constructor(
//    private val apiClient: NotesApiClient,
//    private val filterNotesUseCase: FilterNotesUseCase,
//) {
//
//    suspend fun getNoteById(id: Int): NoteNetwork {
//        return apiClient.getNoteById(id)
//    }
//
//    suspend fun getNotes(): List<NoteNetwork> {
//        return apiClient
//            .getAllNotes()
//            .filter { note ->
//                filterNotesUseCase.isNoteShouldBeDisplayed(note)
//            }
//    }
//}
//
//




/*

Что здесь предлагается найти

циклическую зависимость Repository ↔ UseCase;

mutable-состояние внутри singleton-use-case;

зависимость domain-слоя от NoteNetwork;

неправильную публикацию clickedNote;

несовпадение типов Note и NoteUI;

блокирующий runBlocking;

переключение dispatcher на неправильном слое;

передачу целой модели при навигации вместо одного id;

отсутствие единого Loading / Content / Error состояния.
        */



