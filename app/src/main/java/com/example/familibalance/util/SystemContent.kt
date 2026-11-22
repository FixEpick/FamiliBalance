package com.example.familibalance.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.familibalance.R

/**
 * Системный контент (встроенные квесты и достижения).
 *
 * В профиле хранятся русские строки (data-контракт, синхронизация с сервером).
 * Если текст совпадает с известным системным — на рендере подменяем его
 * переводом из string-ресурсов, поэтому смена языка работает без миграции
 * данных. Пользовательский контент (кастомные/бонусные квесты, названия,
 * отредактированные родителем) не совпадает с ключами и выводится как есть.
 */
object SystemContent {

    /** Комментарий заявки на вывод по умолчанию (ставится в CashOutDialog). */
    const val DEFAULT_CASHOUT_COMMENT = "Вывод наличных"

    /** Системные заголовки: встроенные квесты + достижения + служебные строки истории. */
    private val titleRes = mapOf(
        // Встроенные квесты
        "Фото шкафа" to R.string.sys_quest_wardrobe_title,
        "Фото комнаты" to R.string.sys_quest_room_title,
        "📚 Дневник" to R.string.sys_quest_diary_title,
        "📚 Итоги недели (Дневник)" to R.string.sys_quest_diary_week_title,
        // Достижения — Дневник
        "Первая кровь" to R.string.sys_ach_diary_1_title,
        "Случайный Крит" to R.string.sys_ach_diary_5_title,
        "Босс Первой Парты" to R.string.sys_ach_diary_10_title,
        "Чит-код на знания" to R.string.sys_ach_diary_20_title,
        "Абсолютный Баланс" to R.string.sys_ach_diary_30_title,
        "Спидраннер четверти" to R.string.sys_ach_diary_40_title,
        "Легенда Сервера" to R.string.sys_ach_diary_50_title,
        // Достижения — Шкаф
        "Первооткрыватель полок" to R.string.sys_ach_wardrobe_1_title,
        "Победитель Монстров" to R.string.sys_ach_wardrobe_5_title,
        "Тетрис-Мастер" to R.string.sys_ach_wardrobe_10_title,
        "Инвентарь 100 уровня" to R.string.sys_ach_wardrobe_20_title,
        "Портал в Нарнию" to R.string.sys_ach_wardrobe_30_title,
        "Хранитель Текстур" to R.string.sys_ach_wardrobe_40_title,
        "Бог Гардероба" to R.string.sys_ach_wardrobe_50_title,
        // Достижения — Комната
        "Стартовая Локация" to R.string.sys_ach_room_1_title,
        "Гроза Пылевых Бурь" to R.string.sys_ach_room_5_title,
        "Архитектор Уюта" to R.string.sys_ach_room_10_title,
        "Анти-Хаос Щит" to R.string.sys_ach_room_20_title,
        "Мастер Скрытности" to R.string.sys_ach_room_30_title,
        "Генерал Чистоты" to R.string.sys_ach_room_40_title,
        "Властелин Квадратных Метров" to R.string.sys_ach_room_50_title,
        // Служебные причины штрафов дневника
        "Оценка 3 (дневник)" to R.string.sys_reason_grade3_diary,
        "Оценка 2 (дневник)" to R.string.sys_reason_grade2_diary,
        // Legacy-варианты (записи до переименования «папа» → «родитель»)
        "Оценка 3" to R.string.sys_reason_grade3,
        "Оценка 2" to R.string.sys_reason_grade2,
        // Дефолтный комментарий вывода — в истории и заявках
        "Вывод наличных" to R.string.cashout_default_comment
    )

    /** Системные описания: встроенные квесты + достижения. */
    private val descRes = mapOf(
        // Встроенные квесты (актуальные + legacy-тексты «для папы»)
        "Наведи идеальный порядок на полках и сфотографируй результат для родителя" to R.string.sys_quest_wardrobe_desc,
        "Наведи идеальный порядок на полках и сфотографируй результат для папы" to R.string.sys_quest_wardrobe_desc,
        "Заправь кровать без складок, собери игрушки и пришли фото" to R.string.sys_quest_room_desc,
        "Квест выполняется раз в неделю. Родитель проверяет все оценки в дневнике за прошедшую неделю." to R.string.sys_quest_diary_desc,
        "Квест выполняется раз в неделю. Папа проверяет все оценки в дневнике за прошедшую неделю." to R.string.sys_quest_diary_desc,
        // Достижения — Дневник
        "Нанес первый удар по скучной учебе. Дальше — больше." to R.string.sys_ach_diary_1_desc,
        "Выбил критический урон по школьной программе." to R.string.sys_ach_diary_5_desc,
        "Десять пятёрок в бортовом журнале — твой уровень знаний выходит на орбиту!" to R.string.sys_ach_diary_10_desc,
        // Legacy-описание до смены формулировки
        "Твоя аура отличника отпугивает двойки. Списать у тебя — платный DLC." to R.string.sys_ach_diary_10_desc,
        "Учителя подозревают, что ты скачал взломанную версию учебника." to R.string.sys_ach_diary_20_desc,
        "Разработчики школы пытались тебя понерфить, но ты всё равно тащишь катку." to R.string.sys_ach_diary_30_desc,
        "Проходишь сложные темы на уровне «Кошмар» с закрытыми глазами." to R.string.sys_ach_diary_40_desc,
        "Твоё имя пора выбить золотыми буквами на доске почета. Главный киберспортсмен класса." to R.string.sys_ach_diary_50_desc,
        // Достижения — Шкаф
        "Нашел давно потерянный носок и навел порядок." to R.string.sys_ach_wardrobe_1_desc,
        "Заглянул в самые темные углы шкафа и не испугался." to R.string.sys_ach_wardrobe_5_desc,
        "Укладываешь футболки и джинсы так плотно, что позавидует любой грузчик." to R.string.sys_ach_wardrobe_10_desc,
        "В твоем лутбоксе с одеждой теперь идеальная сортировка по цвету и редкости." to R.string.sys_ach_wardrobe_20_desc,
        "Навел такую чистоту, что за вешалками открылся проход в волшебную страну." to R.string.sys_ach_wardrobe_30_desc,
        "Ни одна вещь не прогружается на полу. Всё строго на своих полках." to R.string.sys_ach_wardrobe_40_desc,
        "Твой шкаф выглядит как витрина дорогого магазина. Футболки лежат под линейку." to R.string.sys_ach_wardrobe_50_desc,
        // Достижения — Комната
        "Зачистил базу от разбросанных игрушек и вражеских фантиков." to R.string.sys_ach_room_1_desc,
        "Нанес сокрушительный удар по хаосу. Кровать заправлена без единой складки." to R.string.sys_ach_room_5_desc,
        "Твоя комната прошла проверку санэпидемстанции и папиного строгого взгляда." to R.string.sys_ach_room_10_desc,
        "Включил пассивный навык чистоты: вещи сами боятся падать мимо корзины." to R.string.sys_ach_room_20_desc,
        "Убрал всё так быстро и чисто, будто здесь вообще никто не живет." to R.string.sys_ach_room_30_desc,
        "Твоя комната сияет ярче, чем броня паладина. Враг в лице беспорядка разбит." to R.string.sys_ach_room_40_desc,
        "Абсолютный контроль территории. Эту комнату пора снимать для обложки журнала." to R.string.sys_ach_room_50_desc
    )

    fun titleResId(text: String): Int? = titleRes[text]
    fun descResId(text: String): Int? = descRes[text]
}

/** Системный заголовок (квест/достижение/служебная строка) с переводом; иначе — исходный текст. */
@Composable
fun systemTitle(text: String): String =
    SystemContent.titleResId(text)?.let { stringResource(it) } ?: text

/** Системное описание (квест/достижение) с переводом; иначе — исходный текст. */
@Composable
fun systemDesc(text: String): String =
    SystemContent.descResId(text)?.let { stringResource(it) } ?: text

/** Комментарий заявки на вывод: дефолтный «Вывод наличных» переводится, пользовательский — как есть. */
@Composable
fun cashoutCommentDisplay(comment: String): String =
    if (comment == SystemContent.DEFAULT_CASHOUT_COMMENT) {
        stringResource(R.string.cashout_default_comment)
    } else {
        comment
    }
