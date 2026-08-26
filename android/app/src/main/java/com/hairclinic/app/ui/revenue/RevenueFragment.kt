package com.hairclinic.app.ui.revenue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.RevenueSummary
import com.hairclinic.app.databinding.FragmentRevenueBinding
import com.hairclinic.app.databinding.ItemCalendarDayBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch
import java.util.Calendar

class RevenueFragment : Fragment() {
    private var _binding: FragmentRevenueBinding? = null
    private val binding get() = _binding!!

    private var year: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    private var summary: RevenueSummary? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRevenueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.monthPicker.setOnClickListener { pickMonth() }
        binding.refreshBtn.setOnClickListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) load()
    }

    private fun pickMonth() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_month_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPickerWheel = dialogView.findViewById<NumberPicker>(R.id.monthPickerWheel)
        yearPicker.minValue = 2000
        yearPicker.maxValue = 2100
        yearPicker.value = year
        yearPicker.wrapSelectorWheel = false
        monthPickerWheel.minValue = 1
        monthPickerWheel.maxValue = 12
        monthPickerWheel.displayedValues = (1..12).map { "${it}月" }.toTypedArray()
        monthPickerWheel.value = month
        monthPickerWheel.wrapSelectorWheel = false
        AlertDialog.Builder(requireContext())
            .setTitle("选择月份")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                year = yearPicker.value
                month = monthPickerWheel.value
                load()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val data = ApiClient.get(requireContext()).revenueSummary(year, month)
                year = data.year
                month = data.month
                summary = data
                binding.monthPicker.text = "${data.year}年${data.month}月 · 月历"
                showMonth(data)
                renderCalendar()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    ProjectEditFragment.apiError(e, "加载失败"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun renderCalendar() {
        val data = summary ?: return
        binding.calendarGrid.removeAllViews()
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, data.year)
        cal.set(Calendar.MONTH, data.month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        // Monday-first: Calendar.SUNDAY=1 ... convert to 0=Mon
        val firstDow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayMap = data.days.associateBy { it.day }
        val totalCells = ((firstDow + daysInMonth + 6) / 7) * 7

        for (i in 0 until totalCells) {
            val cell = ItemCalendarDayBinding.inflate(layoutInflater, binding.calendarGrid, false)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 7, 1f)
                rowSpec = GridLayout.spec(i / 7)
                setMargins(2, 2, 2, 2)
            }
            cell.root.layoutParams = params

            val dayNum = i - firstDow + 1
            if (dayNum !in 1..daysInMonth) {
                cell.dayNumber.text = ""
                cell.dayAmount.text = ""
                cell.dayCell.alpha = 0.25f
                cell.dayCell.isClickable = false
            } else {
                val day = dayMap[dayNum]
                cell.dayNumber.text = dayNum.toString()
                val profit = day?.profit ?: 0.0
                cell.dayAmount.text = shortMoney(profit)
                cell.dayNumber.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink))
                cell.dayAmount.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        when {
                            profit > 0 -> R.color.gold
                            profit < 0 -> R.color.cinnabar
                            else -> R.color.ink_soft
                        },
                    ),
                )
                cell.dayCell.isClickable = false
            }
            binding.calendarGrid.addView(cell.root)
        }
    }

    private fun showMonth(data: RevenueSummary) {
        binding.scopeTitle.text = "本月汇总"
        bindNumbers(data.revenue, data.order_count, data.cost, data.inbound_count, data.profit)
    }

    private fun bindNumbers(
        revenue: Double,
        orderCount: Int,
        cost: Double,
        inboundCount: Int,
        profit: Double,
    ) {
        binding.revenueText.text = money(revenue)
        binding.costText.text = costMoney(cost)
        binding.profitText.text = money(profit)
        binding.orderCountText.text = "订单 $orderCount 笔"
        binding.inboundCountText.text = if (inboundCount > 0) "入库 $inboundCount 笔" else "按进货价"
    }

    private fun money(value: Double): String = ProjectEditFragment.formatPrice(value)

    private fun costMoney(value: Double): String {
        if (value == 0.0) return "0"
        return "-${money(value)}"
    }

    private fun shortMoney(value: Double): String {
        val abs = kotlin.math.abs(value)
        val body = when {
            abs >= 10000 -> String.format("%.1fw", abs / 10000.0)
            abs >= 1000 -> String.format("%.1fk", abs / 1000.0)
            else -> ProjectEditFragment.formatPrice(abs)
        }
        return if (value < 0) "-$body" else body
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
