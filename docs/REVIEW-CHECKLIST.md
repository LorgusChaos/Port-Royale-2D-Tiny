# Чек-лист проработки ревью

Состояние работ по находкам из [REVIEW.md](REVIEW.md). Здесь только «что сделано и что
дальше». Что именно не так, как чинить и какие тесты нужны — в REVIEW.md, в разделе находки
с тем же номером.

## Правила работы

1. **Читай экономно.** Этот файл перед работой прочитай целиком. REVIEW.md целиком не читай,
   он большой: раздел находки ищи по заголовку `### N.`, для пачки 2 — раздел «Условия
   выноса».
2. **Номера строк в REVIEW.md верны на 2026-11-13** и съезжают после первых же правок. Ищи
   код по именам функций и перед правкой убедись, что он всё ещё такой, как описано.
3. **Находки подтверждены чтением кода, в игре ни одна не воспроизводилась.** Падающий тест —
   первое настоящее подтверждение. Там, где у находки тест помечен «сейчас», сначала пиши
   тест и убедись, что он падает, потом исправляй. Если тест, написанный по описанию, не
   падает или код уже не такой, как описано, — возможно, ошиблась находка. Остановись,
   запиши это в «Решения и отклонения» и скажи автору; тест и код под описание не подгоняй.
4. **Порядок — по пачкам сверху вниз.** За один заход одна пачка, в пачке 2 — один шаг.
   Закончив, остановись и отчитайся: у автора лимит на пять часов, и заход, взявший больше,
   обрывается на середине.
5. **Отмечай пункт сразу, как он закрыт,** а не в конце захода. Оборванный заход с
   неотмеченными пунктами означает, что их придётся перепроверять заново.
6. **Пункт закрыт, когда** исправление сделано так, как описано в «Исправление» (или
   отклонение записано ниже), добавлены тесты из «Тесты» и `:core:test` зелёный. К отметке
   допиши дату и имена добавленных тестов.
7. **Статусы:** `[ ]` — не начато; `[~]` — сделано и покрыто, ждёт проверки автором в
   запущенной игре; `[x]` — закрыто. `[~]` превращается в `[x]` только после слов автора:
   чистое завершение процесса подтверждением не считается (CLAUDE.md, «Testing discipline»).
   К пункту `[~]` допиши шаги проверки.
8. **Чини только то, что описано в находке.** Заметил рядом другое — запиши в «Новые
   наблюдения» и не трогай. Иначе правки расползаются, и по списку их уже не проверить.
9. **Решения из раздела «Решения за автором» сам не принимай.** Спроси перед началом пункта,
   ответ запиши рядом с вопросом.
10. **Неподтверждённое сначала подтверди** — тестом или чтением кода. Подтвердилось: заведи в
    REVIEW.md находку со следующим номером, с разделами «Исправление» и «Тесты», и добавь
    строку в подходящую пачку. Не подтвердилось: отметь и напиши почему.
11. **Коммиты только локальные** (решение автора 2026-11-14). Вся работа по чек-листу идёт в
    локальной ветке `review/checklist`. Коммит — после каждой пачки, в пачке 2 — после
    каждого шага; его хэш дописать к пункту в этом файле — это точка отката. Сообщение коммита
    называет пачку или шаг и номера находок. Ничего не пушить и не сливать в `main` до
    последнего шага («Завершение» в конце файла). Документы ревью (`docs/REVIEW.md`, этот
    файл, `Project design/decisions/`) в локальные коммиты не входят: откат кода не должен
    откатывать журнал, а их судьба решается в находке 28.

## Решения за автором

- [x] **Находка 2.** Подтвердить правило: противник приходит в бой с игроком с тем корпусом,
  что остался, и уносит полученный урон после бегства игрока. Сейчас так устроены только бои
  капитанов и бои ИИ между собой. Решено 2026-11-14 по рекомендации: правило подтверждено,
  сделано в пачке 4.
- [x] **Находка 3.** Груз не влезает в покупаемый корабль: отказ с объяснением (рекомендация
  ревью) или покупка без пересадки. Решено 2026-11-14 по рекомендации (слово автора «чини всё по
  необходимости»): отказ с объяснением, как в доке.
- [x] **Находка 12.** Писать ли в сейв корабли ИИ, рейды и морские события. Рекомендация: нет,
  и записать это в коде как намеренное. Пассажира сохранять в любом случае. Решено 2026-11-14
  по рекомендации (слово автора «чини всё по необходимости»): не писать, записано в KDoc
  `PortRoyaleGame.newWorld`; пассажир — в сейв, пачка 5. **Пересмотрено автором 2026-11-14
  после отчёта:** «сейв должен быть полноценный и игра продолжаться полностью с тех же событий,
  на которых она была сохранена» — корабли ИИ, рейды и морские события в сейв пишутся. Отдельная
  работа «Полный сейв» (лист решений — до кода), см. «Решения и отклонения».
- [x] **Находка 14.** Запретить автосохранение в бою; закрывать ли так же ручное сохранение;
  применять ли поражение в момент, когда корпус дошёл до нуля. Решено 2026-11-14 по
  рекомендации: автосейв в бою — нет; ручное сохранение в бою — закрыто так же; поражение в
  момент нуля — не нужно, раз ни одно сохранение не попадает в окно поражения.
- [x] **Находка 22.** Подтверждение перед отзывом капитана с грузом. Решено 2026-11-14 по
  рекомендации: да — первое нажатие «Recall» при грузе на борту называет, что и на какую сумму
  будет списано, второе отзывает; без груза — как раньше, одним нажатием.
- [x] **Находка 28.** Что из неотслеживаемого коммитить, что отправить в `.gitignore`, что
  удалить. Сюда же судьба `docs/REVIEW.md` и этого файла. Решено 2026-11-14 по рекомендации:
  исходники того, что поставляется (панель HUD), — коммит; черновики и сравнения — в
  `.gitignore`; остатки отвергнутой локальной генерации — тоже в `.gitignore`, **не удалены**:
  удаление неотслеживаемых файлов не отменить, это за автором. `docs/REVIEW.md`, этот файл и
  `Project design/decisions/` — коммитить на шаге «Завершение»: комментарии в коде теперь
  ссылаются на «REVIEW.md, finding N», и без файла в репозитории эти ссылки ведут в никуда.
- [x] **Пачка 2.** Лист решений по выносу мира (`/veha`); предложения ревью — в REVIEW.md,
  «Условия выноса», пункт 5. Ответ 2026-11-14: пункты 1–26 без правок; темп — шаги идут на
  зелёных тестах и `--shot`, автор проверяет один раз в конце пачки; локальный коммит после
  каждого шага (правило 11). Лист: `Project design/decisions/review-batch-2.md`.

## Пачка 1. Срочное и изолированное

- [x] **1** Игровые часы останавливаются на 1x после 46-го игрового дня — высокая — тест
  сейчас, `GameClockTest`. Закрыто 2026-11-14: оба теста сначала упали на нынешнем коде (+0.0
  вместо +10), после перехода на `Double` зелёные. Тесты: `the clock keeps running at normal
  speed past day forty-six`, `the clock keeps running at normal speed two years into a game`.
  Коммит `d2a0990`
- [x] **11** Esc при открытом окне капитана замораживает игру до перезапуска — высокая —
  проверка вручную. Сделано 2026-11-14 (`handleEscape`, `resumeGame`, `beginPlaying`), тестов
  нет — ввод и сцены. Проверить:
  1. в море клик по кораблю своего капитана → окно открылось → Esc: окно закрылось, меню паузы
     не появилось, мир идёт, корабль слушается кликов;
  2. то же с окном имущества (кнопка имущества в HUD) → Esc;
  3. окно капитана открыто → Esc закрывает окно → второй Esc открывает паузу → «Продолжить»:
     игра идёт, окна нет;
  4. окно открыто → кнопка «назад» в нём по-прежнему закрывает его.
  Коммит `d2a0990`.
- [x] **16** Имя сохранения попадает в путь файла без очистки — высокая — тест сейчас,
  `SaveManagerTest`; поведение с запрещёнными символами проверить и вручную. Сделано
  2026-11-14: `SaveManager.checkSaveName`, `SaveGameUI` показывает причину отказа и пишет
  «Saved» только после записи, автосейв при ошибке пишет в `GameLog` (категория `SAVE`).
  6 тестов из 10 сначала упали на заглушке с нынешним поведением экрана, после исправления
  зелёные. Тесты: `an ordinary save name is kept exactly as typed`, `a cyrillic save name is
  kept exactly as typed`, `a save name cannot climb out of the saves folder`, `a save name
  cannot reach into a subfolder`, `characters Windows forbids in file names are dropped`,
  `names Windows reserves for devices are rejected in any case`, `a name that only looks like
  a device name is fine`, `a blank save name is rejected`, `a manual save cannot take the
  autosave's name`, `a save name never ends in a dot or a space`. Проверить: Esc → Save, по
  очереди ввести и нажать Save:
  1. `12:30` → «Saved "1230".», в списке появилось `1230`;
  2. `../settings` → «Saved "settings".», сохранение в списке, настройки игры не сбились;
  3. `what?` и `a/b` → «Saved "what".» и «Saved "ab".»;
  4. `CON` → отказ «reserved by Windows», игра не упала;
  5. `autosave` (и клик по `autosave` в списке → Save) → отказ «kept for the autosave»;
  6. `???` → отказ «Use letters, digits, spaces, _ or -.»; пустое поле → «Enter a name first.»;
  7. проверочные сохранения потом удалить из `~/PortRoyaleTiny/saves/`.
  Коммит `d2a0990`.
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 2. Вынос мира из `PortRoyaleGame`

Перенос механический: код переезжает как есть, вместе с известными ошибками. Исправлений,
переименований и слияния дублей в этой пачке нет. Условия целиком — REVIEW.md, «Условия
выноса». Как именно — согласованный лист решений `Project design/decisions/review-batch-2.md`
(прочитать перед каждым шагом). Порядок шагов изменён листом (п. 4), см. «Решения и
отклонения».

Шаг закрыт как `[~]`, когда: перенос сделан, тесты блока зелёные, оба модуля собираются,
`--shot=sea` без исключений, локальный коммит шага сделан и его хэш записан к пункту
(правило 11). Проверки автором по шагам нет — одна в конце пачки по сценариям листа (п. 25),
после неё `[~]` → `[x]`.

- [x] Лист решений написан и согласован автором — 2026-11-14
- [x] Шаг 1. `GameWorld`, всё состояние мира с переходниками в игре, морские события.
  Сделано 2026-11-14: `world/GameWorld.kt` (21 поле мира, морские события, `isNearAnyPort`,
  `deliverPassenger`), `world/WorldEvents.kt` (`WorldEvents.toast` + ключ `ToastIcon`),
  `world/ResolvedPort.kt` (`ResolvedPort`, `resolvePorts`); в игре — переходники, картинку по
  ключу даёт `IconFactory.iconFor(ToastIcon)`. Тестов 495, все зелёные; оба модуля собираются;
  `--shot=sea` без исключений, снимок в порядке. Тесты `GameWorldSeaEventsTest`: `sailing into
  a sea event raises it once and frees the slot`, `a sea event out of reach stays where it is`,
  `a chest of gold pays into the purse`, `a chest of goods with room in the hold adds one good
  and shows its icon`, `a chest of goods with the hold full pays loose coin instead`, `a rescued
  castaway asks to be taken to one of the ports`, `delivering the castaway pays them off and
  frees the berth`, `the castaway stays aboard through every other port`, `sea events appear on
  open water and never in a harbour`, `no second castaway is offered while one is aboard`.
  Сторож `GameWorldImportsTest`: `the world imports nothing that needs a window, a sound device
  or the game class`, `the world does not reach those classes by their full names either`.
  Проверка автором — сценарий (1) листа, в конце пачки. Коммит `ada925f`.
- [x] Шаг 2. Общие помощники кораблей ИИ: появление и замена, цель и маршрут с `routeBudget`,
  `isHostile`/`shipTier`, `fleetShipTransform`. Сделано 2026-11-14: в `GameWorld` переехали
  `spawnAiShips` (с `scatterAlongRoutes`, `anchoredAt`), `respawnReplacement`, `pirateOnStation`,
  `pirateLairAnchor`, `pirateBerth`, `retargetAiShip` (с `weightedPort`, `prowlNearGround`,
  `prowlPointOutside`, `pickCruisingGround`, `raidDestinationFor`, `prowlPointInside`),
  `routeTo`, `routeAvoiding`, `breakOffChase`, `isHostile`, `shipTier`, `fleetShipTransform`
  (с `fleetPath`, `pointAlongPath`, `portApproachPos`, `pathBetweenPorts`); в игре — переходники
  для того, что ещё зовёт непереехавший код; `create()` зовёт `world.spawnAiShips()`. Восемь
  констант, которые читают и игра, и мир, стали публичными в пакете `world`. Тестов 505, все
  зелёные; оба модуля собираются; `--shot=sea` без исключений, снимок в порядке. Тесты
  `GameWorldAiHelpersTest` (`GameRandom.withSeed`, настоящая карта): `a new world sails each
  flag's traders and warships out of that flag's own harbours`, `a new world's pirates start out
  on station and every ship starts on open water`, `a trader is given a course over water to
  another harbour`, `a warship sails out to a harbour and then home to its anchor`, `a ship in a
  chase is routed after its quarry`, `with the allowance for courses spent a ship keeps the
  course it has`, `a sunk trader or warship is replaced by one of the same flag out of its own
  harbours`, `a sunk pirate is replaced by a pirate already out on station`, `a pirate picks
  only fights it can win and a trader picks none`, `a navy turns on the player as the player's
  standing with its flag sinks`. Проверка автором — сценарий (2) листа, в конце пачки. Коммит
  `e8095d8`.
- [x] **29** Корабли ИИ начинают игру в гаванях: разброс по маршрутам не срабатывает — низкая —
  тест сейчас, `GameWorldAiHelpersTest`. Замечено на шаге 2, исправлено 2026-11-14 по слову
  автора вне порядка пачек (см. «Решения и отклонения»): `spawnAiShips` снимает покадровый
  предел маршрутов на время `scatterAlongRoutes` и возвращает прежний. Тест `a new world opens
  with its ships already under way rather than in harbour` сначала упал на нынешнем коде
  («Spain Trader starts with no route»), после исправления зелёный; всего 506, все зелёные;
  `--shot=sea` без исключений. Проверить: новая игра → в первые секунды торговцы и военные уже
  идут по морю в разных точках своих маршрутов, а не стоят у гаваней и не отчаливают разом;
  пираты, как и раньше, на своих водах; загрузка новой игры заметно не дольше прежней.
  Коммит `f9a803f`.
- [x] Шаг 3. Рейды и `sinkShip`. Сделано 2026-11-14: в `GameWorld` переехали `updateRaids`,
  `beginRaid`, `announceRaid`, `keepFormation`, `keepLineAhead`, `recordWake`,
  `sendNavalResponse`, `sendSquadron`, `settleEscalation`, `endRaid`, `sinkShip` и константы,
  которые читает только рейд; три тоста рейда идут через `WorldEvents`, у `ToastIcon` два новых
  ключа — `Debt` и `Property` (картинки те же, что и были). В игре — переходники `updateRaids` и
  `sinkShip`; переходник `navalEscalation` удалён, читателей не осталось. Константы, общие с
  `resolveEncounter` (`RAID_WARSHIP_*`, `RAID_PIRATE_CAUTION`, `RAID_ATTACK_COOLDOWN_*`),
  остались в игре. Тестов 514, все зелёные; оба модуля собираются; `--shot=sea` без
  исключений, снимок в порядке. Тесты `GameWorldRaidsTest` (`GameRandom.withSeed`, настоящая
  карта): `a raid forms once the quiet spell runs out and says nothing while it gathers`, `the
  band sets out only when every member has reached the rally point`, `followers sail in line
  astern along the water the leader has already crossed`, `the raid is announced only when the
  band reaches the corridor`, `after the response delay the two ports' flags send their nearest
  warships and say so`, `when the raid's time is up the pirates go home and the all-clear is
  given`, `a ship sunk inside a raid leaves a mark on the water, leaves the world and is
  replaced`, `a ship sunk with no raid running leaves no mark and is still replaced`. Проверка
  автором — сценарий (3) листа, в конце пачки. Коммит `e73f5e6`.
- [x] Шаг 4. Обновление кораблей ИИ, сторож, встречи с игроком. Сделано 2026-11-14: в
  `GameWorld` переехали `updateAiShips` со всем, что он зовёт (`updatePursuit`, `updateFlight`,
  `refugeFrom`, `updateEncounters`, `pressChase`, `beginChase`, `updateVoyage`, `worthChasing`,
  `pickQuarry`, `resolveContacts`, `resolveEncounter`, `threatTo`, `avoidThreatIfNeeded`,
  `abandonPursuit`, `separateAiShips`, `isDocking`, `steer`, `nudgeIfWater`, `fleeFromPlayer`,
  `breakFree`, `relayRoute`, `findWayOut`, `refloat`, `repairInHomeWaters`), `runWatchdog` (с
  `describeShipState`), `checkAiEncounters`, `giveUpCooldownFor` и константы, которые читает
  только этот блок. Смешанные функции разрезаны (лист, п. 7): мировые половины
  `startAiCombat` и `finishCombat` — в мире, режим, музыка, ввод и экран боя — в игре; «Sail On»
  стал `GameWorld.declineEncounter`. `WorldEvents` получил `offerEncounter` и `startCombat`
  (лист, п. 5). В игре — переходники `updateAiShips`, `runWatchdog`, `checkAiEncounters`,
  `giveUpCooldownFor`; удалены переходники без читателей: `pirateLairAnchor`, `breakOffChase`,
  `pickCruisingGround`, `sinkShip`, `routeTo`, `routeAvoiding`, `retargetAiShip`, `watchdog`,
  `lastWatchdogPositions`, `watchdogWorldSeconds`, `fleetScanOffset`, `currentAiTarget`,
  `boxedInReported`. Тестов 524, все зелёные; оба модуля собираются без предупреждений;
  `--shot=sea` без исключений, снимок в порядке. Тесты `GameWorldAiUpdateTest`
  (`GameRandom.withSeed`, настоящая карта): `a populated world sails three minutes with no hull
  aground, lost or standing still`, `a pirate on station runs down a merchantman it sees and
  takes it`, `a hostile ship met by chance offers the player a choice and starts nothing`, `a
  ship that hunted the player down attacks outright`, `the player's own hunt ends in a fight even
  with a ship that would never attack`, `nothing engages the player in harbour or in the ship's
  boat`, `sailing on sends the ship away for a while, and sooner back the more it outguns the
  player`, `a won fight takes the enemy out of the world once, pays nothing twice, and a new ship
  sails`, `a lost fight leaves the player adrift and the victor lets them go`, `a ship the player
  outran comes back only after its cooldown`. Замечено при тестах — находка 30 (пачка 7).
  Проверка автором — сценарий (4) листа, в конце пачки. Коммит `4ea5d84`.
- [x] Шаг 5. Флот: `updateFleet`, встречи, укрытие и выход. Сделано 2026-11-14: в `GameWorld`
  переехали `updateFleet`, `updateFleetEncounters`, `resolveFleetCombat`, `evadeIfNeeded`,
  `resumeIfSafe`, `fleetSeaDistance`, `pathLength` и `FLEET_DETOUR_AVOID_RADIUS`; тост о
  потерянном капитане идёт через `WorldEvents` (`ToastIcon.Castaway`). В игре — переходники
  `updateFleet`, `updateFleetEncounters`, `fleetSeaDistance` (таверна); удалены переходники без
  читателей: `isHostile`, `giveUpCooldownFor`, `shipTier`, `isNearAnyPort`,
  `respawnReplacement`, `pathBetweenPorts`, `fleetPaths`, константа
  `PIRATE_FLEE_AWARENESS_RADIUS` и ставшие лишними импорты. Тестов 531, все зелёные; оба модуля
  собираются без предупреждений; `--shot=sea` без исключений, снимок в порядке. Тесты
  `GameWorldFleetTest` (настоящая карта, `GameRandom.withSeed`): `a captain under way is drawn
  on the sea route, as far along it as the clock says`, `a captain who sights a pirate with a
  harbour nearer runs for it, waits while the pirate is about, and sails on once it has gone`,
  `a captain who sights a pirate far from any harbour steers around it once and keeps going`, `a
  captain caught by a pirate with no guns sinks it, is paid for it, and a new pirate sails`, `a
  captain caught by a pirate that cannot be beaten is lost with the ship, and the player is
  told`. Проверка автором — сценарий (5) листа, в конце пачки. Коммит `0c43d5a`.
- [x] Шаг 6. Корабль игрока; входы `updateAlways`/`updateSailing`; переходники → `world.x`.
  Сделано 2026-11-14: в `GameWorld` переехали `updateAutopilot`, `updateShipHunt`, `tryMove` и
  `ARRIVE_THRESHOLD`; правки состояния из `handleInput` стали `sailByHand`, `huntShip`,
  `setCourse` (клавиши и разбор клика остались в игре, `shipVisible` — тоже); `arriveAtPort`
  разрезан: `GameWorld.arriveAtPort` (доставка пассажира) → `WorldEvents.arrivedAtPort` → в игре
  `enterPort` (режим, музыка, хаб); `--shot-port` идёт тем же путём. Входы `updateAlways` (флот)
  и `updateSailing` (корабль игрока, ИИ, сторож, встречи, рейды, события, обрастание) — порядок
  прежний, комментарии переехали с кодом. Все переходники состояния и функций в игре заменены на
  `world.x`; таверна получает расстояние лямбдой, читающей `world` в момент вызова (готово к
  новому миру). Удалены ставшие лишними `AI_SHIP_ENGAGE_RADIUS` и импорты. Тестов 535, все
  зелёные; оба модуля собираются без предупреждений; `--shot=sea` и `--shot=market
  --shot-port=Havana` без исключений, рынок открывается. Тесты `GameWorldPlayerShipTest`:
  `the autopilot sails a laid course into harbour, makes port once, and pays off a passenger
  bound there`, `a course to open water ends there without making port`, `at the helm the ship
  drops its course and never runs ashore, however hard it is steered at the land`, `a hunted ship
  is run down to contact, where the player's ship holds, and the hunt ends when it is gone`.
  Проверка автором — сценарий (6) листа, в конце пачки. Коммит `9e480fc`.
- [x] Новая игра и загрузка создают новый объект мира (мировая часть находки 12). Сделано
  2026-11-14: в игре одна функция `newWorld()` (новый `GameWorld`, его корабли и морские
  события); её зовут `create()`, `startNewGame` и `loadGame`, позиция корабля пишется в новый
  мир после неё (лист, п. 18). Сброс полей корабля в `beginPlaying` удалён — новый мир и так
  начинает с ними. В KDoc `newWorld` записано, что корабли ИИ, рейды и события в сейв не входят
  намеренно (решение по находке 12 — см. «Решения за автором»). Тестов 536, все зелёные; оба
  модуля собираются без предупреждений; `--shot=sea` без исключений, снимок в порядке. Тест
  `GameWorldNewWorldTest`: `the next game's world has no raid, a full fleet of its own, no
  passenger and no hunt`. После пункта в `PortRoyaleGame` нет логики мира (3153 → 1760 строк),
  сторож импортов зелёный (лист, п. 26). Проверить (сценарий (10) листа): доиграть до объявления
  рейда, подобрать спасённого → Esc → новая игра → рейда нет, пассажира нет, корабли в пути;
  то же через загрузку сейва. Коммит `b7dde5d`.
- [x] Автор запустил игру после пачки и прошёл сценарии листа (п. 25) — 2026-11-14, см. «Решения и отклонения»

## Пачка 3. Модель проверяет себя сама

Пачка 3 — коммит `9a776ca` (2026-11-14). Тестов 558, все зелёные; оба модуля собираются без
предупреждений; `--shot` рынка, верфи, таверны, статуса корабля без исключений, снимки в
порядке. Красный прогон до исправления: упали ровно новые тесты 17, 3, 8, 18 (18 штук).

- [x] **17** Золото может уйти в минус: действия модели не проверяют свои условия — средняя —
  тест сейчас, `PlayerTest`, `FleetSimulationTest`. Сделано: каждое действие `Player` (`buy`,
  `sell`, `buyShip`, `boardShip`, три улучшения, `cleanHull`, `repair`, склад — шесть действий,
  `takeLoan`, `repayLoan`) проверяет своё `can…` и возвращает `Boolean`; экраны играют звук и
  делают побочные действия только при успехе, перерисовываются всегда. `FleetSimulation.assign`
  отказывает занятому капитану, кораблю в другом порту и маршруту «порт → он же». Тесты
  `PlayerTest`: `a good the purse cannot pay for is not bought`, `a good there is no room for is
  not bought`, `a good that is not aboard cannot be sold`, `a trade the player can afford goes
  through and says so`, `a ship the purse cannot pay for is not bought`, `a ship docked in
  another port cannot be boarded`, `upgrades the purse cannot pay for are not fitted`, `a hull is
  not cleaned or repaired on credit`, `a warehouse is not bought or enlarged on credit`, `a
  warehouse moves nothing that is not there to move`, `a second loan is not given and a loan is
  not repaid on credit`; `FleetSimulationTest`: `a captain cannot be sent from a port where the
  ship is not lying`, `a captain cannot be sent from a port to the same port`, `a captain already
  at sea is not given a second ship`. Проверить: в порту открыть верфь/статус корабля, пока
  капитан в рейсе тратит золото, — покупка без денег не проходит, золото не уходит в минус.
- [x] **3** Покупка корабля не проверяет, влезает ли груз — средняя — тест сейчас,
  `PlayerTest`. Сделано: `canBuyShip` требует `cargoFitsOn`; верфь по нажатию «Buy» пишет
  причину отказа (сначала золото, потом трюм). Тесты: `a ship too small for the hold is refused
  however full the purse`, `a ship the hold fits into can be bought, and the hold is within it
  afterwards`. Проверить: набить трюм Sloop больше 30 → верфь → Cutter «Buy» → строка «Your N
  units of cargo will not fit in the Cutter's 30-unit hold», покупки нет.
- [x] **8** Сундук с товаром переполняет трюм — низкая — тест сейчас, `PlayerTest`. Сделано:
  `Player.takeAboard(good, n): Int`, сундук кладёт сколько влезло и пишет фактическое число.
  Тест — на вынесенном мире (`GameWorldSeaEventsTest`): `a chest of goods takes aboard only what
  the hold has room for` (сначала упал: 52 при вместимости 50).
- [x] **18** Маршрут капитана можно сменить посреди рейса — средняя/низкая — тест сейчас,
  `FleetSimulationTest`. Сделано: `FleetSimulation.redirect(captain, portB): Boolean`; окно
  капитана зовёт его, при отказе пишет причину; окно перерисовывается дважды в секунду, пока
  открыто. Тесты: `a captain on the way out keeps the port they are sailing for`, `a captain on
  the way home can be given a new far port`, `a captain cannot be redirected to their own home
  port`. Проверить: открыть окно капитана на пути домой, выбрать новый порт, подождать, пока
  капитан развернётся, — «Confirm New Route» становится недоступной сама, без кликов.
- [x] **22** Отзыв капитана молча уничтожает груз — низкая — правило закрепить в
  `FleetSimulationTest`, подтверждение проверить вручную. Сделано: в окне капитана и в таверне
  первое нажатие «Recall» при грузе на борту превращает кнопку в «Confirm recall» и пишет, что
  и на какую сумму будет списано; второе отзывает. Тест: `recalling a captain with a full hold
  writes off the cargo and what it cost`. Проверить: капитан с грузом → «Recall» → строка о
  списании, капитан ещё в рейсе → «Confirm recall» → отозван; капитан без груза — одним
  нажатием.
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 4. Корабль покидает мир в одном месте

Пачка 4 — коммит `6e134fe` (2026-11-14). Тестов 572, все зелёные; оба модуля собираются без
предупреждений; `--shot=sea` без исключений. Красный прогон до исправления (на заглушках той же
формы с нынешним поведением): упали ровно 10 новых тестов 7, 13, 2.

- [x] **7** Корабль ИИ можно потопить дважды — средняя/низкая — тест на вынесенном мире.
  Сделано как в REVIEW.md, п. 1–4: победа игрока и победа капитана идут через `sinkShip`;
  `sinkShip` первой строкой `if (!aiShips.remove(ship)) return`; `startAiCombat` выходит, если
  корабля уже нет; капитаны не вступают в бой с `currentAiTarget` и с кораблём, по которому
  открыто окно выбора (новое поле мира `pendingEncounter`, ставится при предложении, снимается
  ответом). Тесты: `a ship sunk twice is replaced once`, `a raider the player sinks leaves the
  raid and is replaced once`, `choosing to fight a ship that has meanwhile sunk starts no fight`
  (`GameWorldAiUpdateTest`), `a captain does not fight the ship the player is fighting`, `a
  captain does not fight a ship the player has just been offered` (`GameWorldFleetTest`).
  Проверить: пока открыто «Fight / Sail On» рядом с маршрутом своего капитана — капитан пирата
  не топит; бой с пиратом-участником рейда, победа → у рейда на корабль меньше.
- [x] **13** Рейд может зависнуть навсегда и остановить все следующие — средняя — тест
  сейчас, `PirateRaidTest`, плюс тест на вынесенном мире. Сделано: все потопления через
  `sinkShip` (находка 7); `pirates(alive)`/`warships(alive)` считают только живых; у сбора
  таймаут `RAID_GATHER_SECONDS` = 300 с мирового времени — по истечении отряд выходит с теми, кто
  дошёл (опоздавшие отпущены домой), а если не дошёл никто, рейд отменяется; рейд без живых
  пиратов заканчивается в любой фазе. Тесты `PirateRaidTest`: `pirates gone from the sea without
  being released are not counted as the raid's`, `a band waiting on a straggler sets out on time
  with the ships that arrived`, `a band nobody brings to the rally point is called off on time`,
  `a band that has all arrived sets out at once`, `a band whose every member is gone is not
  formed up`; `GameWorldRaidsTest`: `a raid whose whole band is lost before it is announced ends
  without a word`. Проверить: F3 — после рейда, отряд которого перетопили до объявления,
  следующий рейд приходит в обычный срок.
- [x] **2** В бою с игроком противник всегда с полным корпусом — средняя — тест сейчас,
  `CombatStateTest`. Сделано: `ShipCombatant.currentHull` (по умолчанию = `maxHull`),
  `CombatState` начинает с него и отдаёт каждое изменение через `onEnemyHullChanged`; игра
  передаёт `ship.currentHull` и пишет урон обратно в `AiShip`. Тесты: `an enemy that comes to the
  fight battered starts it battered`, `an enemy with no hull named starts the fight whole`,
  `damage done before the player gets away is handed back`. Проверить: напасть на пирата после
  его боя с военным (F3 показывает корпус) — полоса корпуса врага в бою неполная; попасть и
  уйти — при следующей встрече он всё ещё побит.
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 5. Сохранения

Пачка 5 — коммит `f14fcdc` (2026-11-14). Тестов 582, все зелёные; оба модуля собираются без
предупреждений; `--shot=menu` без исключений. Красный прогон до исправления (на заглушках той же
формы): упали ровно 5 новых тестов — запасная копия (2), пассажир, корпус ниже нуля и выше
максимума.

- [x] **19** Запись сохранения не атомарна, повреждённый сейв не загружается молча —
  средняя/низкая — `SaveManagerTest` после выноса записи из-под Gdx. Сделано: запись и чтение —
  `SaveManager.writeSaveFile`/`readSaveFile` на `java.io.File` (папку сейвов по-прежнему даёт
  Gdx); запись во временный `<имя>.json.tmp` и перенос с заменой, прежний файл остаётся как
  `<имя>.json.bak`; повреждённый файл читается из `.bak` с пометкой; `load` возвращает
  `LoadResult`, неудача — строка в `GameLog` (`SAVE`) и причина в окне загрузки; загрузка из
  копии — тост в игре. Кодировка та же, что у прежних `writeString`/`readString` (кодировка
  платформы), — старые сейвы читаются. Тесты: `a save leaves no half-written file behind and
  reads back`, `saving over a save keeps the one before it as a backup`, `a cut-off save with
  nothing to fall back on is refused, not thrown`, `a cut-off save falls back to the copy before
  it and says so`. Проверить: сохранить дважды под одним именем → в
  `~/PortRoyaleTiny/saves/` рядом с `имя.json` лежит `имя.json.bak`, в списке загрузки его нет;
  обрезать `имя.json` в блокноте → загрузка проходит с тостом о копии; удалить `.bak` и
  обрезать снова → в окне загрузки строка «could not be read», игра не падает.
- [x] **14** Автосохранение срабатывает посреди боя — средняя/низкая — корпус при загрузке:
  `SaveManagerTest`; автосейв: вручную. Сделано: автосейв не срабатывает при `COMBAT` (таймер
  доходит и срабатывает сразу после боя); «Save» в меню паузы во время боя неактивна; корпус
  активного корабля при загрузке — `SaveManager.resolveActiveHull` (от 1 до максимума с
  улучшениями, без бесплатной починки). Тесты: `an active hull saved at zero or below loads
  afloat`, `an active hull saved above the ship's maximum loads at the maximum`, `an active hull
  within bounds loads as saved`. Проверить: в бою Esc → «Save» серая; бой длиннее 10 минут
  автосейва не даёт, после боя автосейв появляется.
- [x] **12** Пассажир пишется в сейв; флаги окон и охота сбрасываются при новой игре и
  загрузке — средняя — `SaveManagerTest`. Мировая часть закрывается в пачке 2. Сделано:
  `GameSaveData.passengerPort` (пусто — никого), `SaveManager.resolvePassenger`; при загрузке
  пассажир садится в новый мир после `newWorld()`. Флаги окон сбрасывает `beginPlaying` (пачка
  1), охоту и прочее мировое — новый мир (пачка 2). Тесты: `a passenger's destination survives
  a save and a load`, `a save written before passengers were saved has nobody aboard`, `a
  passenger bound for a port the game no longer has is not aboard`. Проверить: подобрать
  спасённого → сохранить → выйти из игры → загрузить → доставить: награда выплачена.
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 6. Морские события

Пачка 6 — коммит `a4e9bc4` (2026-11-14). Тестов 585, все зелёные; оба модуля собираются без
предупреждений. Красный прогон до исправления: сторож назвал все 10 строк (`GameWorld.kt`
317–378), тест спасённого упал («took the first one's berth»).

- [x] **4** Морские события берут случайность мимо `GameRandom` — средняя — тест-сторож
  правила. Сделано: десять вызовов в морских событиях (`spawnSeaEvent`, `triggerSeaEvent`,
  `deliverPassenger`) — через `GameRandom` (`nextFloat(a, b)`, `nextIntInclusive`, `pick`).
  Тест-сторож `diagnostics/RandomnessSourceTest`: `game logic takes its chances only from
  GameRandom`, `the guard is looking at the game's own sources`. Проверить: нечего — поведение
  то же, исходы теперь воспроизводятся по зерну из F3.
- [x] **9** Второй спасённый затирает первого — низкая — тест на вынесенном мире. Сделано: с
  пассажиром на борту точка спасённого не срабатывает и остаётся на карте до доставки первого.
  Тест: `with a passenger aboard another castaway is left in the water for later`. Проверить:
  с пассажиром на борту проплыть через точку второго спасённого — ничего не происходит, точка
  остаётся; после доставки первого — подбирается.
- [x] **25** `docs/testing.md` описывает бой как несеемую случайность — низкая — правка
  документа. Сделано: абзац переписан — всё через `GameRandom.withSeed`, сторож следит;
  подстройка противника оставлена как способ закрепить правило, а не число.
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 7. Движение и маршруты

Пачка 7 (5, 10, 15) — коммит `61e8365` (2026-11-14). Тестов 590, все зелёные; оба модуля
собираются без предупреждений; `--shot=sea` без исключений. Красный прогон до исправления: упали
ровно 5 новых тестов.

- [x] **5** Обход угрозы молча превращается в прямой курс — средняя/низкая — тест сейчас,
  `WorldMapTest`. Сделано как в REVIEW.md: старт внутри круга — радиус сужается до расстояния
  от старта до угрозы минус клетка, без обхода — только при нулевом радиусе; цель внутри круга —
  как было. Тест: `a ship that sights danger nearer than the berth it wants still steers clear of
  it` (сначала: «closed to 0.0 of the threat, starting 60.0 off»).
- [x] **10** Запасной маршрут капитана — прямая через сушу — низкая — тест на вынесенном мире.
  Сделано: нет пути к укрытию или в обход — манёвра нет, маршрут прежний, `avoidedThreat` не
  выставляется (попытка повторится), строка в `GameLog` (`FLEET`) — одна на пару «капитан,
  угроза», чтобы не писать каждый кадр. Тест (`GameWorldFleetTest`, карта из двух бассейнов за
  стеной суши): `a captain with no way to the nearest harbour keeps to the route rather than
  sailing through land` (сначала: «ran for a harbour there is no way to … Nassau»).
- [x] **15** Шаг времени для корабля игрока не ограничен — средняя/низкая — тест на
  вынесенном мире. Сделано: кадровое время мира ограничено 0,1 с до умножения на скорость
  (`MAX_FRAME_SECONDS`); автопилот не шагает дальше точки маршрута и остаток хода кадра несёт к
  следующей; `tryMove` двигает корабль игрока кусками по 5 единиц, проверяя воду на каждом.
  Тесты (`GameWorldPlayerShipTest`): `one long frame takes the ship to its waypoint, not past
  it`, `one long step at the helm does not jump a strip of land`, `a frame of five seconds sails
  the ship where five frames of a second do`. Проверить: на 20x перетащить окно игры за
  заголовок секунду-две во время автопилота — корабль не перескакивает сушу и не проскакивает
  порт; на 1x автопилот идёт, как раньше.
- [x] **30** Тяжёлый корпус не может закончить погоню касанием — средняя — тест сейчас,
  `GameWorldAiUpdateTest`. Замечено на шаге 4 пачки 2, исправлено 2026-11-14 по слову автора
  («чини всё по необходимости») вне порядка пачек, как предлагает REVIEW.md: новый
  `GameWorld.closeWithQuarry` — когда маршрут погони кончился, а добыча ближе
  `CHASE_CLOSE_RANGE`, охотник правит прямо на неё через `steer`. Тесты `a galleon runs
  alongside a merchantman lying still and takes it`, `a brigantine runs down a merchantman under
  way and takes it` сначала упали на нынешнем коде («hung off… never took it», «kept station
  off…»), после исправления зелёные; тест на Sloop зелёный; всего 526. Проверить: F3 у
  пиратского галеона или военной бригантины в погоне — корабль доходит до цели вплотную, а не
  висит в корпусе от неё. Коммит `6e546a0`.
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 8. Интерфейс

Пачка 8 — коммит `1799c1b` (2026-11-14). Тестов 599, все зелёные; оба модуля собираются без
предупреждений; `--shot` рынка и крепости без исключений, снимки в порядке.

- [x] **20** Новое сообщение затирает открытое — средняя/низкая — тест чистого класса очереди.
  Сделано: `ui/ToastQueue` (без Scene2D) — новое сообщение встаёт за текущим, вопрос не
  вытесняется и не закрывается без ответа, ответ исполняется ровно один раз; `EventToastUI`
  рисует голову очереди; новая игра и загрузка очищают очередь. Тесты `ToastQueueTest`:
  `messages are shown in the order they came, each after the last is closed`, `a message that
  arrives during a question waits behind it`, `a question cannot be closed without an answer`,
  `the answer to a question is acted on exactly once`, `a message raised by an answer comes after
  what was already waiting`, `clearing drops everything pending`. Проверить: открыто «Fight /
  Sail On», в это время капитана топят или срок займа выходит — после ответа на вопрос
  появляется второе сообщение, ничего не потеряно.
- [x] **21** Экраны порта показывают цены и долг на момент последнего нажатия — низкая —
  проверка вручную. Сделано: рынок и крепость обновляют тексты (цены, золото, трюм, рамки
  избытка/нехватки; долг, «due in», доступность «Repay») каждые 0,25 с реального времени, только
  `setText`, без пересборки таблиц. Проверить: рынок на 20x, продать десять единиц, подождать
  пять секунд — цена меняется без нажатия; крепость с займом при истечении срока — сумма
  удваивается на экране сама.
- [x] **23** Полноэкранный режим «включается» без подходящего режима дисплея — низкая — тест
  чистой функции выбора режима, плюс вручную. Сделано: `ui/chooseDisplayMode` (точное
  совпадение, иначе текущий режим рабочего стола; из совпадений — с наибольшей частотой, как
  было); флаг, подпись и запись в настройки — только после успешного `setFullscreenMode`, в
  `applyResolution` тоже. Тесты `DisplayModeChoiceTest`: `the exact size is chosen when the
  monitor has it`, `without the size the desktop's own mode is kept`, `an empty list falls back
  to the desktop's mode`. Проверить: на мониторе без выбранного разрешения включить полный экран
  — игра уходит в полный экран в разрешении рабочего стола, подпись «On»; если переключение не
  удалось — подпись «Off».
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 9. Порядок в репозитории

Пачка 9 — коммит `4696c75` (2026-11-14). Тестов 603, все зелёные; оба модуля собираются без
предупреждений. Красный прогон до исправления (на прежних `NOTICE` и `WorldWatchdog.kt`): упали
тест сторожа («IllegalStateException: boom») и оба теста NOTICE.

- [x] **6** Исключение в проверке сторожа роняет игру — низкая — тест сейчас. Сделано:
  `checks(...)` в `try/catch`, исключение — проблема `watchdog/check-failed/<класс>` через
  обычный `Reporter` (раз, пока не пройдёт), `active.retainAll(seen)` — в `finally`. Тест
  (`WorldWatchdogTest` в `DiagnosticsToolsTest.kt`): `a check that throws is reported once and
  the sweeps go on`. Проверить: нечего — страховка на будущее.
- [x] **24** NOTICE называет файл карты, которого нет, а поставляемые не названы —
  средняя/низкая — тест сейчас, `AssetIntegrityTest`. Сделано: запись о карте названа по
  фактическим файлам `caribbean_mask.png` и `caribbean.jpg` — оба как производные от карты Uwe
  Dedering (CC BY-SA 3.0), с перечнем изменений. Записи `assets/audio` и `assets/fonts` сверены
  тестом — все файлы названы. Тесты: `every path NOTICE names exists`, `every third-party file
  that ships is credited in NOTICE`. **Автору подтвердить:** что `caribbean.jpg` перерисован
  поверх береговой линии карты Dedering, выведено из листа дизайна («Суша остаётся на прежних
  местах») и из того, что маска — её вода; если карта рисовалась иначе, запись поправить.
- [x] **26** Тесты поиска пути проверяют время по часам — низкая — переписать два теста.
  Сделано: `WorldMap.lastSearchExpanded` (раскрытые клетки последнего поиска); два теста
  проверяют его: дальний маршрут раскрывает меньше 40 000 клеток (2/3 предела поиска), поиск к
  суше не раскрывает ни одной. Замеры времени перенесены в `PathfindingTimingTest` под тегом
  `simulation` (вне `:core:test`). Тесты: `a cross-map route opens only part of the sea`, `an
  unreachable goal gives up promptly instead of surveying the whole sea`.
- [x] **27** Версия записана вручную во втором месте — низкая — тест-сверка. Сделано:
  `VersionAgreementTest` — `the launcher states the same version as the build` (сверяет
  `appVersion` в `gradle.properties` с `VERSION` в `Lwjgl3Launcher.kt`). Перенос версии в
  манифест/ресурс не делал — тест стоит, пока есть дубль.
- [x] **28** Неотслеживаемые остатки в `art-src/` и `tools/art/` — низкая — чистый
  `git status`. Сделано по решению (см. «Решения за автором»): исходники панели корабля HUD
  (`art-src/ui/ship_panel.png`, `ship_panel_cut.png`) закоммичены; `art-src/compare/`,
  `style/`, `props/`, `ui/cut/`, `tools/art/gen.py`, `art-src/icons/coin_sdxl.png` — в
  `.gitignore`. В `git status` остались только документы ревью и `Project design/decisions/` —
  их коммит на шаге «Завершение».
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 10. Подтверждённое из «Сначала подтвердить»: логика

Пачка 10 — коммит `218404b` (2026-11-14). Тестов 615, все зелёные; оба модуля собираются без
предупреждений. Красный прогон до исправления (на заглушках той же формы): упали ровно 6 новых
тестов 31–34 и 38.

- [x] **31** Капитан в укрытии ждёт без срока — низкая — тест сейчас, `GameWorldFleetTest`.
  Сделано: `SHELTER_MAX_GAME_MINUTES` = 60 игровых минут (6 минут за клавиатурой на 1x) от
  прибытия в укрытие; после — выход на маршрут, даже если угроза рядом. Тест: `a captain kept in
  harbour by a ship that never leaves sails on once the time is up`.
- [x] **32** Пират гонится за капитаном, которого уже нет — низкая — тест сейчас. Сделано:
  `updatePursuit` бросает погоню, если `pursuingCaptain` больше не среди активных капитанов.
  Тест: `a pirate chasing a captain who is lost gives the chase up`.
- [x] **33** `fleetPaths` не чистится при потере и отзыве капитана — низкая — тест сейчас.
  Сделано: `updateFleetEncounters` первой строкой оставляет записи только активных капитанов.
  Тест: `a captain recalled or lost leaves no drawn course behind`.
- [x] **34** Неудачный манёвр капитана повторяет поиск пути каждый кадр — низкая — тест сейчас.
  Сделано: `evadeIfNeeded` и `resumeIfSafe` тратят по маршруту из `routeBudget` (новый
  `spendCourse`); при исчерпанном бюджете ждут кадра. Тест: `a captain's manoeuvre waits for a
  frame with a course to spare`. В тестах флота бюджет теперь пополняется, как в игре
  (`routeBudget = 6` в подготовке).
- [x] **38** Правленый или повреждённый сейв проходит без границ — низкая — тест сейчас.
  Сделано: `resolveOwnedShips` — корпус до максимума, обрастание 0..1 (и не-конечное → 0),
  уровни 0..3; `SaveManager.resolveActiveShipId` — несуществующий id → первый корабль; займ с
  неизвестной фракцией — строка в `GameLog`. Тесты: `a docked ship's condition outside its
  bounds comes back within them`, `an active ship id that is not in the fleet falls back to the
  first ship`, `an active ship id that is in the fleet is kept`.
- [x] **42** Классы логики без тестов — низкая — `LoanTest`. Сделано: правило просрочки займа
  перенесено из игры в `Player.checkLoanDeadline` (игра только показывает сообщение). Тесты
  `LoanTest`: `before the deadline a loan stays as it was taken`, `at the deadline the debt
  doubles and the lender's navy turns hostile`, `an overdue loan doubles only once`, `with no
  loan there is nothing to fall due`, `every offer asks back more than it lends and gives time to
  do it`. `Settings` и `DiagnosticsFiles` — с находками 36 и 37 (пачка 11).
- [x] Автор запустил игру после пачки — 2026-11-14

## Пачка 11. Подтверждённое из «Сначала подтвердить»: интерфейс, диагностика, сборка

Пачка 11 — коммит `2418d44` (2026-11-14). Тестов 623, все зелёные; оба модуля собираются без
предупреждений; `--shot` моря и верфи без исключений, HUD на месте. Красного прогона нет: у
пунктов пачки нет тестов «сейчас» (новые тесты — на чистые функции, появившиеся с исправлением).

- [x] **35** Клик по карте при открытом сообщении исполняется после его закрытия — низкая —
  вручную. Сделано: `touchDown` при открытом тосте клик съедает и не запоминает. Заодно (пункт
  «фокус» из «Сначала подтвердить»): поле имени на верфи снимает фокус клавиатуры, когда
  скрыто. Проверить: при открытом сообщении кликнуть по морю → закрыть сообщение — корабль не
  трогается; на верфи купить корабль → после «Confirm» клавиши WASD ведут корабль, а не печатают
  в невидимое поле.
- [x] **36** Настройки пишутся на каждый шаг ползунка, `NaN` проходит в громкость — низкая —
  `SettingsTest`. Сделано: громкость применяется сразу, файл пишется по отпусканию ползунка и по
  «Back»; `SettingsData.sanitized()` — не-конечная громкость → по умолчанию, громкость 0..1,
  размер окна 320..16384; `AudioManager.setMasterVolume` не пропускает не-конечное. Тесты
  `SettingsTest`: `a volume that is not a number comes back as the default`, `an infinite volume
  comes back within bounds`, `volumes outside 0 to 1 are brought within it`, `ordinary settings
  pass through unchanged`. Проверить: протащить ползунок громкости — звук меняется сразу; в
  `settings.json` вписать `NaN` в громкость → игра запускается с громкостью по умолчанию.
- [x] **37** Диагностика: имена файлов до секунды, лог сессии без предела — низкая —
  `DiagnosticsFilesTest`. Сделано: `uniqueName` — метка до миллисекунды и суффикс `-2`, `-3`,
  если имя занято (лог, отчёт о падении, снимок); `LogBudget` — предел лога сессии 5 МБ, после
  него одна строка о пропуске. Тесты: `two files named in the same millisecond get different
  names`, `a name already taken gets a suffix`, `the session log stops growing at its limit and
  says so once`. Проверить: два раза быстро F12 — два снимка в папке диагностики.
- [x] **40** В jar уходят спрайты, на которые код не ссылается — низкая — `AssetIntegrityTest`.
  Сделано: старый набор Kenney — 29 кораблей и 96 тайлов (`assets/sprites/tiles/` код тоже не
  читал) — перенесён в `art-src/legacy/kenney-ships/` и `kenney-tiles/`; `dinghyLarge1.png`
  (шлюпка) остался в `assets`; NOTICE поправлен, `AssetIntegrityTest` зелёный. Цвета нового
  набора (`ship2_{cream,red,blue,green}_{1,3,5}`) оставлены — части набора 5×6. Проверить:
  шлюпка после поражения рисуется, как раньше; jar меньше.
- [x] **41** `ReferenceDocsTest` не ловит отставание `Reference/` — низкая — сам тест. Сделано:
  `generatedBlocks()` — один список блоков для записи и для сверки; новый `ReferenceDocsCurrentTest`
  (без тега, в `:core:test`): `the reference tables say what the code says` — зелёный, то есть
  `Reference/` сейчас совпадает с кодом.
- [x] **43** CI: право записи у всего workflow, actions по тегу — низкая — прогон CI. Сделано:
  верхний уровень `contents: read`, `contents: write` — только задаче `release`; actions
  закреплены по SHA коммита (получены `git ls-remote` 2026-11-14), тег — в комментарии.
  Проверить: следующий прогон workflow на push в `main` проходит (после «Завершения»).
- [x] **44** Лишняя работа каждый кадр (строй, подписи HUD, звук, текстуры) — низкая — запуском.
  Сделано: подписи HUD (дата, золото, скорость, имя корабля и капитана) — `setText`/`fitLabel`
  только при смене; дорожки музыки создаются при первом использовании, громкость применяется к
  уже созданным; текстуры заливок и рамок `UiSkinFactory` освобождаются при выходе вместе со
  шрифтом. Кильватер строя рейда и список статусов HUD оставлены — см. «Решения и отклонения».
  Проверить: HUD обновляет золото и дату, как раньше; музыка в море и в бою играет.
- [x] Автор запустил игру после пачки — 2026-11-14

## Сначала подтвердить

Пункты из раздела «Не подтверждено» в REVIEW.md. Берутся после пачки 9, если автор не скажет
иначе. Исключение — первый пункт: его проверить в пачке 3, там тот же код и тот же тестовый
файл.

- [x] Капитан может навсегда зависнуть с грузом — сценарий в `FleetSimulationTest`. Не
  подтвердилось 2026-11-14: тест `a captain whose hold can no longer be sold at a profit trades
  again in the end` (купил при нейтральной репутации, затем репутация флага на обоих концах —
  минимум, цены на базе) зелёный с первого прогона — капитан продаёт и торгует дальше. Цены в
  игре со временем возвращаются к базе, так что «навсегда» не выходит; задержка на несколько
  рейсов возможна и задумана («не фиксировать убыток»). Тест оставлен сторожем.
- [x] У укрытия капитана в порту нет таймаута — подтверждено частично 2026-11-14 → находка
  31 (пачка 10).
- [x] `pursuingCaptain` не очищается при потере капитана — подтверждено → находка 32.
- [x] `fleetPaths` не чистится при потере капитана — подтверждено → находка 33.
- [x] Клик по карте при открытом сообщении исполняется после его закрытия — подтверждено →
  находка 35 (пачка 11).
- [x] Поиск пути мимо покадрового бюджета; бюджет задан на кадр, а не на секунду — первая
  половина подтверждена → находка 34; вторая не ошибка: бюджет на кадр задуман (комментарий в
  `render()`).
- [x] Покадровые выделения памяти: строй рейда, статусы кораблей, `raid.pirates()`, `Pilot` —
  подтверждено → находка 44 (`Pilot` — только у берега, не каждый кадр).
- [x] Подписи HUD перекомпоновываются каждый кадр — подтверждено → находка 44.
- [x] Текстуры `UiSkinFactory` создаются мимо `Skin` и не освобождаются — подтверждено →
  находка 44.
- [x] Настройки пишутся в файл на каждое движение ползунка; `NaN` проходит в громкость —
  подтверждено → находка 36.
- [x] Звук: все дорожки грузятся при старте, звуки — при первом проигрывании — подтверждено
  → находка 44 (дорожки; звуки при первом проигрывании — короткие, оставлено).
- [x] Фокус клавиатуры остаётся на скрытом поле имени корабля — подтверждено для верфи (в
  доке — нет) → в пачку 11 вместе с 35.
- [x] Диагностика: имена файлов с точностью до секунды — подтверждено → находка 37.
- [x] Диагностика: файл сессии не ограничен по размеру — подтверждено → находка 37.
- [x] Повреждённый или правленый сейв: займ с неизвестной фракцией, значения без границ,
  несуществующий `activeShipId` — подтверждено → находка 38.
- [x] Цена найма капитана упирается в `Int.MAX_VALUE` — подтверждено, но не ошибка
  (насыщение, не переполнение; недостижимо без миллиарда золота) → находка 39, чинить нечего.
- [x] В jar уходят спрайты, на которые код не ссылается — подтверждено (41 файл) → находка 40.
- [x] `ReferenceDocsTest` не проверяет, что `Reference/` не отстал от кода — подтверждено →
  находка 41.
- [x] Классы логики без единого теста: `Loan`, `SeaEvent`, `Settings`, `DiagnosticsFiles`,
  чтение и запись файла сохранения — подтверждено для `Loan`, `Settings`, `DiagnosticsFiles`
  (`SeaEvent` покрыт косвенно, файл сохранения — с пачки 5) → находка 42.
- [x] CI: права на запись выданы всему workflow; actions закреплены по тегу, а не по хэшу —
  подтверждено → находка 43.

## Решения и отклонения

Сюда — ответы автора на вопросы выше, случаи, когда исправление сделано не так, как
рекомендует REVIEW.md, и находки, которые не подтвердились. По строке на запись, с датой.

- 2026-11-14, находка 16. Точку и пробел на конце имени не отклоняю, а убираю: точка
  отбрасывается фильтром вместе с прочими недопустимыми символами, пробелы по краям
  обрезаются. Итог тот же, что требует REVIEW.md (имя на точку или пробел до диска не
  доходит), но игрок получает сохранение, а не отказ. Недопустимые символы тоже отбрасываются,
  а не ведут к отказу («оставить буквы, цифры…»); итоговое имя видно в «Saved "…"».
- 2026-11-14, находка 16. Не считаю ошибкой, но для порядка: тестов «сейчас» в буквальном
  смысле не было — функции очистки в коде не существовало. Красный прогон сделан на заглушке
  `checkSaveName`, повторявшей нынешнее поведение экрана (обрезка пробелов + проверка на
  пустоту); на ней упали 6 тестов из 10, ровно те, где нынешний код пропускает опасное имя.
- 2026-11-14, пачка 2. Порядок шагов отличается от предложенного в REVIEW.md («Условия
  выноса», п. 3): рейды вызывают семь функций кораблей ИИ (`retargetAiShip`, `routeTo`,
  `pirateBerth`, `pirateLairAnchor`, `pirateOnStation`, `breakOffChase`,
  `respawnReplacement`), а ИИ — `sinkShip` и `raidDestinationFor`. Поэтому перед рейдами
  добавлен шаг «общие помощники ИИ», шагов стало 6. Согласовано автором (лист, п. 4).
- 2026-11-14, весь чек-лист. Правило 11 («не коммитить без просьбы») заменено решением автора:
  всё делается локально в ветке `review/checklist`, локальный коммит после каждой пачки (в
  пачке 2 — после каждого шага) с хэшем у пункта для отката; общий коммит и push в `main` —
  отдельным последним шагом («Завершение»). Пачка 1 закоммичена так первой: `d2a0990`.
- 2026-11-14, пачка 2, шаг 1. `shipVisible` оставлен в игре, хотя лист относит «корабль
  игрока» к миру: флаг читают только отрисовка (корабль / свечение у причала) и пульс порта,
  мировая логика его не касается. По смыслу это то же, что флаги окон (лист, п. 19).
- 2026-11-14, пачка 2, шаг 1. Доставка спасённого вынесена из `arriveAtPort` уже сейчас, как
  `GameWorld.deliverPassenger`: это блок морских событий, и тест листа п. 25 (1) «доставка
  платит и очищает» требует её в мире на шаге 1. Остальное в `arriveAtPort` ждёт шага 6.
- 2026-11-14, пачка 2, шаг 1. Сторож импортов проверяет три файла мира (`GameWorld.kt`,
  `WorldEvents.kt`, `ResolvedPort.kt`), а не один `GameWorld.kt`, и вторым тестом ловит полные
  имена без импорта (`Gdx.`, `com.portroyale.tiny.ui…`). Строже листа (п. 8), не мягче.
- 2026-11-14, пачка 2, шаг 1. Ключ иконки — `sealed interface ToastIcon` (`Coin`, `Bottle`,
  `Castaway`, `Cargo(good)`): «для товара — сам `Good`» (лист, п. 22) обёрнут в `Cargo`, чтобы
  у `toast` была одна сигнатура. Переходников для `seaEventSlots` и `passengerDestination` нет:
  после переноса их в игре никто не читает (лист, п. 3). Новые типы `world` публичные, а не
  `internal`: в коде `internal` не используется нигде.
- 2026-11-14, пачка 2, шаг 2. Шаг забрал больше, чем перечисляет лист (п. 4 (2)), — по правилу
  того же пункта «перенесённый код не зовёт игру»: `retargetAiShip` зовёт `raidDestinationFor`
  (а та — `prowlPointInside`) и `pickCruisingGround`, `fleetShipTransform` — цепочку `fleetPath`,
  `pointAlongPath`, `portApproachPos`, `pathBetweenPorts`. `raidDestinationFor` и
  `prowlPointInside` по смыслу — рейды (шаг 3), но зовёт их только `retargetAiShip`, переходников
  для них нет. `routeAvoiding` переехал вместе с `routeTo`: тот же `routeBudget`, иначе правило
  бюджета жило бы в двух местах. `giveUpCooldownFor` остался в игре: блок его не зовёт.
- 2026-11-14, пачка 2, шаг 2. Тест «позиция по ходу рейса» для `fleetShipTransform` не писал:
  лист относит его к шагу 5 (п. 25 (5)). На шаге 2 функция переехала без своего теста.
- 2026-11-14, находка 29. Исправлена посреди пачки 2 по прямому слову автора («ошибку
  найденную тоже исправь»), хотя лист (п. 1, 15, 24) и правило 8 велят не чинить по ходу
  переноса. Причину запрета — не отличить «сломал перенос» от «изменило исправление» — снимает
  отдельный коммит: перенос шага 2 — `e8095d8`, исправление — `f9a803f`, после него.
  Видимое изменение (корабли с первого кадра в пути) — единственное отступление от п. 1 листа.
- 2026-11-14, пачка 2, шаг 4. `WorldEvents` получил два обязательных метода, поэтому в
  тестовые заглушки шагов 1–3 (`GameWorldSeaEventsTest`, `GameWorldAiHelpersTest`,
  `GameWorldRaidsTest`) добавлены пустые `offerEncounter`/`startCombat`; ни одно утверждение не
  менялось. Методы без реализации по умолчанию сознательно: забытое игрой событие должна ловить
  сборка, а не тишина в игре.
- 2026-11-14, пачка 2, шаг 4. Текст окна «Fight / Sail On» остался в игре: мир сообщает только,
  с каким кораблём встреча (`offerEncounter(ship)`), окно и его слова — дело игры. Тосты
  победы и поражения, наоборот, переехали в мир вместе со своими ветками `finishCombat`
  (`ToastIcon.Coin`, `ToastIcon.Castaway` — те же картинки).
- 2026-11-14, пачка 2, шаг 4. `AI_SHIP_ENGAGE_RADIUS` и `PIRATE_FLEE_AWARENESS_RADIUS` теперь
  есть и в игре, и в мире: это псевдонимы `Encounters.CONTACT_RADIUS`/`SIGHT_RADIUS`, а не
  числа, и в игре их ещё читает флот (шаг 5) — так же, как `PORT_SAFE_RADIUS` на шаге 1. Копия
  `PORT_SAFE_RADIUS` в игре удалена: читателей не осталось.
- 2026-11-14, пачка 2, шаг 4. Тест листа п. 25 (4) «пират догоняет торговца» написан на пирате
  класса Sloop. На первом варианте (выдуманный корпус: скорость 14, поворот 28°/с) он упал, и
  разбор показал ошибку игры, а не переноса: корпус, чей радиус разворота больше дальности
  касания, погоню касанием не заканчивает (Galleon — никогда). Заведена находка 30; тест на
  ошибку — при её исправлении (лист, п. 15).
- 2026-11-14, весь чек-лист. Слово автора после шага 4: «чини всё по необходимости. Мне нужно,
  чтобы чек-лист был обработан и продолжилась дальнейшая разработка игры». Поэтому: (а) правило
  4 «один шаг за заход» снято — иду дальше без остановки, коммит после каждого шага/пачки
  остаётся точкой отката; (б) находки, замеченные по ходу, чиню отдельным коммитом, как 29;
  (в) вопросы из «Решений за автором» решаю по рекомендации ревью и записываю решение у
  вопроса; (г) проверки «Автор запустил игру» остаются за автором — их не отмечаю, вместо них
  делаю `--shot` и прогоны на настоящей карте.

- 2026-11-14, пачка 2, шаг 6. `GameWorld.updateAutopilot` и `updateShipHunt` публичные, хотя
  в игре их звал только свой класс: тесты листа п. 25 (6) гоняют корабль игрока без остального
  мира (`updateSailing` запустил бы ещё рейды и события). Видимость расширена ровно для этого
  (лист, п. 16). `WorldEvents` получил `arrivedAtPort` — в заглушки тестов шагов 1–5 добавлен
  пустой метод.
- 2026-11-14, пачка 2, шаг 6. Мировая половина `arriveAtPort` — `GameWorld.arriveAtPort`
  (доставка пассажира, затем событие); `--shot-port` тоже идёт через неё, как раньше шёл через
  игровую `arriveAtPort`. Игровая половина переименована в `enterPort`, чтобы два разных
  действия не носили одно имя.

- 2026-11-14, пачка 3, находка 17. Существующий тест `reassigning an idle captain costs
  nothing extra` назначал капитана на маршрут из порта, где корабля нет (Portobelo → Havana при
  корабле в Havana), — экран так не позволяет, а новая проверка `assign` отказывает. Маршрут в
  тесте заменён на Havana → Nassau; то, что тест проверяет (повторное назначение бесплатно), не
  менялось.
- 2026-11-14, пачка 3, находка 17. `FleetSimulation.hire` новых проверок не получил: находка
  называет только `assign`, а у найма экран и так показывает лишь корабли в этом порту. Записано
  в «Новые наблюдения».
- 2026-11-14, пачка 3, находка 3. На верфи кнопки «Buy» не гаснут заранее — экрана с
  недоступными кнопками там и не было; причина отказа появляется по нажатию строкой над
  кораблями. Отказ со словами, как требует решение по находке 3, — есть.
- 2026-11-14, пачка 3, находка 22. В таверне отметка «Confirm recall» снимается при следующей
  перерисовке или повторном открытии — по таймеру таверна не перерисовывается (окно капитана —
  перерисовывается).

- 2026-11-14, пачка 4, находка 13. Сверх REVIEW.md: рейд, который так и не объявили (отменён
  на сборе или потерял весь отряд до объявления), заканчивается без тоста «Pirate activity …
  has subsided» — иначе игрок получал бы отбой тревоги, о которой не слышал. Тест `when the
  raid's time is up…` ставил «время вышло» необъявленному рейду в фазе сбора, чего в игре не
  бывает (таймер идёт только в фазе `RAIDING`, после объявления); подготовка теста переведена
  в `RAIDING` с объявлением, утверждения не менялись. Утверждение о «ещё учтённом» обломке в
  `a ship sunk inside a raid…` теперь смотрит в книгу рейда (`displacedAnchors`), а не в
  `pirates(alive)` — иначе оно проходило бы при любом коде.
- 2026-11-14, пачка 4, находка 2. В бою корпус врага не ниже 1 на входе (`coerceIn(1, max)`):
  корабль с нулём уже потоплен. Свести два цикла боя в один (REVIEW.md, «позже») — не делал.

- 2026-11-14, пачка 7, находка 15. Сверх буквы REVIEW.md («шаг обрезать по расстоянию до
  точки»): кадр, в котором автопилот достигал точки маршрута, раньше проходил без движения, и
  тест «один кадр в 5 с = пять кадров по 1 с» падал бы и с одной обрезкой. Остаток хода теперь
  идёт к следующей точке в том же кадре. Видимое следствие: корабль доходит до последней точки
  (подхода к порту) ровно, а не останавливается в пределах 14 единиц от неё.
- 2026-11-14, пачка 7, находка 15. Ограничение кадра 0,1 с касается всего мира (часы, рынок, ИИ),
  а не только корабля игрока, — так велит REVIEW.md («до умножения на скорость»). Кадр длиннее
  0,1 с (перетаскивание окна) теряет время мира сверх этого.

- 2026-11-14, «Сначала подтвердить» и пачки 10–11. По слову автора раздел пройден целиком:
  проверка кода — сабагентом `Explore`, подтверждённое заведено находками 31–44 (REVIEW.md) и
  разнесено на пачки 10 (логика) и 11 (интерфейс, диагностика, сборка). Находка 39 (цена
  найма) — подтверждена, но не ошибка, не чинилась.
- 2026-11-14, пачка 11, находка 44. Кильватер строя рейда (`keepLineAhead`: `wake.toList()` на
  каждого ведомого каждый кадр, со сбросом `pathIndex`) не трогал: копия заодно перекладывает
  ведомому путь, и замена её на общий список — правка движения, а не расхода; расход только во
  время рейда. Список статусов HUD (`buildShipStatuses`) — десяток объектов за кадр, оставлен.
- 2026-11-14, пачка 11, находка 36. Выход из настроек по Esc идёт мимо «Back» (через
  `showMainMenu` в игре) и файл не пишет — но ползунок пишет файл в конце перетаскивания, так
  что значение не теряется.

- 2026-11-14, находка 28, после отчёта. Автор: остатки локальной генерации удалить — «мне пока
  локальная генерация не нужна». `tools/art/gen.py` и `art-src/icons/coin_sdxl.png` удалены,
  их строки убраны из `.gitignore`.
- 2026-11-14, после отчёта. Автор пересмотрел решение по находке 12: сейв — полный (мир со
  всеми кораблями, рейдом, событиями). Начата отдельная работа «Полный сейв»: сначала лист
  решений, код — после ответа автора.

- 2026-11-14, все пачки. Автор запустил игру и ответил: «По игре всё работает корректно,
  насколько я могу сейчас судить». По правилу 7 пункты `[~]` переведены в `[x]`, проверки «Автор
  запустил игру» отмечены. Для порядка: проверка была общей, а не по каждому сценарию из шагов
  проверки; редкие случаи (повреждённый сейв, монитор без нужного разрешения, капитан у порта с
  пиратом) закрыты тестами, в игре отдельно не вызывались.
- 2026-11-14, находка 24. Автор: «карта с википедии была взята за референс и полностью
  перерисована ранее через ИИ», маска «по-моему тоже переделана под новую картинку». Запись в
  NOTICE переписана: обе карты — сделаны для проекта (картинка — ИИ, маска — по картинке), карта
  Dedering — только образец береговой линии, её пикселей в файлах нет; упоминание оставлено как
  указание источника.
- 2026-11-14, находка 12 (пересмотр). Лист решений «Полный сейв» (18 пунктов) принят автором
  без правок («описано грамотно… доверюсь тебе»); сохранён в
  `Project design/decisions/full-save.md`.

- 2026-11-14, «Полный сейв» (пересмотр находки 12) — коммит `e64452c`. Сделано по листу
  `Project design/decisions/full-save.md`: `save/WorldSave.kt` пишет и восстанавливает мир
  целиком (корабли ИИ со всеми полями и ссылками по `id`, рейд, эскалация, события, курс и
  охота игрока, обходы капитанов, счётчик `id`); загрузка восстанавливает мир, старый сейв — с
  новым миром и сообщением. Попутно: `id` корабля ИИ — параметр конструктора; `sinkShip`
  чистит у остальных `fleeingFrom` и `avoidedThreat` (иначе живой корабль ссылался бы на
  обломок, которого сейв не вернёт); отслеживаемая `Pilot` точка восстанавливается тем же
  объектом пути (`Pilot` узнаёт её по ссылке). Тестов 630, все зелёные. Тесты `WorldSaveTest`:
  `every AI ship comes back where it was, doing what it was doing`, `a raid comes back at the
  same stage, on the same clock, with the same ships`, `sea events come back where they lay, with
  the same clocks`, `the player's course and hunt and the captain's detour come back`, `the
  loaded world carries on exactly as the saved one would have`, `a save without a world, or with
  one this build cannot read, restores nothing`, `references to ships, captains and ports that
  are gone come back empty`. Проверить (п. 18 листа): во время объявленного рейда, с погоней и
  сундуком рядом — сохранить, выйти из игры, загрузить: рейд на той же стадии с тем же
  таймером, погоня продолжается, сундук на месте, корабль игрока идёт своим курсом.

## Новые наблюдения

Сюда — то, что замечено по ходу и не относится к находке, над которой идёт работа.
По строке на запись: место в коде, суть, дата.

- 2026-11-14, `CLAUDE.md`, «Testing discipline»: там записано 471 тест, после пачки 1 их 483,
  после шага 1 пачки 2 — 495, после шага 2 — 505, после находки 29 — 506, после шага 3 — 514,
  после шага 4 — 524. Не правил — это не часть находок.
- 2026-11-14, над `updateFlight` (с шага 4 — в `GameWorld.kt`): KDoc «On station, the band
  spreads out and starts taking merchantmen…» остался без функции — текст времён рейдов, под
  ним идёт собственный KDoc `updateFlight`. Переехал вместе с `updateFlight`, как был. Так же
  переехали два KDoc не на своих местах: KDoc `refloat` и текст KDoc `repairInHomeWaters` стоят
  над `describeShipState`.
- 2026-11-14, `PortRoyaleGame.kt`: импорты `world.Helm` и `world.BUSY_CORRIDOR_RADIUS` не
  используются уже до шага 4 (проверено по `HEAD`). Не трогал.
- 2026-11-14, `GameWorld.kt`, комментарий над `GIVE_UP_COOLDOWN_CAUTIOUS` и KDoc переехавших
  функций ссылаются на `PortRoyaleGame.giveUpCooldownFor`/`PortRoyaleGame.evadeIfNeeded` —
  после переноса часть этих ссылок указывает не туда. Текст перенесён как есть (лист, п. 1).
- 2026-11-14, `--shot=sea`: камера за 30 кадров съёмки уезжает прокруткой у края
  (`EDGE_PAN_MARGIN`), если курсор стоит у края окна — снимок тогда смотрит мимо корабля
  игрока. Режим съёмки читает настоящую мышь; для проверки «ничего не пропало» это не помеха,
  но снимок от запуска к запуску разный.
- 2026-11-14, `GameWorld.spawnAiShips` → `scatterAlongRoutes`: разброс кораблей по маршрутам
  при старте не срабатывал (бюджет маршрутов при создании мира 0). Подтверждено тестом и
  заведено находкой 29 в REVIEW.md; исправлено по слову автора — см. пункт 29 в пачке 2.
- 2026-11-14, `world/GameClock.kt`, KDoc объекта: «used for … (currently just loan
  deadlines)» устарело — от часов зависят ещё календарь HUD и рейсы капитанов (это же пишет
  находка 1).
- 2026-11-14, `save/GameSave.kt`, `SaveManager.save`: ни ручное, ни автосохранение не пишут
  в `GameLog` строку об успешной записи, хотя KDoc `GameLog` называет «a save written» среди
  того, что он записывает. Категория `SAVE` до находки 16 не использовалась нигде.
- 2026-11-14, `ui/SaveGameUI.kt`, список сохранений: старый сейв, чьё имя содержит теперь
  отбрасываемые символы (например `my.save`), при перезаписи кликом по списку сохранится под
  очищенным именем (`mysave`), а старый файл останется рядом. Ручная запись под такими
  именами теперь невозможна, так что касается только сейвов, сделанных до пачки 1.
- 2026-11-14, `ui/SaveGameUI.kt`: ручная ошибка записи показывается только в строке статуса и
  в `GameLog` не попадает (находка 16 требует лога только для автосейва).

- 2026-11-14, `economy/FleetSimulation.kt`, `hire`: не проверяет, что капитан свободен, корабль
  стоит в порту отправления и порты разные (у `assign` эти проверки теперь есть, находка 17).
  Сейчас экран найма показывает только корабли этого порта, так что вреда нет.

## Завершение: общий коммит и push в `main`

Последний шаг всего чек-листа — после пачки 9 и раздела «Сначала подтвердить». До него всё
делается локально, в ветке `review/checklist` (правило 11); в `main` не уходит ничего.

- [x] Все пункты выше закрыты `[x]` или отклонены с записью в «Решения и отклонения»;
  `:core:test` зелёный; автор запустил игру на итоговой ветке — пункты закрыты, тесты зелёные
  (630); ждёт проверки автором полного сейва на итоговой ветке
- [x] Применено решение автора по находке 28: что из `docs/REVIEW.md`, этого файла и
  `Project design/decisions/` входит в коммит, что уходит в `.gitignore`, что удаляется
- [x] Все изменения ветки `review/checklist` сведены в один общий коммит в `main`
  (`git merge --squash`); список закрытых находок — в сообщении коммита
- [x] Push `main` — по прямой просьбе автора в момент этого шага
- [ ] Судьба локальной ветки `review/checklist` с пошаговыми коммитами — по слову автора

Завершено 2026-11-14 по слову автора: «Предлагаю работу по чек-листу считать оконченной. Всё
коммитим, обновляем версию игры и пушим в мейн». Версия 0.4.6.1 (`gradle.properties`, заголовок
окна, `CHANGELOG.md`, `README.md`); `Reference/` совпадает с кодом (`ReferenceDocsCurrentTest`).
В общий коммит вошли `docs/REVIEW.md`, этот файл и `Project design/decisions/` (решение по
находке 28). Листы `review-batch-2.md` и `full-save.md` по правилу навыка `veha` («удалить, когда
веха вышла») полагается убрать после выпуска — не удалены: удаление неотслеживаемых файлов
остановила проверка безопасности, а после коммита это решение автора (один обратимый коммит).
Полный сейв автор в игре до выпуска не проверял — закрыт тестами `WorldSaveTest`. Ветка
`review/checklist` с пошаговыми коммитами оставлена локально; её судьба — по слову автора.
