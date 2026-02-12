package com.example.ourmemories.Utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.WindowManager
import android.widget.Button
import android.widget.NumberPicker
import com.example.ourmemories.R

import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

object DatePickerHelper {

    /**
     * Показывает BottomSheetDialog с выбором даты.
     *
     * @param context Контекст (обычно requireContext()).
     * @param initialTimestamp (Опционально) Время в мс, которое нужно показать при открытии.
     * @param onDateSelected
     */
    fun showDatePicker(
        context: Context,
        initialTimestamp: Long? = null,
        onDateSelected: (dateString: String, timestamp: Long) -> Unit
    ) {
        val dialog = BottomSheetDialog(
            context, R.style.Base_Theme_OurMemories
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.window?.let { window ->
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attributes = window.attributes
                attributes.blurBehindRadius = 60
                window.attributes = attributes
            }
        }
        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay) ?: return
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth) ?: return
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear) ?: return
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate) ?: return

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        if (initialTimestamp != null && initialTimestamp > 0) {
            calendar.timeInMillis = initialTimestamp
        } else {
            calendar.set(Calendar.YEAR, 2000)
            calendar.set(Calendar.MONTH, 0)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
        }

        npYear.minValue = 1900
        npYear.maxValue = currentYear
        npYear.value = calendar.get(Calendar.YEAR)
        npYear.wrapSelectorWheel = false

        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0
        npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months
        npMonth.value = calendar.get(Calendar.MONTH)

        npDay.minValue = 1
        npDay.maxValue = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value)
            cal.set(Calendar.MONTH, npMonth.value)
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            npDay.maxValue = maxDays
            if (npDay.value > maxDays) {
                npDay.value = maxDays
            }
        }

        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }

        btnConfirm.setOnClickListener {
            val selectedCal = Calendar.getInstance()
            selectedCal.set(Calendar.YEAR, npYear.value)
            selectedCal.set(Calendar.MONTH, npMonth.value)
            selectedCal.set(Calendar.DAY_OF_MONTH, npDay.value)

            @SuppressLint("DefaultLocale") val dateString = String.format(
                "%02d.%02d.%d", npDay.value, npMonth.value + 1, npYear.value
            )

            onDateSelected(dateString, selectedCal.timeInMillis)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}