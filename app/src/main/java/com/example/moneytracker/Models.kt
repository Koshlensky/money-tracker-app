package com.example.moneytracker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/* ======================= Валюты ======================= */

enum class Currency(val symbol: String) {
    USD("$"),
    JPY("¥"),
    RUB("₽")
}

/* ======================= Модели ======================= */

data class Expense(
    var amountCents: Long = 0,
    var note: String = ""
)

data class Person(
    val id: Long,
    var name: String = "",
    var baseTotalCents: Long = 0,
    val expenses: MutableList<Expense> = mutableListOf()
) {
    fun spentCents() = expenses.sumOf { it.amountCents }
    fun remainingCents() = baseTotalCents - spentCents()
}

/* ======================= Хранилище (встроили сюда) ======================= */

private const val PREFS = "moneytracker_prefs"
private const val KEY_PEOPLE = "people_json"
private const val KEY_CCY = "currency"

private object StorageInline {
    private val gson = Gson()

    fun savePeople(context: Context, people: List<Person>) {
        val json = gson.toJson(people)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PEOPLE, json)
            .apply()
    }

    fun loadPeople(context: Context): MutableList<Person> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PEOPLE, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Person>>() {}.type
            gson.fromJson<MutableList<Person>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveCurrency(context: Context, currency: Currency) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CCY, currency.name).apply()
    }

    fun loadCurrency(context: Context): Currency? {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CCY, null) ?: return null
        return runCatching { Currency.valueOf(name) }.getOrNull()
    }
}

/* ======================= Репозиторий ======================= */

object PersonsRepo {
    private val seq = AtomicLong(1)
    val people: MutableList<Person> = mutableListOf()

    /** Текущая валюта хранения */
    var currentCurrency: Currency = Currency.USD
        private set

    fun load(context: Context) {
        if (people.isNotEmpty()) return
        val loaded = StorageInline.loadPeople(context)
        if (loaded.isEmpty()) {
            people += Person(id = seq.getAndIncrement())
        } else {
            people.clear(); people.addAll(loaded)
            val maxId = people.maxOfOrNull { it.id } ?: 0L
            seq.set(maxId + 1)
        }
        StorageInline.loadCurrency(context)?.let { c -> currentCurrency = c }
    }

    fun save(context: Context) {
        StorageInline.savePeople(context, people)
        StorageInline.saveCurrency(context, currentCurrency)
    }

    fun addPerson(context: Context): Person {
        val p = Person(id = seq.getAndIncrement())
        people += p
        save(context)
        return p
    }

    fun update(context: Context, person: Person) {
        val i = people.indexOfFirst { it.id == person.id }
        if (i >= 0) people[i] = person
        save(context)
    }

    fun find(id: Long) = people.first { it.id == id }

    fun remove(context: Context, id: Long) {
        people.removeAll { it.id == id }
        save(context)
    }

    /**
     * Конвертирует ВСЕ суммы из currentCurrency -> targetCurrency.
     * @param factor коэффициент умножения (например, USD->JPY ~ 156)
     */
    fun convertAll(context: Context, target: Currency, factor: Double) {
        if (target == currentCurrency) return
        fun convert(v: Long): Long = (v.toDouble() * factor).roundToLong()

        people.forEach { person ->
            person.baseTotalCents = convert(person.baseTotalCents)
            person.expenses.forEach { it.amountCents = convert(it.amountCents) }
        }
        currentCurrency = target
        save(context)
    }
}

/* ======================= Утилиты денег ======================= */

fun parseUsdToCents(text: String) =
    (text.replace(',', '.').replace(Regex("[^0-9.]"), "")
        .toDoubleOrNull() ?: 0.0).let { Math.round(it * 100) }

fun centsToText(c: Long, currency: Currency) =
    when (currency) {
        Currency.USD -> "${Currency.USD.symbol}${"%.2f".format(c / 100.0)}"
        Currency.JPY -> "${Currency.JPY.symbol}${c}"
        Currency.RUB -> "${Currency.RUB.symbol}${"%.2f".format(c / 100.0)}"
    }
