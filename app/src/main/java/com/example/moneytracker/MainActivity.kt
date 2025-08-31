package com.example.moneytracker

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var fab: FloatingActionButton
    private val adapter = PeopleAdapter { personId ->
        startActivity(Intent(this, ExpensesActivity::class.java).putExtra("personId", personId))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rv = findViewById(R.id.rvPeople)
        fab = findViewById(R.id.fabAddPerson)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        adapter.submit(PersonsRepo.people)

        fab.setOnClickListener {
            PersonsRepo.addPerson()
            adapter.submit(PersonsRepo.people)
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(PersonsRepo.people) // обновить остаток после трат
    }
}

private class PeopleAdapter(
    val onOpen: (Long) -> Unit
) : RecyclerView.Adapter<PeopleVH>() {

    private var items: List<Person> = emptyList()
    fun submit(list: List<Person>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return PeopleVH(v, onOpen)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: PeopleVH, position: Int) = holder.bind(items[position])
}

private class PeopleVH(
    v: View,
    private val onOpen: (Long) -> Unit
) : RecyclerView.ViewHolder(v) {

    private val etName: EditText = v.findViewById(R.id.etName)
    private val etBase: EditText = v.findViewById(R.id.etBaseTotal)
    private val tvRemaining: TextView = v.findViewById(R.id.tvRemaining)
    private val clickAway: View = v.findViewById(R.id.clickAway)

    fun bind(p: Person) {
        etName.setText(p.name)
        etBase.setText(if (p.baseTotalCents == 0L) "" else (p.baseTotalCents / 100.0).toString())
        tvRemaining.text = "Осталось: ${centsToUsdText(p.remainingCents())}"

        etName.addTextChangedListener(simpleWatcher { text ->
            p.name = text
            PersonsRepo.update(p)
        })
        etBase.addTextChangedListener(simpleWatcher { text ->
            p.baseTotalCents = parseUsdToCents(text)
            PersonsRepo.update(p)
            tvRemaining.text = "Осталось: ${centsToUsdText(p.remainingCents())}"
        })

        // тап по карточке (вне полей) — открываем траты
        itemView.setOnClickListener { /* глушим общий клик */ }
        clickAway.setOnClickListener { onOpen(p.id) }
        // также откроем по клику на фон, если поля не в фокусе
        itemView.setOnTouchListener { _, _ ->
            if (!etName.isFocused && !etBase.isFocused) {
                onOpen(p.id); true
            } else false
        }
    }

    private fun simpleWatcher(onAfter: (String) -> Unit) =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onAfter(s?.toString().orEmpty()) }
        }
}
