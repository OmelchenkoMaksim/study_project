# Senior Android Engineering Knowledge Base для self-assessment в 2026

## Рамка и критерии

Эта база знаний калибрована не под “знает API”, а под “умеет принимать архитектурные и эксплуатационные решения в production”. За основу взят современный Android-стек, который сам Android team описывает через unidirectional data flow, state holders, coroutines/Flow, модульность и performance-first подход. При этом я **осознанно** держу фокус на вашем целевом наборе: Jetpack Compose как основной UI, Coroutines/Flow, MVI state machine, Dagger 2 с ручной сборкой графа, Room + DataStore, Retrofit + OkHttp, custom Fragment-based back stack, JUnit5 + MockK + Compose UI testing, Version Catalogs, R8, Baseline Profiles и modularization. Официальная документация Android в 2026 году действительно рекомендует Hilt для DI и Compose-first navigation стек вроде Navigation 3 для Compose-only приложений, а Interop-гайд прямо называет Fragment + ComposeView скорее переходной или hybrid-стратегией. Но для крупных кодовых баз с кастомной навигацией, feature-графами и постепенной миграцией именно такой стек по-прежнему реалистичен и требует senior-level глубины. citeturn0search23turn9search4turn4search22turn12view2turn3search1

Ниже каждый раздел устроен как interview-calibration: короткая theory block, затем вопросы, которые отличают surface-level familiarity от системного engineering мышления. “Senior answer” здесь не означает “самый длинный”; это ответ, который правильно ставит границы, проговаривает trade-offs, связывает локальное решение с жизненным циклом экрана, back stack, тестируемостью, деградацией производительности и стоимостью поддержки. citeturn0search19turn3search25turn0search7

## Concurrency и reactive state

### Kotlin и coroutines internals

**Theory block.**  
Senior Android-разработчик в 2026 должен мыслить не “где launch”, а “кто владеет scope, кто владеет dispatcher, и как failure/cancellation распространяются по дереву job-ов”. Structured concurrency означает, что дочерние корутины живут внутри родительского scope и влияют на завершение родителя; исключение в child по умолчанию отменяет siblings и parent, если не используется supervision. `CoroutineExceptionHandler` — это не бизнес-обработка ошибок, а последний рубеж для **uncaught** исключений корневой корутины; он не “чинит” обычный control flow и не перехватывает всё подряд. Cancellation кооперативна: она проявляется в suspension points и должна уважаться в CPU-bound коде и cleanup-блоках. На Android важны main-safety и явное владение dispatcher-ами: код data/domain слоя не должен случайно блокировать Main, а hardcoded dispatchers ухудшают тестируемость и контроль исполнения. Отдельный маркер зрелости — почти рефлекторное избегание `GlobalScope`: Android-документация прямо связывает lifecycle-tied scope с высвобождением ресурсов и отсутствием утечек. citeturn0search4turn0search0turn0search16turn0search12turn6search17

#### Вопрос

**Когда нужен `supervisorScope`, а когда обычный `coroutineScope`?**

**Junior answer.**  
`coroutineScope` отменяет все дочерние корутины при ошибке одной из них, а `supervisorScope` — нет. Поэтому `supervisorScope` нужен, когда задачи независимы. citeturn0search0turn0search4

**Middle answer.**  
Если экран грузит несколько независимых блоков данных, `supervisorScope` позволяет показать частичный успех и локально обработать ошибку одного запроса. Если же дочерние задачи вместе формируют один атомарный результат, правильнее `coroutineScope`, чтобы не получить полусобранное состояние. citeturn0search0turn6search17

**Senior answer.**  
Выбор определяется не “независимы/не независимы” сам по себе, а контрактом уровня state machine. Если MVI-state допускает деградацию по частям — supervision уместен; если reducer ожидает консистентный агрегат, supervision превращает failure semantics в неявную бизнес-логику и усложняет восстановление. Senior ещё проговаривает, кто собирает ошибки: actor, orchestration use case или слой repository, и не смешивает supervision с бесконтрольным проглатыванием ошибок. citeturn0search0turn0search3turn0search11

**Why this question matters.**  
Вопрос проверяет, понимает ли человек корутины как дерево жизненных циклов и failure propagation, а не как “асинхронные функции”. В production это напрямую влияет на частичный рендеринг, retry, пустые экраны, race conditions и предсказуемость state transitions. citeturn0search0turn0search16

#### Вопрос

**Почему `CoroutineExceptionHandler` почти никогда не является основным механизмом обработки ошибок на экране?**

**Junior answer.**  
Потому что он вызывается не всегда и лучше использовать `try/catch`. Обычно ошибки надо ловить рядом с кодом, который их ожидает. citeturn0search16turn0search0

**Middle answer.**  
Он подходит для логирования и crash reporting корневых coroutine failures, но не для нормальной бизнес-обработки. Исключения из `async` приходят в `await`, а child-корутины в structured concurrency часто завершают parent до того, как handler вообще становится релевантным. citeturn0search16turn0search0

**Senior answer.**  
На senior-уровне ошибка — часть контракта use case или actor-а, а не факт существования глобального handler-а. Если экрану нужно перейти в `Error`, показать `News`, откатить optimistic state или решить, retry-able ли сбой, это должна делать state machine, а `CoroutineExceptionHandler` остаётся только страховочной сеткой для дефектов, которые уже вышли за пределы нормального control flow. citeturn0search16turn0search3turn14view0

**Why this question matters.**  
Такой вопрос быстро отделяет людей, которые действительно строили fault model на экране, от тех, кто видел handler в документации и переоценил его роль. Ошибка управления здесь почти всегда превращается либо в silent failure, либо в неуправляемый crash/noise в логах. citeturn0search16turn6search17

#### Вопрос

**Кто должен владеть dispatcher selection и почему hardcode `Dispatchers.IO` глубоко в коде часто плохая идея?**

**Junior answer.**  
Dispatcher нужен, чтобы выбрать поток. Если захардкодить его везде, код станет менее гибким. citeturn6search17turn7search9

**Middle answer.**  
Инжектируемые dispatchers упрощают тесты и позволяют `runTest` контролировать исполнение. Кроме того, main-safety — это обязанность слоя, который делает blocking или expensive work, а не UI-состава, который не должен гадать, где что переключать. citeturn6search0turn6search8turn7search9

**Senior answer.**  
У senior developer dispatcher — часть boundary contract: domain/use case может быть main-safe без знания о платформе, repository может изолировать blocking I/O, а orchestration-слой — ограничивать concurrency policy. Hardcoded dispatcher внутри глубокой зависимости закрепляет execution policy в неправильном месте, затрудняет deterministic tests, скрывает производственные bottleneck-ы и мешает централизованно менять scheduling under load. citeturn6search0turn6search8turn6search16

**Why this question matters.**  
Вопрос проверяет, понимает ли человек coroutines как средство управления исполнением системы, а не просто как замену callback-ам. В больших приложениях это влияет на reproducibility тестов, responsiveness UI и расследование перформанс-проблем. citeturn6search0turn6search8

### Flow, StateFlow и SharedFlow

**Theory block.**  
Зрелое понимание Flow начинается с выбора **семантики потока**, а не типа “который удобнее”. Обычный `Flow` обычно cold: каждый collector снова запускает upstream. `SharedFlow` и `StateFlow` hot: их активная инстанция живёт независимо от collectors. `StateFlow` — специализированный `SharedFlow` для состояния: он всегда имеет текущее значение, транслирует только последнее состояние и conflates emissions через `Any.equals`. `SharedFlow` лучше подходит для событий и broadcast-сигналов, где важны `replay`, buffer semantics и отсутствие “обязательного current value”. `stateIn` и `shareIn` создают sharing coroutine в заданном scope; реальная стоимость и поведение зависят от `SharingStarted`, replay и buffering. При миграции с RxJava важно не переносить механически категории Observable/Subject в Flow-мир: cancellation, buffering и подписка/подписчик-семантика отличаются. Для тестирования senior должен уверенно различать тесты “flow как input” и “flow как output”, использовать fake producers, continuous collection и `runTest`, а не надеяться, что hot stream “как-нибудь успеет эмитнуть”. citeturn0search13turn7search23turn0search9turn7search6turn7search3turn7search10turn6search4turn7search0

#### Вопрос

**Когда UI state должен быть `StateFlow`, а когда нужен `SharedFlow`?**

**Junior answer.**  
`StateFlow` — для состояния экрана, `SharedFlow` — для событий. Например, список данных — `StateFlow`, snackbar — `SharedFlow`. citeturn7search23turn0search9

**Middle answer.**  
Если новый подписчик должен немедленно увидеть текущее значение — это `StateFlow`. Если повторная доставка старого значения опасна или бессмысленна, как у navigation/snackbar/result events, нужен отдельный `SharedFlow` или `News`-канал без хранения события в state. citeturn7search23turn14view0turn0search9

**Senior answer.**  
На senior-уровне вопрос формулируется как “какова replay-семантика этого доменного факта?”. UI state обязан быть idempotent и восстанавливаемым, поэтому он живёт в `StateFlow`; одноразовые эффекты не являются частью durable state и должны жить отдельно, иначе back stack, ресабскрайб и process recreation начинают переигрывать старые действия. citeturn7search23turn14view0turn0search11

**Why this question matters.**  
Это один из самых дешёвых способов проверить, видел ли человек реальные баги с повторным navigation, фантомными snackbar и “событиями в state”. Ответ показывает, есть ли у кандидата модель времени и повторной подписки, а не только знание названий классов. citeturn14view0turn0search9

#### Вопрос

**Какие скрытые проблемы приносит бездумное использование `stateIn` и `shareIn`?**

**Junior answer.**  
Они делают flow hot и позволяют шарить upstream. Нужно правильно выбрать scope и `SharingStarted`. citeturn7search6turn7search3turn7search10

**Middle answer.**  
Ошибка часто в том, что долговечный scope делает seemingly screen-local stream фактически process-wide, а `WhileSubscribed` с неподходящим timeout перезапускает дорогой upstream чаще, чем ожидалось. Ещё одна ловушка — replay/buffer semantics, из-за которых подписчик получает “неожиданно старое” значение. citeturn7search6turn7search3turn7search10

**Senior answer.**  
Senior видит `stateIn/shareIn` как изменение жизненного цикла источника данных: вы не просто “кешируете поток”, вы вводите новый hot-owner с собственной cancellation и failure model. Неправильный owner ломает ресурсные контракты, размазывает ответственность за кэш между feature и repository и может скрыто превратить cheap collector в держателя сети, БД-слушателя или другого дорогого источника. citeturn7search6turn7search3turn0search13

**Why this question matters.**  
В боевых приложениях именно тут рождаются утечки, внезапные перезапросы и “почему после ухода со screen upstream ещё жив?”. Вопрос проверяет ownership thinking, а не знание пары операторов. citeturn7search3turn0search12

#### Вопрос

**Чем миграция с RxJava на Flow отличается от механической замены типов?**

**Junior answer.**  
Есть адаптеры `asFlow`, `asObservable`, `asFlowable`, поэтому миграция возможна постепенно. Но модели всё равно разные. citeturn7search0turn7search1turn7search8

**Middle answer.**  
В RxJava команды часто строятся вокруг Subject-ов и backpressure-типов, а во Flow нужно заново определить cold/hot ownership, cancellation и buffer policy. Если просто обернуть старые цепочки, можно сохранить старую сложность, но потерять предсказуемость structured concurrency и тестируемость. citeturn7search0turn7search16turn0search13

**Senior answer.**  
Хорошая миграция — это пересборка boundary contracts: `Single`-подобные вещи становятся `suspend`, durable state становится `StateFlow`, transient events — `SharedFlow`/News, а interop-адаптеры остаются на границах. Senior не тащит Rx-мышление о “всё поток”, если семантически часть API должна стать обычной `suspend`-операцией с явной отменой, structured scope и simpler error surface. citeturn7search0turn5search3turn0search4

**Why this question matters.**  
Этот вопрос вскрывает, мигрировал ли человек реально legacy-код, или знает только слова “Flow вместо Rx”. В production цена ошибки — смесь двух ментальных моделей, где никто уже не понимает ни ownership, ни доставку событий. citeturn7search0turn0search13

### Compose state, recomposition и performance

**Theory block.**  
Senior-level Compose — это прежде всего понимание того, **почему** происходит recomposition и где именно читается state. Compose может скипать recomposition, если входы skippable и stable; стабильность определяется компилятором по правилам immutability/наблюдаемости, а не по ощущениям разработчика. В 2026 Android docs отдельно рекомендуют сначала **диагностировать** stability-проблемы, а уже потом чинить их — например, через strong skipping, аннотации стабильности, изменение структуры типов или перенос state reads в более дешёвую фазу, вроде draw. State hoisting остаётся базовой дисциплиной: состояние поднимается к lowest common ancestor и наружу экспортируется как immutable state + intents/events. Side-effect API (`LaunchedEffect`, `DisposableEffect`, `rememberUpdatedState`, `produceState`, `snapshotFlow`) нужны не “чтобы код сработал”, а чтобы удержать lifecycle-awareness и UDF. Типичные senior-грабли: нестабильные DTO в hot composable tree, отсутствие stable keys в lazy list, чтение часто меняющегося state в composition вместо layout/draw, и неправильная интеграция `ComposeView` во Fragment без корректной disposal strategy. citeturn10search2turn10search4turn10search9turn10search3turn10search1turn10search18turn10search16turn12view1

#### Вопрос

**Когда нестабильный параметр — это реальная проблема, а когда его можно не трогать?**

**Junior answer.**  
Нестабильные параметры могут вызывать лишние recomposition, поэтому лучше делать классы stable. Но не всегда это критично. citeturn10search2turn10search9

**Middle answer.**  
Если composable не находится в горячем пути, вызывается редко или реальная трассировка не показывает лишней работы, “чинить стабильность” может быть преждевременной оптимизацией. Документация Compose прямо советует сначала диагностировать, а уже потом применять стабильность/strong skipping. citeturn10search9turn10search0turn10search4

**Senior answer.**  
Senior смотрит не на annotation count, а на cost model: где recomposition происходит, можно ли её скипнуть, и что реально дорого — composition, layout, draw или side-effects вокруг. Иногда правильнее не делать тип “stable”, а разрезать tree, поменять state ownership, вынести derived values или сместить чтение state в lambda-based modifier, чтобы Compose обходил более тяжёлые фазы. citeturn10search18turn10search2turn10search11

**Why this question matters.**  
Вопрос отличает людей, которые умеют профилировать Compose, от людей, которые видят любую нестабильность как дефект сами по себе. Это напрямую влияет на стоимость support-а: можно потратить недели на annotations и не починить реальный bottleneck. citeturn10search9turn10search17

#### Вопрос

**Как выбирать между `LaunchedEffect`, `DisposableEffect` и `rememberUpdatedState`?**

**Junior answer.**  
`LaunchedEffect` запускает корутину, `DisposableEffect` нужен для подписки/очистки, `rememberUpdatedState` помогает не рестартовать эффект при обновлении callback-а. citeturn10search1

**Middle answer.**  
Если нужна lifecycle-aware coroutine на время присутствия composable — `LaunchedEffect`. Если вы регистрируете listener, observer или callback и обязаны корректно отписаться — `DisposableEffect`. Если долгоживущий эффект должен видеть свежий lambda/reference без перезапуска, используйте `rememberUpdatedState`, иначе легко получить stale callback. citeturn10search1

**Senior answer.**  
Senior ещё спрашивает: должен ли этот эффект вообще жить в Composition, или это уже responsibility state holder-а/MVI-feature. Эффекты в Compose не должны ломать UDF: навигация, snackbar, analytics и внешние подписки должны входить в UI как реакция на state/event contract, а не превращать composable в второй orchestration-layer. citeturn10search1turn10search3turn0search11

**Why this question matters.**  
Здесь проверяется зрелость в работе с declarative UI: человек либо понимает Composition lifecycle, либо просто “подбирает API, пока работает”. Production-цена ошибок — дублирующиеся подписки, потерянные cleanup, stale callbacks и нестабильные тесты. citeturn10search1turn6search11

#### Вопрос

**Почему в lazy списках так важны keys и где часто прячется реальная perf-проблема?**

**Junior answer.**  
Keys помогают Compose правильно сопоставлять items при reorder/insert/remove. Без keys remembered state может поехать. citeturn10search16

**Middle answer.**  
Настоящая проблема часто не только в самих keys, а в том, что item читает слишком много общего state или пересоздаёт нестабильные параметры на каждый frame. Тогда даже правильные keys не спасают от широких recomposition. citeturn10search16turn10search18

**Senior answer.**  
Senior думает о списке как о горячем контуре производительности: нужны stable item models, локализованные state reads, аккуратные remember/derivedStateOf и минимально возможные invalidation regions. Хороший ответ обычно связывает keys с сохранением item-local state, а perf — с тем, сколько узлов мы заставляем дойти до composition/layout/draw на каждое изменение. citeturn10search16turn10search18turn10search11

**Why this question matters.**  
Списки — место, где surface-level Compose knowledge ломается быстрее всего. Ответ показывает, видел ли человек реальные проблемы скролла, partial updates и неожиданного state drift внутри item-ов. citeturn10search16turn10search17

### MVI и unidirectional data flow

**Theory block.**  
Сильная MVI-модель на Android — это не просто “один `StateFlow<State>` на экран”. Суть в том, что state production идёт как pipeline: inputs → state holder/state machine → immutable UI state → render. В MVICore-подобной схеме `Actor` занимается асинхронной или условной работой и порождает `Effect`, `Reducer` детерминированно вычисляет новый `State`, а `NewsPublisher` обрабатывает одноразовые сигналы, которые бессмысленно хранить в state — например, redirect, snackbar, transient error. Это критично: MVICore прямо описывает `News` как решение recurring-проблемы “events that should be consumed only once” и рекомендует не класть такие вещи в state, чтобы новые подписчики не переигрывали старые события. Senior-понимание MVI — это ещё и чувство меры: MVI отлично масштабирует сложные экраны с параллельными источниками, retry, optimistic updates и явной replay-семантикой, но легко деградирует в boilerplate, если reducer становится мусоросборником, а actor — скрытым god object. Android-гайды по UDF здесь хорошо совпадают с MVI: immutable state наружу, intents внутрь, логика state production вне UI. citeturn0search3turn0search11turn15search5turn15search0turn14view0turn15search4

#### Вопрос

**Почему navigation/snackbar/result event не должны жить внутри screen state как флаг?**

**Junior answer.**  
Потому что state переиспользуется и событие может сработать повторно. Для одноразовых событий лучше отдельный канал. citeturn14view0

**Middle answer.**  
Если положить redirect/snackbar в state, новый collector или возврат на экран может повторно получить уже устаревшее действие. MVICore отдельно выделяет `News` именно как способ держать single-consumption events отдельно от durable state. citeturn14view0turn15search5

**Senior answer.**  
Senior отвечает через семантику времени: state должен быть воспроизводимым снимком экрана, а event — факт перехода/реакции, у которого другая replay policy. Смешение этих двух временных моделей обычно ломает back stack, process recreation и тесты, потому что “история того, что нужно сделать один раз” маскируется под “текущее состояние”. citeturn14view0turn0search11turn2search18

**Why this question matters.**  
Это практически идеальный production-фильтр на зрелость MVI/UDF-мышления. Если человек настаивает на `showSnackbar = true` в state без разговора о replay-семантике, почти наверняка он ещё не проживал реальные баги жизненного цикла. citeturn14view0turn0search11

#### Вопрос

**Где проходит граница между `Actor` и `Reducer`?**

**Junior answer.**  
`Reducer` меняет state, `Actor` делает асинхронную работу. Обычно network и validation идут в `Actor`, а чистое преобразование state — в `Reducer`. citeturn15search0turn15search5

**Middle answer.**  
Хороший reducer должен быть максимально dumb и детерминированным: получил effect — выдал новый state. `Actor` инкапсулирует async jobs, conditional branching и взаимодействие с внешним миром, чтобы бизнес-ветвление не пряталось внутри state reduction. citeturn15search0turn15search3

**Senior answer.**  
Senior обычно обсуждает не только синтаксическую границу, но и эволюцию feature: если reducer начинает зависеть от времени, IO или внешних сервисов, вы теряете replayability и компактность reasoning. Если actor, наоборот, начинает тайно мутировать всё подряд и решать половину state machine, MVI теряет прозрачность и тестируемость; значит, границы надо сдвигать на уровне эффектов и action-модели, а не только классов. citeturn15search0turn15search1turn15search4

**Why this question matters.**  
В production главная польза MVI — не “модное слово”, а возможность локально рассуждать о state transitions и error handling. Этот вопрос быстро показывает, понимает ли человек систему как state machine, или просто воспроизводит шаблон из прошлой codebase. citeturn0search3turn15search5

## Архитектура, DI и data layer

### Dagger 2 с ручной сборкой графа

**Theory block.**  
Senior-уровень в Dagger 2 — это способность проектировать граф зависимостей как часть архитектуры приложения, а не просто “собрать инъекцию”. Dagger остаётся compile-time DI framework, который генерирует код, близкий к hand-written wiring, и валидирует binding graph на этапе компиляции. Для Android-кодовой базы с custom navigation это особенно ценно: вы явно контролируете feature graph, scopes, lifetime объектов и границы модулей. `Subcomponent` наследует bindings родителя и удобен для вложенных scope-ов; multibinding позволяет собирать plugin-like registry без knowledge о конкретных реализациях в месте использования. В то же время merged key space subcomponent-ов может размыть policy boundaries, если их не подкреплять visibility/qualifier discipline. Assisted injection нужен там, где часть параметров приходит из runtime, но такие типы по правилам Dagger не скопируются и не могут быть scoped. Hilt в 2026 остаётся официально рекомендуемым на Android и упрощает Android-specific boilerplate через predefined components, однако платой становятся меньшая явность графа, большее следование framework conventions и не всегда удобные trade-offs в глубоких multi-module/custom-host архитектурах. citeturn1search0turn1search2turn1search4turn1search11turn9search2turn9search6turn9search1turn9search4turn9search21turn9search11

#### Вопрос

**Когда для feature-модуля вы предпочтёте `Subcomponent`, а когда component dependency?**

**Junior answer.**  
`Subcomponent` подходит для вложенного графа, а component dependency — когда графы больше изолированы. Оба варианта рабочие. citeturn1search4turn1search11

**Middle answer.**  
Если feature должен естественно наследовать родительские bindings и жить во вложенном scope, subcomponent обычно проще. Если нужна более жёсткая изоляция и явный exported surface, зависимости компонентов могут быть понятнее, хотя они сложнее для распространения multibindings. citeturn1search4turn1search11

**Senior answer.**  
Senior смотрит на организационную границу: кто “владеет” binding key space и насколько допустимо, чтобы parent bindings были доступны child по умолчанию. Если важнее дебаггинг и естественное наследование scope-ов — subcomponent; если важнее enforce-ить публичный контракт feature-модуля как библиотеки — component dependency может дать дисциплину ценой дополнительной сложности и less ergonomic graph composition. citeturn1search11turn1search4turn1search2

**Why this question matters.**  
Вопрос проверяет, умеет ли человек связывать DI с модульностью и ownership boundaries. В крупных приложениях это влияет не только на кодогенерацию, но и на то, насколько команда способна локально изменять feature без пробоя по всему графу. citeturn3search1turn1search11

#### Вопрос

**Чем ручной Dagger 2 может быть лучше Hilt именно в большой multi-module codebase с custom Fragment navigation?**

**Junior answer.**  
Hilt проще в настройке, а Dagger 2 даёт больше контроля. Поэтому в сложных проектах Dagger 2 иногда удобнее. citeturn9search1turn9search4

**Middle answer.**  
Ручной Dagger позволяет явно собирать feature graph под конкретный back stack entry, Fragment host или runtime factory и не подстраиваться под стандартные Android components Hilt. Это особенно полезно, когда приложение уже имеет собственную навигационную и scope-модель. citeturn1search0turn9search1turn9search15

**Senior answer.**  
Senior не отвечает лозунгом “Dagger гибче”, а проговаривает стоимость владения: manual Dagger выигрывает там, где явный graph ownership, независимые feature boundaries и нестандартные lifetime-модели критичнее boilerplate reduction. Hilt выигрывает там, где стандартный Android lifecycle и build ergonomics дают больше пользы; но если архитектура уже декомпозирована вокруг собственных entry points и scope-ов, Hilt может навязать другой центр тяжести системы, а не просто “сократить код”. citeturn9search1turn9search4turn9search21turn9search11

**Why this question matters.**  
Такой вопрос отделяет людей, которые реально выбирали DI-стратегию под архитектуру, от тех, кто просто повторяет рекомендации по умолчанию. Для senior это не религиозный спор, а решение о стоимости дальнейшей эволюции кодовой базы. citeturn9search4turn1search0

### Room и DataStore

**Theory block.**  
Старший Android-инженер должен видеть Room и DataStore как два разных инструмента с разной durability/shape-семантикой. Room — это слой над SQLite с compile-time проверкой SQL, поддержкой `suspend`-операций, observable queries через `Flow` и полноценными migration paths. Для production важны не “DAO работает”, а локальный source of truth, корректные transaction boundaries, предсказуемая миграция схемы и тестирование этих миграций до релиза. Room хорошо ложится на offline-first и на архитектуру, где UI пересобирается из локального состояния после process death. DataStore, напротив, рассчитан на небольшие объёмы данных, которые нужно хранить асинхронно, консистентно и транзакционно; Android docs прямо позиционируют его как замену `SharedPreferences` для новых сценариев. В 2026 рекомендации особенно подчёркивают: DataStore должен жить в data layer как singleton и не читаться/писаться прямо из composable. Практический senior-критерий здесь простой: transient UI state — не тащить в Room; сложные реляционные сущности — не запихивать в DataStore; `SavedStateHandle` — не путать с постоянным хранилищем. citeturn2search10turn7search12turn2search0turn2search5turn2search17turn2search14

#### Вопрос

**Как выбирать между Room, DataStore и `SavedStateHandle`?**

**Junior answer.**  
Room — для таблиц и сложных данных, DataStore — для небольших настроек, `SavedStateHandle` — для восстановления состояния экрана. Это разные уровни хранения. citeturn2search10turn2search5turn2search2

**Middle answer.**  
Если данные должны переживать процесс и быть source of truth приложения — это Room или DataStore, в зависимости от формы и сложности данных. `SavedStateHandle` нужен для небольшого UI-related state, достаточного, чтобы после process death повторно собрать screen state из data layer, а не хранить всё содержимое экрана. citeturn2search2turn10search15turn8search9

**Senior answer.**  
Senior отвечает через lifespan и reconstruction cost. Если значение — часть durable business data, оно живёт на диске; если это navigation/filter/query key, достаточный для повторной загрузки и восстановления user journey, его место в `SavedStateHandle`; если это purely ephemeral Compose-local detail, иногда достаточно `rememberSaveable`/`rememberSerializable` без протаскивания в ViewModel. citeturn2search2turn8search18turn8search17turn10search15

**Why this question matters.**  
В production именно путаница между этими уровнями даёт либо избыточное хранение, либо потерю состояния после process death, либо неадекватно раздутые ViewModel. Ответ показывает, умеет ли человек мыслить сроком жизни данных, а не только API. citeturn8search9turn2search17

#### Вопрос

**Где проходит граница между auto migration и manual migration в Room?**

**Junior answer.**  
Auto migration подходит для простых изменений схемы, а сложные случаи лучше делать вручную. Нужно следить за совместимостью. citeturn2search0

**Middle answer.**  
Как только изменение перестаёт быть тривиальным rename/add/drop и требует реальной трансформации данных, контроля над SQL или нестандартной логики, manual migration безопаснее и понятнее. Senior-level hygiene здесь — ещё и тестировать migrations, а не верить, что схема “очевидная”. citeturn2search0turn2search10

**Senior answer.**  
Хороший senior-ответ включает operational thinking: миграция — это не только схема, но и пользовательские данные, размер таблиц, идемпотентность, rollback story и observability релиза. Если вы не можете уверенно описать, что случится с содержимым базы на реальном устройстве после обновления, значит автоматизация уже не покрывает ваш риск-профиль и нужна ручная миграция с тестом. citeturn2search0turn2search10

**Why this question matters.**  
Этот вопрос проверяет, работал ли человек с upgrade path настоящих пользователей, а не только с clean install. Уровень senior часто виден именно в том, насколько он думает про уже существующие данные, а не только про новую схему. citeturn2search0

### Retrofit и OkHttp

**Theory block.**  
Senior-понимание networking на Android — это не “создал Retrofit interface”, а контроль над transport semantics, caching, error surface и orchestration. Retrofit умеет представлять API как interface; для Kotlin он поддерживает `suspend`-методы, которые либо возвращают `Response<T>`, либо сразу body и в случае non-2xx бросают `HttpException`. Важно не смешивать модели: `suspend fun` с возвращаемым `Call<T>` в Retrofit помечается как некорректная комбинация. OkHttp остаётся фундаментом: он по умолчанию эффективен за счёт HTTP/2, connection pooling, transparent GZIP и response cache. Но cache работает не “магически”: документация отдельно предупреждает, что ответ должен быть прочитан полностью, иначе он не закешируется; также разумно держать один shared `OkHttpClient`, а не плодить клиентов и кэши на одно и то же. Interceptors — мощный, но опасный инструмент: application и network interceptors имеют разную семантику, порядок важен, а `proceed()` должен использоваться корректно. Senior-дисциплина — разделять transport errors, protocol errors, domain errors и UI errors, не превращая repository в смесь логгера, retry-машины и parser-а. citeturn5search0turn5search3turn5search1turn5search4turn5search9turn5search5

#### Вопрос

**Когда API метод должен возвращать `T`, а когда `Response<T>`?**

**Junior answer.**  
Если нужен только body, можно вернуть `T`. Если нужно читать код ответа и заголовки, лучше `Response<T>`. citeturn5search3

**Middle answer.**  
Для большинства happy-path endpoint-ов `T` даёт чище API и автоматически переводит non-2xx в `HttpException`. `Response<T>` нужен там, где transport metadata — часть бизнес-решения: например, conditional requests, pagination headers, partial success semantics или необходимость различать empty body и отсутствие ответа. citeturn5search3

**Senior answer.**  
Senior выбирает тип возврата на границе data layer как часть контракта, а не на вкус. Если наружу постоянно течёт `Response<T>`, вы поднимаете transport concerns слишком высоко; если всегда скрываете его, можно потерять важную протокольную семантику. Правильный выбор зависит от того, должен ли вышестоящий слой принимать решение на основе HTTP-метаданных или нет. citeturn5search3turn5search9

**Why this question matters.**  
Вопрос быстро вскрывает, понимает ли инженер, что networking API — это boundary design, а не просто способ достать JSON. От этого зависит чистота repository contracts и то, насколько протокол протекает в UI/domain. citeturn5search3

#### Вопрос

**Чем application interceptor отличается от network interceptor и где часто ошибаются?**

**Junior answer.**  
Они работают на разных уровнях запроса. Application interceptor обычно выше, network interceptor ближе к сети. citeturn5search1turn5search8

**Middle answer.**  
Application interceptor видит полный span вызова и удобен для cross-cutting concerns вроде auth/header decoration или общих policy-решений. Network interceptor работает ближе к конкретному сетевому обмену и требует большей аккуратности; документация подчёркивает, что `proceed()` надо вызывать корректно, а порядок interceptors влияет на итоговое поведение. citeturn5search1turn5search8

**Senior answer.**  
Senior смотрит на интерсептор как на транспортный middleware, а не на место “куда бы засунуть побольше логики”. Если retry, metrics, auth-refresh, caching override и request rewriting смешаны без явной policy order, вы теряете наблюдаемость и предсказуемость. Особенно опасно превращать network layer в implicit state machine, которую уже нельзя нормально тестировать или reason about при ошибках. citeturn5search1turn5search9

**Why this question matters.**  
На боевых приложениях сетевой стек почти всегда со временем обрастает cross-cutting логикой. Этот вопрос показывает, умеет ли человек держать transport pipeline чистым и управляемым, а не только добавлять interceptors по мере появления новой задачи. citeturn5search1turn5search4

## Navigation, lifecycle и resilience

### Custom Fragment-based navigation и back stack

**Theory block.**  
Если вы сознательно живёте с custom Fragment-based navigation в 2026, senior-уровень здесь определяется не знанием `FragmentTransaction`, а способностью владеть **back stack как данными системы**. `FragmentManager` управляет стеком транзакций и позволяет сохранять/восстанавливать состояние fragment-based navigation; `FragmentResult` доставляется только когда слушающий fragment снова становится `STARTED`, что удобно для возврата результатов через pop. При hosting Compose в Fragment официальные interop-доки требуют аккуратной integration strategy: `ComposeView` во Fragment должен использовать корректную `ViewCompositionStrategy`, обычно `DisposeOnViewTreeLifecycleDestroyed`. Если на одном layout несколько `ComposeView`, им нужны уникальные id для корректной работы `savedInstanceState`. Для back-навигации Android рекомендует `OnBackPressedDispatcher`, а predictive back требует корректной интеграции с современными AndroidX API. При этом в 2026 уже существует стабильный Navigation 3, построенный вокруг Compose state и собственного back stack, так что custom navigation нужно уметь защищать аргументами: модульные границы, control over lifetime, интеграция с существующим Fragment stack, собственные transition/result contracts. Если таких аргументов нет — возможно, вы просто платите infra-tax без выгоды. citeturn4search1turn4search21turn4search9turn12view2turn12view1turn4search3turn4search11turn4search22

#### Вопрос

**Какие типичные ошибки допускают при hosting Compose screen внутри Fragment?**

**Junior answer.**  
Надо использовать `ComposeView` и правильно вызывать `setContent()`. Ещё важно не забыть про lifecycle. citeturn12view2

**Middle answer.**  
Частые ошибки: неверная disposal strategy для Composition во Fragment, забытый `DisposeOnViewTreeLifecycleDestroyed`, а также отсутствие уникальных id у нескольких `ComposeView`, из-за чего ломается state restoration. Ещё легко забыть, что binding во Fragment живёт только между `onCreateView()` и `onDestroyView()`. citeturn12view2turn12view0

**Senior answer.**  
Senior видит здесь пересечение двух lifecycle world-ов: Fragment view lifecycle и Composition lifecycle. Если screen state или side-effects привязаны не к тому owner-у, можно получить либо преждевременную disposal, либо удержание ресурсов после уничтожения view, либо расхождение между back stack entry и тем, что по факту ещё живёт в памяти. citeturn12view1turn12view2turn10search1

**Why this question matters.**  
Это практический вопрос на реальные hybrid-приложения. Он хорошо показывает, работал ли человек с живой interop-схемой, а не только с Compose-only demo apps. citeturn12view2turn4search20

#### Вопрос

**Когда custom navigation действительно оправдана по сравнению с Navigation Compose или Navigation 3?**

**Junior answer.**  
Когда уже есть свой стек или нужны нестандартные сценарии. В остальных случаях стандартная библиотека часто проще. citeturn4search22turn4search2

**Middle answer.**  
Custom stack оправдан, если у вас уже есть Fragment-based host architecture, feature isolation, собственные result/transition contracts или требования к back stack, которые стандартная библиотека не покрывает без сильных компромиссов. Но это должно окупаться, потому что Navigation 3 уже даёт state-driven back stack для Compose. citeturn4search22turn8search11turn4search1

**Senior answer.**  
Senior оценивает не только функциональность, но и platform fit: кто будет поддерживать deep links, predictive back, saved navigation state, modular boundaries и инструменты тестирования через год-два. Если custom nav не даёт явного стратегического преимущества в ваших lifetime/boundary contracts, она становится дорогой внутренней платформой, которую команда фактически пишет и поддерживает вместо продукта. citeturn4search11turn4search22turn3search13

**Why this question matters.**  
Вопрос проверяет architectural judgment. Отличие senior здесь в том, умеет ли он обосновать инфраструктурную сложность через долгосрочную выгоду, а не через привычку команды. citeturn4search22turn3search1

### Process lifecycle, WorkManager и process death

**Theory block.**  
Сильный Android-инженер всегда помнит: process не является надёжным контейнером состояния. Android docs прямо описывают, что процесс приложения может быть уничтожен системой в фоне, а `ViewModel` переживает configuration change, но не system-initiated process death. Поэтому senior-стратегия восстановления строится так: сохранить **минимум** transient UI state (`SavedStateHandle`, `rememberSaveable`, в 2026 — и `rememberSerializable`/serialization support там, где уместно), а screen state целиком повторно произвести из data layer. `SavedStateHandle` хорош для query/filter/id-ключей и прочих small UI elements, достаточных для восстановления user journey; но сложный screen state Android docs не рекомендуют складировать туда. Для гарантированной фоновой работы нужен WorkManager: он хранит работу во внутренней SQLite БД, переживает перезагрузки устройства, поддерживает unique work, constraints и expedited work для важных задач. `ProcessLifecycleOwner` полезен как coarse-grained сигнал foreground/background на процесс, но не заменяет screen lifecycle. Senior-уровень здесь — перестать надеяться на “память процесса” как на backend и строить экран так, будто его всегда могут уничтожить и поднять заново. citeturn8search5turn8search9turn2search2turn10search15turn8search18turn8search4turn8search0turn8search2turn8search17

#### Вопрос

**Что именно вы сохраняете в `SavedStateHandle`, а что сознательно не сохраняете?**

**Junior answer.**  
Сохраняю небольшие значения вроде query, selected tab или id. Большие данные лучше не хранить там. citeturn2search2turn10search15

**Middle answer.**  
В `SavedStateHandle` стоит сохранять минимальный набор, который нужен, чтобы повторно восстановить экран после process death. Полный screen state, списки сущностей и тяжёлые модели лучше снова произвести из repository/Room/DataStore, иначе handle превращается в хрупкий кэш. citeturn2search2turn8search9turn10search15

**Senior answer.**  
Senior отвечает через reconstruction boundary: в `SavedStateHandle` идут ключи восстановления, а не производные данные. Если нельзя чётко объяснить, почему это значение не может быть снова вычислено из data layer, его, скорее всего, и не нужно сохранять в handle. citeturn2search18turn10search15turn8search18

**Why this question matters.**  
Это вопрос на трезвое мышление про lifespans. Он очень быстро выявляет, кто реально проектирует под process death, а кто до сих пор неявно предполагает “приложение просто висит в памяти”. citeturn8search5turn8search9

#### Вопрос

**Когда нужен WorkManager, а когда его использование — оверкилл?**

**Junior answer.**  
WorkManager нужен для надёжной фоновой работы, которая должна выполниться даже позже. Для простой работы внутри открытого экрана он может быть лишним. citeturn8search4turn8search8

**Middle answer.**  
Если задача должна переживать выход из приложения, ограничения системы и даже reboot, WorkManager — правильный выбор. Если операция напрямую привязана к живому экрану и должна отмениться вместе с ним, её естественное место в screen scope, а не в persistent scheduler. citeturn8search4turn8search8

**Senior answer.**  
Senior также смотрит на идемпотентность и ownership: WorkManager хорош для sync/upload/reconcile задач с durable intent и понятной retry policy. Он плох как замена нормальной orchestration-модели UI, когда разработчик просто не хочет разбираться в scopes и жизненном цикле и потому “запускает всё через воркер”. citeturn8search12turn8search4turn8search0

**Why this question matters.**  
Вопрос проверяет, понимает ли инженер различие между screen-bound concurrency и durable background execution. На production это влияет и на UX, и на battery/use-of-resources, и на корректность повторных запусков. citeturn8search4turn8search0

## Testing и build engineering

### Testing strategy

**Theory block.**  
Senior Android testing в 2026 — это не “покрыть проценты”, а построить дешёвую и устойчивую сеть обратной связи. Базовый слой — pure tests reducers/use cases/transformers; выше — actor/repository tests с fake dependencies; ещё выше — Compose UI tests на пользовательские сценарии; вокруг этого — узкий слой integration tests, где проверяются boundary contracts. Для coroutine-кода Android docs рекомендуют `runTest`; он даёт `TestScope`, виртуальное время и контролируемое выполнение child coroutines. Для Flow-тестов важно различать, является ли поток входом или выходом системы под тестом: тестируются либо fake producers, либо сбор эмиссий subject under test. `backgroundScope` полезен для долговременных collectors, которые должны автоматически отмениться в конце теста. В Compose testing в 2026 существенен переход на `runComposeUiTest`: он сам выполняет тест внутри `runTest` и синхронизирует test clock с Compose окружением, что уменьшает ручное управление scheduler-ом. В hybrid UI Compose и Views можно тестировать одновременно: Espresso для View и ComposeTestRule для composables. JUnit Jupiter ценен extension model-ю и гибкостью, а MockK — удобной поддержкой Kotlin/coroutines, но senior не подменяет дизайн тестируемости магией моков. citeturn6search0turn6search3turn6search4turn6search11turn4search20turn6search1turn6search2turn6search13turn6search16

#### Вопрос

**Почему coroutine-тесты остаются flaky даже с `runTest`?**

**Junior answer.**  
Потому что не всё управление временем и dispatchers настроено правильно. Иногда корутины стартуют вне test scope. citeturn6search0turn6search3

**Middle answer.**  
Типичные причины — hardcoded dispatchers, фоновые collectors вне `TestScope`, race между запуском и ассертами, а также код, который живёт дольше теста. `runTest` помогает, но не исправляет архитектуру автоматически. citeturn6search0turn6search16turn6search8

**Senior answer.**  
Senior отвечает системно: flaky-тест обычно сигнализирует не о “плохом runTest”, а о том, что execution ownership в production-коде неявен. Если объект под тестом сам создаёт scope/dispatcher, держит скрытый background job или смешивает горячие стримы без явного lifecycle, тест просто честно показывает архитектурную неуправляемость. citeturn6search0turn6search16turn0search12

**Why this question matters.**  
Вопрос проверяет, видит ли человек тест как диагностику дизайна, а не как обряд с `advanceUntilIdle()`. Это критично для команд, где flaky suite медленно, но верно убивает доверие к CI. citeturn6search0turn6search13

#### Вопрос

**Как вы тестируете `StateFlow`/`SharedFlow` output экрана или feature?**

**Junior answer.**  
Подписываюсь на поток и проверяю эмиссии. Для hot stream важно успеть начать collection до события. citeturn6search4

**Middle answer.**  
Для `StateFlow` обычно проверяется стартовое состояние и последовательность переходов после intents. Для `SharedFlow`/events нужен долговременный collector в test scope, иначе transient emission можно пропустить; Android-гайд по Flow-test явно разделяет подходы к flow-as-input и flow-as-output. citeturn6search4turn6search13

**Senior answer.**  
Senior тестирует не “что что-то эмитнулось”, а контракт state machine: какие intents порождают какие states и какие one-off events при каком состоянии мира. Хороший тестовый дизайн ещё и минимизирует knowledge о внутренних операторах, чтобы refactor pipeline не ломал тесты, пока сохраняется внешний реактивный контракт. citeturn6search4turn14view0turn0search11

**Why this question matters.**  
Это вопрос на умение тестировать реактивную систему по её observable contract. Он отлично отделяет людей, которые проверяют случайные implementation details, от людей, которые защищают инварианты экрана. citeturn6search4turn0search3

#### Вопрос

**Что должна проверять Compose UI test, чтобы оставаться полезной и не стать хрупкой?**

**Junior answer.**  
Нужно проверять пользовательский сценарий: видимость элементов, взаимодействия, текст, переходы состояния. Не стоит слишком завязываться на внутреннюю реализацию. citeturn6search11turn4search20

**Middle answer.**  
Хороший Compose UI test опирается на semantics и observable user outcome, а не на внутреннюю структуру composable tree. В hybrid-экранах нужно естественно сочетать Compose и Espresso, а не пытаться одной системой тестировать всё подряд не по месту. citeturn4search20turn6search11

**Senior answer.**  
Senior обычно держит UI tests узкими и ценными: smoke на critical journeys, а сложную state logic тестирует ниже. Идея в том, чтобы UI test подтверждал интеграцию rendering + input + async synchronization, а не дублировал десятки reducer/unit tests в более медленной и дорогой форме. citeturn6search11turn6search0

**Why this question matters.**  
В production UI tests либо становятся активом, либо превращаются в дорогой шум. По ответу видно, умеет ли человек строить тестовую пирамиду, где каждый слой делает свою работу. citeturn6search1turn6search11

### Build, modularization и performance

**Theory block.**  
Сильный senior Android developer рассматривает build и performance как часть feature-delivery, а не как постфактум-оптимизацию. Version Catalogs централизуют dependency management и хорошо сочетаются с multi-module структурой. Но modularization, по Android docs, не имеет единственного правильного шаблона: модульные границы должны соответствовать ownership, сборочным скоростям, API/implementation surface и способности команды эволюционировать систему. Для навигации и feature boundaries в 2026 Android отдельно обсуждает разделение на `api` и `impl` submodules. R8 — это не только shrinking, но и оптимизация/обфускация; Android docs отдельно предупреждают не превращать релиз в debug-сборку глобальными `-dontshrink`, `-dontobfuscate` и `-dontoptimize`. Baseline Profiles дают ощутимое ускорение кода уже с первого запуска, а Startup Profiles дополнительно оптимизируют DEX layout и ускоряют старт ещё сильнее. Но порядок мышления важен: перформанс надо измерять; Compose stability fixes и strong skipping применяются после диагностики, а не как ритуал. Senior-уровень тут — связывать архитектурные границы, build cost, cold start и runtime responsiveness в одну систему решений. citeturn3search3turn3search19turn3search1turn3search5turn3search9turn3search2turn3search14turn3search18turn3search0turn3search4turn3search8turn3search20turn10search2turn10search9

#### Вопрос

**Как выбирать module boundaries, чтобы они были полезны, а не декоративны?**

**Junior answer.**  
Нужно делить проект на логические части, например по feature-ам или слоям. Так проще поддерживать код. citeturn3search1turn3search5

**Middle answer.**  
Полезная граница уменьшает compilation impact, делает API surface явным и закрепляет ownership. Если модуль существует, но всё равно тянет половину проекта через `api` или требует повсеместных friend-like знаний, то модульность декоративна. citeturn3search1turn3search5turn3search9

**Senior answer.**  
Senior проектирует модули вокруг change-rate и команды, а не только вокруг папок. Хорошая модульность облегчает parallel development, снижает blast radius изменений и даёт контракты, которые можно тестировать и оптимизировать независимо; плохая — увеличивает Gradle overhead и DI/navigation complexity без реального снижения связанности. citeturn3search1turn3search5turn3search9

**Why this question matters.**  
Этот вопрос показывает, понимает ли инженер modularization как инструмент управления сложностью, а не как визуальную организацию репозитория. На scale это напрямую влияет на скорость команды и стоимость изменений. citeturn3search1turn3search5

#### Вопрос

**Куда инвестировать первым делом: Baseline Profiles, Startup Profiles, R8 tuning или Compose stability work?**

**Junior answer.**  
Сначала надо померить проблему, а потом оптимизировать. Часто Baseline Profiles и R8 дают заметный эффект. citeturn3search0turn3search2turn10search9

**Middle answer.**  
Если страдает cold start и critical journeys, Baseline Profiles часто дают быстрый и измеримый выигрыш; Startup Profiles усиливают startup-часть ещё сильнее. Compose stability work нужен, когда трассировка показывает лишнюю recomposition/phase work, а R8 tuning — когда видно, что размер и optimizer policy реально влияют на запуск и runtime. citeturn3search0turn3search20turn10search17turn3search2

**Senior answer.**  
Senior отвечает не списком фич, а последовательностью: instrumentation → hypothesis → smallest high-leverage change → re-measure. Baseline/Startup Profiles и R8 улучшают системные характеристики приложения в целом, тогда как stability fixes часто более локальны; но если hotspot — конкретный Compose screen под скроллом, глобальные build-оптимизации не заменят точечной работы с invalidation model. citeturn3search24turn3search0turn3search20turn10search9turn10search18

**Why this question matters.**  
Вопрос проверяет инженерное мышление о performance как о measured system, а не о наборе модных практик. Senior почти всегда отличается тем, что умеет расставлять оптимизации по ROI, а не по хайпу. citeturn3search24turn10search17

## Практические задачи

### Task для coroutines и MVI orchestration

Реализуйте `Actor`, который по intent `Refresh(force: Boolean)` параллельно запускает два запроса: `loadProfile()` и `loadPermissions()`. Если `profile` упал, экран должен перейти в `Error`; если `permissions` упали, экран должен показать `News.PermissionWarning`, но остаться в `Content`.  
**Что проверяет.** Structured concurrency, выбор между `coroutineScope` и `supervisorScope`, границы `Actor`/`Reducer`/`NewsPublisher`, fault model экрана. citeturn0search0turn14view0turn15search0

### Task для Flow и hot stream semantics

Дан repository, который предоставляет cold `Flow<List<Item>>`. Превратите его в screen-level `StateFlow<ItemsState>` так, чтобы expensive upstream не перезапускался на каждый новый collector, но корректно останавливался после ухода экрана. Объясните выбор `stateIn` и `SharingStarted`.  
**Что проверяет.** Cold vs hot, `stateIn`, ownership of scope, replay semantics, восстановление подписки после возврата на экран. citeturn7search6turn7search10turn0search13

### Task для Compose performance

Есть `LazyColumn` со списком из 5 000 элементов. Внутри каждого item есть локальный remembered state, а сортировка списка меняется по нажатию кнопки. Исправьте код так, чтобы при reorder remembered state не “переезжал”, а recomposition footprint был минимальным.  
**Что проверяет.** Stable keys, state locality, invalidation regions, базовое профилирование горячего списка. citeturn10search16turn10search18turn10search11

### Task для Fragment-hosted Compose migration

Напишите `Fragment`, который хостит целиком Compose screen через `ComposeView`, корректно освобождает Composition вместе с `viewLifecycleOwner` и переживает `savedInstanceState`. Дополнительно покажите, как безопасно вернуть результат назад в предыдущий Fragment.  
**Что проверяет.** Compose/View interop, `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`, back stack semantics, fragment result delivery. citeturn12view2turn12view0turn4search9

### Task для persistence layer

Смоделируйте экран фильтрации каталога, где: выбранные toggles и sort order должны переживать process death, а список товаров должен пересобираться из локального source of truth. Разделите, что хранится в Room, что в DataStore, а что в `SavedStateHandle`.  
**Что проверяет.** Lifespans данных, source of truth, reconstruction after process death, практическая граница Room/DataStore/SavedStateHandle. citeturn2search5turn2search10turn2search2turn8search9

### Task для testing strategy

Напишите тест для feature/ViewModel, который при старте собирает repository flow, а по событию `Retry` переходит `Loading -> Content` или `Loading -> Error`. Тест должен быть детерминированным, использовать `runTest`, не течь корутинами после завершения и отдельно проверять transient event.  
**Что проверяет.** `runTest`, `TestScope`, continuous collection, тестирование `StateFlow` и one-off event contracts, дизайн тестов без лишней зависимости от implementation details. citeturn6search0turn6search13turn6search4turn14view0

## Как использовать эту базу для self-calibration

Если вы отвечаете на вопросы в стиле Junior/Middle, это не значит, что у вас “плохая база”; чаще это означает, что вам не хватает опыта работы с **контрактами системы под нагрузкой времени**: process death, hot streams, replay semantics, scope ownership, DI graph boundaries, migration cost, performance regression и тестовой детерминированности. Сильный senior в Android 2026 почти всегда мыслит через lifetime, ownership, replay, observability и cost of change. Именно эти оси объединяют seemingly разные темы — coroutines, Compose, DI, storage, navigation, testing и build engineering — в одну инженерную систему. citeturn8search5turn0search23turn3search1turn3search24turn6search0

Практически это означает следующее. Если вы хотите расти именно к senior-калибру под указанный стек, полезно перепроверять любой свой design choice одним и тем же набором вопросов: кто владеет жизненным циклом; что произойдёт при повторной подписке; что случится после process death; какой здесь durable state, а какой transient event; как это тестируется детерминированно; как это деградирует по производительности; какова цена миграции и поддержки через полгода. Если на эти вопросы есть чёткие ответы — архитектура обычно здорова. Если нет, проблема чаще всего не в API, а в неявных границах ответственности. citeturn0search19turn2search18turn14view0turn10search9turn6search4