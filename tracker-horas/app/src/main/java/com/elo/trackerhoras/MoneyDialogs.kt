package com.elo.trackerhoras

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import java.util.Locale

/** Diálogos de dinero, compartidos por la app y la pantalla de bloqueo. */
object MoneyDialogs {

    private fun field(a: Activity, hint: String, value: Double?, signed: Boolean): Pair<LinearLayout, EditText> {
        val input = EditText(a)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
        input.hint = hint
        if (value != null && value > 0.0) {
            input.setText(String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.'))
            input.setSelection(input.text.length)
        }
        val box = LinearLayout(a)
        val pad = (22 * a.resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 3, pad, 0)
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return box to input
    }

    /** Escribir el total correcto de hoy (reemplaza lo que había). */
    fun editToday(a: Activity, onDone: () -> Unit) {
        val current = Store.moneyToday(a)
        val (box, input) = field(a, "Total ganado hoy, ej: 85", current, signed = false)
        AlertDialog.Builder(a)
            .setTitle("Dinero de hoy (S/)")
            .setMessage("Escribe el total correcto de hoy. Reemplaza el monto anterior.")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val v = parseSoles(input.text.toString()) ?: 0.0
                Store.setMoney(a, Store.today(), v)
                Notifier.update(a)
                onDone()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Sumar un monto a lo de hoy. */
    fun addToday(a: Activity, onDone: () -> Unit) {
        val (box, input) = field(a, "Ej: 85 (usa -20 para restar)", null, signed = true)
        AlertDialog.Builder(a)
            .setTitle("Sumar dinero de hoy (S/)")
            .setView(box)
            .setPositiveButton("Sumar") { _, _ ->
                val v = parseSoles(input.text.toString())
                if (v != null) {
                    Store.addMoney(a, Store.today(), v)
                    Notifier.update(a)
                    onDone()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
