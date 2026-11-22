package com.example.familibalance.data.push

import android.content.Context
import android.util.Log
import com.example.familibalance.R
import com.example.familibalance.util.SystemContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Локализация пушей на устройстве получателя.
 *
 * Отправитель передаёт в data-payload ключи строк (`title_key`/`body_key` —
 * имена string-ресурсов) и аргументы (`title_args`/`body_args` — JSON-массив
 * строк). Здесь ключ резолвится через [Context.getString] в локали устройства
 * получателя, поэтому язык пуша не зависит от локали отправителя.
 *
 * Аргументы, совпадающие с системными строками (названия встроенных квестов
 * и достижений), дополнительно переводятся через [SystemContent] —
 * пользовательский контент остаётся как есть.
 *
 * Fallback: если ключа нет или он неизвестен — используется текстовый
 * `title`/`body` (обратная совместимость со старыми отправителями).
 */
object PushTexts {

    private val json = Json { ignoreUnknownKeys = true }

    /** Имя ключа → string-res. Ключи совпадают с именами в strings.xml. */
    private val resIds = mapOf(
        "push_photo_report_title" to R.string.push_photo_report_title,
        "push_photo_report_body" to R.string.push_photo_report_body,
        "push_cashout_req_title" to R.string.push_cashout_req_title,
        "push_cashout_req_body" to R.string.push_cashout_req_body,
        "push_honest_sub_title" to R.string.push_honest_sub_title,
        "push_honest_sub_body" to R.string.push_honest_sub_body,
        "push_dream_title" to R.string.push_dream_title,
        "push_dream_body" to R.string.push_dream_body,
        "push_quest_ok_title" to R.string.push_quest_ok_title,
        "push_quest_ok_body" to R.string.push_quest_ok_body,
        "push_quest_rej_title" to R.string.push_quest_rej_title,
        "push_quest_rej_body" to R.string.push_quest_rej_body,
        "push_cashout_ok_title" to R.string.push_cashout_ok_title,
        "push_cashout_ok_body" to R.string.push_cashout_ok_body,
        "push_cashout_rej_title" to R.string.push_cashout_rej_title,
        "push_cashout_rej_body" to R.string.push_cashout_rej_body,
        "push_honest_rej_title" to R.string.push_honest_rej_title,
        "push_honest_rej_body" to R.string.push_honest_rej_body,
        "push_bonus_quest_title" to R.string.push_bonus_quest_title,
        "push_bonus_quest_body" to R.string.push_bonus_quest_body,
        "push_balance_title" to R.string.push_balance_title,
        "push_balance_body" to R.string.push_balance_body
    )

    fun resId(key: String): Int? = resIds[key]

    /** JSON-массив строк из data-payload → List<String>; при ошибке — пустой список. */
    fun parseArgs(argsJson: String?): List<String> {
        if (argsJson.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(argsJson).jsonArray.map { it.jsonPrimitive.content }
        }.getOrElse {
            Log.w("PushTexts", "bad args json: $argsJson")
            emptyList()
        }
    }

    /** Аргумент с системным текстом (квест/достижение) → перевод; иначе как есть. */
    private fun localizeArg(context: Context, arg: String): String =
        SystemContent.titleResId(arg)?.let { context.getString(it) } ?: arg

    /**
     * Резолвит ключ + аргументы в строку. Возвращает null, если ключ
     * неизвестен — вызывающий код падает на текстовый fallback.
     */
    fun resolve(context: Context, key: String?, argsJson: String?): String? {
        if (key.isNullOrBlank()) return null
        val resId = resIds[key] ?: return null
        val args = parseArgs(argsJson).map { localizeArg(context, it) }.toTypedArray()
        return runCatching { context.getString(resId, *args) }.getOrNull()
    }
}
