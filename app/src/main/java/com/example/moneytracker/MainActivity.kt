package com.example.moneytracker

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    private lateinit var etTotalAmount: EditText
    private lateinit var expensesContainer: LinearLayout
    private lateinit var mainLayout: View

    private val expenseItems = mutableListOf<ExpenseItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etTotalAmount = findViewById(R.id.etTotalAmount)
        expensesContainer = findViewById(R.id.expensesContainer)
        mainLayout = findViewById(R.id.mainLayout)

        findViewById<ImageButton>(R.id.btnAddExpense).setOnClickListener { addExpenseItem() }
        mainLayout.setOnClickListener { hideKeyboard() }

        addExpenseItem()
    }

    private fun addExpenseItem() {
        val view = LayoutInflater.from(this).inflate(R.layout.item_expense, expensesContainer, false)
        val etExpenseAmount = view.findViewById<EditText>(R.id.etExpenseAmount)
        val etExpenseDescription = view.findViewById<EditText>(R.id.etExpenseDescription)
        val btnRemoveExpense = view.findViewById<ImageButton>(R.id.btnRemoveExpense)

        val item = ExpenseItem(etExpenseAmount, etExpenseDescription, view)
        expenseItems.add(item)

        etExpenseAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateTotalAmount() }
        })

        btnRemoveExpense.setOnClickListener {
            if (expenseItems.size > 1) {
                expensesContainer.removeView(view)
                expenseItems.remove(item)
                updateTotalAmount()
            }
        }

        expensesContainer.addView(view)
    }

    private fun updateTotalAmount() {
        var total = 0.0
        for (item in expenseItems) {
            val t = item.amountEditText.text.toString()
            total += t.toDoubleOrNull() ?: 0.0
        }
        etTotalAmount.setText(String.format("%.2f", total))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    data class ExpenseItem(
        val amountEditText: EditText,
        val descriptionEditText: EditText,
        val view: View
    )
}
