package com.example.moneytracker

import java.util.concurrent.atomic.AtomicLong

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

object PersonsRepo {
    private val seq = AtomicLong(1)
    val people = mutableListOf(Person(id = seq.getAndIncrement()))
    fun addPerson(): Person { val p = Person(id = seq.getAndIncrement()); people += p; return p }
    fun find(id: Long) = people.first { it.id == id }
    fun update(person: Person) { val i = people.indexOfFirst { it.id == person.id }; if (i >= 0) people[i] = person }
}

fun parseUsdToCents(text: String) =
    (text.replace(',', '.').replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0)
        .let { Math.round(it * 100) }

fun centsToUsdText(c: Long) = String.format("$%.2f", c / 100.0)
