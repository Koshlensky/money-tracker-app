package com.example.moneytracker

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
        // обновляем остатки после возможных изменений в ExpensesActivity
        adapter.submit(PersonsRepo.people)
    }
}

/* ----------------------------- Adapter ----------------------------- */

private class PeopleAdapter(
    private val onOpenRaw: (Long) -> Unit
) : RecyclerView.Adapter<PeopleVH>() {

    private var items: List<Person> = emptyList()

    init {
        setHasStableIds(true) // стабильные ID уменьшают хаотичные ребинды
    }

    fun submit(list: List<Person>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
    override fun getItemId(position: Int): Long = items[position].id

    // ---- дебаунсер открытия, чтобы не плодить несколько активити ----
    private var lastOpenAt = 0L
    private fun openSafe(id: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastOpenAt < 500) return // игнорируем повтор в течение 0.5 c
        lastOpenAt = now
        onOpenRaw(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person, parent, false)
        return PeopleVH(v, ::openSafe)
    }

    override fun onBindViewHolder(holder: PeopleVH, position: Int) {
        holder.bind(items[position])
    }
}

/* ----------------------------- ViewHolder ----------------------------- */

private class PeopleVH(
    v: View,
    private val openSafe: (Long) -> Unit
) : RecyclerView.ViewHolder(v) {

    private val etName: EditText = v.findViewById(R.id.etName)
    private val etBase: EditText = v.findViewById(R.id.etBaseTotal)
    private val tvRemaining: TextView = v.findViewById(R.id.tvRemaining)
    private val clickAway: View = v.findViewById(R.id.clickAway)
    private val btnRemove: ImageButton = v.findViewById(R.id.btnRemovePerson)

    // держим текущую модель и ссылки на активные вотчеры
    private var current: Person? = null
    private var nameWatcher: TextWatcher? = null
    private var baseWatcher: TextWatcher? = null

    fun bind(p: Person) {
        current = p

        // 1) снимаем старые вотчеры перед setText(), чтобы они не сработали на чужую модель
        nameWatcher?.let { etName.removeTextChangedListener(it) }
        baseWatcher?.let { etBase.removeTextChangedListener(it) }

        // 2) выставляем значения
        etName.setText(p.name)
        etBase.setText(if (p.baseTotalCents == 0L) "" else (p.baseTotalCents / 100.0).toString())
        tvRemaining.text = "Осталось: ${centsToUsdText(p.remainingCents())}"

        // 3) навешиваем НОВЫЕ вотчеры, работающие с current
        nameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                current?.let {
                    it.name = s?.toString().orEmpty()
                    PersonsRepo.update(it)
                }
            }
        }.also { etName.addTextChangedListener(it) }

        baseWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                current?.let {
                    it.baseTotalCents = parseUsdToCents(s?.toString().orEmpty())
                    PersonsRepo.update(it)
                    tvRemaining.text = "Осталось: ${centsToUsdText(it.remainingCents())}"
                }
            }
        }.also { etBase.addTextChangedListener(it) }

        // ----- Открытие экрана трат: оставляем ОДИН способ -----
        itemView.setOnClickListener {
            if (!etName.isFocused && !etBase.isFocused) openSafe(p.id)
        }
        clickAway.setOnClickListener { openSafe(p.id) }
        // ВАЖНО: не используем setOnTouchListener — он мог вызывать дубли

        // ----- Удаление карточки -----
        btnRemove.setOnClickListener {
            AlertDialog.Builder(itemView.context)
                .setTitle("Удалить?")
                .setMessage("Удалить карточку «${if (p.name.isBlank()) "без имени" else p.name}»?")
                .setPositiveButton("Удалить") { _, _ ->
                    PersonsRepo.people.removeAll { it.id == p.id }
                    (itemView.parent as? RecyclerView)?.adapter?.notifyDataSetChanged()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}
