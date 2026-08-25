package com.hairclinic.app.ui.revenue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.RevenueDay
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
    private var selectedDay: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRevenueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.prevMonthBtn.setOnClickListener {
            shiftMonth(-1)
            load()
        }
        binding.nextMonthBtn.setOnClickListener {
            shiftMonth(1)
            load()
        }
        binding.refreshBtn.setOnClickListener { load() }
        binding.monthSummaryBtn.setOnClickListener {
            selectedDay = null
            summary?.let { showMonth(it) }
            renderCalendar()
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) load()
    }

    private fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        cal.add(Calendar.MONTH, delta)
        year = cal.get(Calendar.YEAR)
        month = cal.get(Calendar.MONTH) + 1
        selectedDay = null
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val data = ApiClient.get(requireContext()).revenueSummary(year, month)
                year = data.year
                month = data.month
                summary = data
                if (selectedDay != null && data.days.none { it.day == selectedDay }) {
                    selectedDay = null
                }
                binding.monthTitle.text = "${data.year}年${data.month}月"
                binding.pageSubtitle.text = "${data.year}年${data.month}月 · 月历"
                renderCalendar()
                if (selectedDay == null) showMonth(data) else {
                    val day = data.days.firstOrNull { it.day == selectedDay }
                    if (day != null) showDay(day) else showMonth(data)
                }
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
                val revenue = day?.revenue ?: 0.0
                cell.dayAmount.text = if (revenue > 0) shortMoney(revenue) else ""
                val selected = selectedDay == dayNum
                cell.dayCell.setBackgroundResource(
                    if (selected) R.drawable.bg_chip else R.drawable.bg_form_panel,
                )
                cell.dayNumber.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.paper2 else R.color.ink,
                    ),
                )
                cell.dayAmount.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.paper2 else if (revenue > 0) R.color.pine else R.color.ink_soft,
                    ),
                )
                cell.dayCell.setOnClickListener {
                    selectedDay = dayNum
                    if (day != null) showDay(day) else {
                        showDay(
                            RevenueDay(
                                date = "%04d-%02d-%02d".format(data.year, data.month, dayNum),
                                day = dayNum,
                                revenue = 0.0,
                                order_count = 0,
                                cost = 0.0,
                                inbound_count = 0,
                                profit = 0.0,
                            ),
                        )
                    }
                    renderCalendar()
                }
            }
            binding.calendarGrid.addView(cell.root)
        }
    }

    private fun showMonth(data: RevenueSummary) {
        binding.scopeTitle.text = "本月汇总"
        binding.monthSummaryBtn.isVisible = false
        bindNumbers(data.revenue, data.order_count, data.cost, data.inbound_count, data.profit)
    }

    private fun showDay(day: RevenueDay) {
        binding.scopeTitle.text = "${day.date} 当日"
        binding.monthSummaryBtn.isVisible = true
        bindNumbers(day.revenue, day.order_count, day.cost, day.inbound_count, day.profit)
    }

    private fun bindNumbers(
        revenue: Double,
        orderCount: Int,
        cost: Double,
        inboundCount: Int,
        profit: Double,
    ) {
        binding.revenueText.text = money(revenue)
        binding.costText.text = money(cost)
        binding.profitText.text = money(profit)
        binding.orderCountText.text = "订单 $orderCount 笔"
        binding.inboundCountText.text = "入库 $inboundCount 笔"
    }

    private fun money(value: Double): String = "¥${ProjectEditFragment.formatPrice(value)}"

    private fun shortMoney(value: Double): String {
        return if (value >= 10000) {
            String.format("%.1fw", value / 10000.0)
        } else if (value >= 1000) {
            String.format("%.1fk", value / 1000.0)
        } else {
            ProjectEditFragment.formatPrice(value)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
