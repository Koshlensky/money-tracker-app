package com.example.moneytracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.PopupMenu
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

class ExpensesActivity : AppCompatActivity() {

    private lateinit var person: Person
    private lateinit var expensesContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnCurrency: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenses)

        val id = intent.getLongExtra("personId", -1)
        person = PersonsRepo.find(id)

        tvTitle = findViewById(R.id.tvTitle)
        expensesContainer = findViewById(R.id.expensesContainer)
        val btnAdd: FloatingActionButton = findViewById(R.id.btnAddExpense)
        val btnBack: FloatingActionButton = findViewById(R.id.btnBack)
        btnCurrency = findViewById(R.id.btnCurrencyExp)

        setTitleText()
        refreshList()

        btnAdd.setOnClickListener {
            person.expenses.add(Expense())
            PersonsRepo.update(this, person)
            refreshList()
        }
        btnBack.setOnClickListener { finish() }
        btnCurrency.setOnClickListener { showCurrencyMenu(it) }
    }

    private fun setTitleText() {
        val name = if (person.name.isBlank()) "без имени" else person.name
        tvTitle.text = "Окно трат — $name\nОсталось: ${centsToText(person.remainingCents(), PersonsRepo.currentCurrency)}"
    }

    private fun refreshList() {
        expensesContainer.removeAllViews()

        person.expenses.forEachIndexed { index, exp ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_expense, expensesContainer, false)

            val tvAmountLabel = v.findViewById<TextView>(R.id.tvAmountLabel)
            val etAmount = v.findViewById<EditText>(R.id.etExpenseAmount)
            val etNote = v.findViewById<EditText>(R.id.etExpenseDescription)
            val btnDel = v.findViewById<ImageButton>(R.id.btnRemoveExpense)

            // динамический лейбл с символом валюты
            val symbol = when (PersonsRepo.currentCurrency) {
                Currency.USD -> "$"
                Currency.JPY -> "¥"
                Currency.RUB -> "₽"
            }
            tvAmountLabel.text = "Сумма траты ($symbol)"

            // Показ значения с учетом валюты
            etAmount.setText(
                if (exp.amountCents == 0L) "" else when (PersonsRepo.currentCurrency) {
                    Currency.JPY -> exp.amountCents.toString()
                    else -> "%.2f".format(exp.amountCents / 100.0)
                }
            )
            etNote.setText(exp.note)

            etAmount.addTextChangedListener(simpleWatcher { text ->
                exp.amountCents = when (PersonsRepo.currentCurrency) {
                    Currency.JPY -> text.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    else -> parseUsdToCents(text)
                }
                PersonsRepo.update(this, person)
                setTitleText()
            })
            etNote.addTextChangedListener(simpleWatcher { text ->
                exp.note = text
                PersonsRepo.update(this, person)
            })

            btnDel.setOnClickListener {
                if (person.expenses.size > 1) {
                    person.expenses.removeAt(index)
                    PersonsRepo.update(this, person)
                    refreshList()
                }
            }

            expensesContainer.addView(v)
        }
    }

    private fun showCurrencyMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "USD ($)")
        menu.menu.add(0, 2, 1, "JPY (¥)")
        menu.menu.add(0, 3, 2, "RUB (₽)")
        menu.setOnMenuItemClickListener { item ->
            val target = when (item.itemId) {
                1 -> Currency.USD
                2 -> Currency.JPY
                3 -> Currency.RUB
                else -> return@setOnMenuItemClickListener false
            }
            convertTo(target); true
        }
        menu.show()
    }

    private fun convertTo(target: Currency) {
        if (target == PersonsRepo.currentCurrency) return
        Toast.makeText(this, "Получаем курс…", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val rates = CurrencyManagerInline.fetchUsdRates()
                val factor = CurrencyManagerInline.factor(
                    current = PersonsRepo.currentCurrency,
                    target = target,
                    usdRates = rates
                )
                PersonsRepo.convertAll(this, target, factor)

                Handler(Looper.getMainLooper()).post {
                    setTitleText()
                    refreshList()
                    Toast.makeText(this, "Сконвертировано в ${target.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Ошибка курса: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun simpleWatcher(onAfter: (String) -> Unit) =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onAfter(s?.toString().orEmpty()) }
        }
}
