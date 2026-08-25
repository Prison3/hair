package com.hairclinic.app.ui.customers

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.data.CustomerVisit
import com.hairclinic.app.data.Order
import com.hairclinic.app.data.formatVisitTime
import com.hairclinic.app.databinding.DialogVisitBinding
import com.hairclinic.app.databinding.FragmentCustomerEditBinding
import com.hairclinic.app.databinding.ItemVisitBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class CustomerEditFragment : Fragment() {
    private var _binding: FragmentCustomerEditBinding? = null
    private val binding get() = _binding!!
    private var customerId: Int = -1
    private val visits = mutableListOf<CustomerVisit>()
    private val orders = mutableListOf<Order>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomerEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        customerId = arguments?.getInt(ARG_ID, -1) ?: -1
        val isEdit = customerId > 0
        binding.pageTitle.text = if (isEdit) "编辑客户" else "添加客户"
        binding.visitSection.isVisible = isEdit
        binding.orderSection.isVisible = isEdit

        if (isEdit) {
            binding.inputName.setText(arguments?.getString(ARG_NAME).orEmpty())
            binding.inputPhone.setText(arguments?.getString(ARG_PHONE).orEmpty())
            binding.inputWechat.setText(arguments?.getString(ARG_WECHAT).orEmpty())
            binding.inputAddress.setText(arguments?.getString(ARG_ADDRESS).orEmpty())
            binding.inputNotes.setText(arguments?.getString(ARG_NOTES).orEmpty())
            when (arguments?.getString(ARG_GENDER)) {
                "男" -> binding.genderMale.isChecked = true
                "女" -> binding.genderFemale.isChecked = true
            }
            when (arguments?.getString(ARG_INTENTION)) {
                "高" -> binding.intentionHigh.isChecked = true
                "中" -> binding.intentionMid.isChecked = true
                "低" -> binding.intentionLow.isChecked = true
            }
            val birthday = arguments?.getString(ARG_BIRTHDAY).orEmpty()
            if (birthday.isNotBlank()) binding.inputBirthday.text = birthday
            loadVisits()
            loadOrders()
        }

        binding.inputBirthday.setOnClickListener { pickBirthday() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
        binding.addVisitBtn.setOnClickListener { showVisitDialog(null) }
    }

    private fun pickBirthday() {
        val cal = Calendar.getInstance()
        val current = binding.inputBirthday.text?.toString().orEmpty()
        if (current.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            val parts = current.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } else {
            cal.set(1995, 0, 1)
        }
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                binding.inputBirthday.text = "%04d-%02d-%02d".format(y, m + 1, d)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun loadVisits() {
        if (customerId <= 0) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = ApiClient.get(requireContext()).listVisits(customerId)
                visits.clear()
                visits.addAll(list)
                renderVisits()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "回访加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadOrders() {
        if (customerId <= 0) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = ApiClient.get(requireContext()).listCustomerOrders(customerId)
                orders.clear()
                orders.addAll(list)
                renderOrders()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "订单加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderOrders() {
        binding.orderBox.removeAllViews()
        binding.orderEmpty.isVisible = orders.isEmpty()
        orders.forEach { order ->
            val row = ItemVisitBinding.inflate(layoutInflater, binding.orderBox, false)
            val time = formatVisitTime(order.created_at)
            row.visitTime.text = "${order.order_no} · ${orderStatusLabel(order.status)}"
            val detail = order.items.joinToString(" · ") { "${it.project_name}×${it.quantity}" }
            row.visitContent.text = buildString {
                append("¥${"%.2f".format(order.total_amount)}")
                if (time.isNotBlank()) append(" · $time")
                append("\n")
                append(detail.ifBlank { "无项目" })
                if (order.remark.isNotBlank()) append("\n备注 ${order.remark}")
            }
            row.visitDelete.isVisible = false
            row.root.isClickable = false
            row.root.isFocusable = false
            binding.orderBox.addView(row.root)
        }
    }

    private fun orderStatusLabel(status: String): String = when (status) {
        "PENDING" -> "待付款"
        "PAID" -> "已付款"
        "DONE" -> "已完成"
        "CANCELLED" -> "已取消"
        else -> status
    }

    private fun renderVisits() {
        binding.visitBox.removeAllViews()
        binding.visitEmpty.isVisible = visits.isEmpty()
        visits.forEach { visit ->
            val row = ItemVisitBinding.inflate(layoutInflater, binding.visitBox, false)
            row.visitTime.text = visit.timeText()
            row.visitContent.text = visit.content.ifBlank { "无内容" }
            row.root.setOnClickListener { showVisitDialog(visit) }
            row.visitDelete.setOnClickListener { confirmDeleteVisit(visit) }
            binding.visitBox.addView(row.root)
        }
    }

    private fun showVisitDialog(visit: CustomerVisit?) {
        val dialogBinding = DialogVisitBinding.inflate(layoutInflater)
        val cal = parseVisitCal(visit?.visited_at)
        dialogBinding.visitTime.text = formatDisplayTime(cal)
        dialogBinding.visitContent.setText(visit?.content.orEmpty())
        dialogBinding.visitTime.setOnClickListener {
            pickDateTime(cal) { picked ->
                cal.timeInMillis = picked.timeInMillis
                dialogBinding.visitTime.text = formatDisplayTime(cal)
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (visit == null) "添加回访" else "编辑回访")
            .setView(dialogBinding.root)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val body = CustomerVisit(
                    id = visit?.id,
                    customer_id = customerId,
                    visited_at = toApiDateTime(formatDisplayTime(cal)),
                    content = dialogBinding.visitContent.text?.toString()?.trim().orEmpty(),
                )
                saveVisit(visit?.id, body)
            }
            .show()
    }

    private fun pickDateTime(initial: Calendar, onPicked: (Calendar) -> Unit) {
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        val picked = Calendar.getInstance()
                        picked.set(y, m, d, hour, minute, 0)
                        picked.set(Calendar.MILLISECOND, 0)
                        onPicked(picked)
                    },
                    initial.get(Calendar.HOUR_OF_DAY),
                    initial.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun saveVisit(visitId: Int?, body: CustomerVisit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                if (visitId != null && visitId > 0) api.updateVisit(customerId, visitId, body)
                else api.createVisit(customerId, body)
                Toast.makeText(requireContext(), "回访已保存", Toast.LENGTH_SHORT).show()
                loadVisits()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "保存回访失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteVisit(visit: CustomerVisit) {
        val id = visit.id ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除回访")
            .setMessage("确定删除 ${visit.timeText()} 这条回访？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        ApiClient.get(requireContext()).deleteVisit(customerId, id)
                        loadVisits()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val phone = binding.inputPhone.text?.toString()?.trim().orEmpty()
            .replace(" ", "")
            .replace("-", "")
        if (name.isBlank() || phone.isBlank()) {
            Toast.makeText(requireContext(), "请填写姓名和手机", Toast.LENGTH_SHORT).show()
            return
        }
        if (!PHONE_PATTERN.matches(phone)) {
            Toast.makeText(requireContext(), "请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
            return
        }
        val gender = when {
            binding.genderMale.isChecked -> "男"
            binding.genderFemale.isChecked -> "女"
            else -> ""
        }
        val intention = when {
            binding.intentionHigh.isChecked -> "高"
            binding.intentionMid.isChecked -> "中"
            binding.intentionLow.isChecked -> "低"
            else -> ""
        }
        val birthday = binding.inputBirthday.text?.toString()?.trim().orEmpty()
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
        val body = Customer(
            id = customerId.takeIf { it > 0 },
            name = name,
            phone = phone,
            gender = gender,
            birthday = birthday,
            wechat = binding.inputWechat.text?.toString()?.trim().orEmpty(),
            address = binding.inputAddress.text?.toString()?.trim().orEmpty(),
            intention = intention,
            notes = binding.inputNotes.text?.toString()?.trim().orEmpty(),
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                val saved = if (customerId > 0) api.updateCustomer(customerId, body) else api.createCustomer(body)
                customerId = saved.id ?: customerId
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                if (binding.visitSection.isVisible) {
                    findNavController().navigateUp()
                } else {
                    binding.pageTitle.text = "编辑客户"
                    binding.visitSection.isVisible = true
                    binding.orderSection.isVisible = true
                    loadVisits()
                    loadOrders()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val PHONE_PATTERN = Regex("""^1[3-9]\d{9}$""")

        const val ARG_ID = "customer_id"
        const val ARG_NAME = "name"
        const val ARG_PHONE = "phone"
        const val ARG_GENDER = "gender"
        const val ARG_BIRTHDAY = "birthday"
        const val ARG_WECHAT = "wechat"
        const val ARG_ADDRESS = "address"
        const val ARG_INTENTION = "intention"
        const val ARG_NOTES = "notes"

        fun args(customer: Customer? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, customer?.id ?: -1)
            putString(ARG_NAME, customer?.name.orEmpty())
            putString(ARG_PHONE, customer?.phone.orEmpty())
            putString(ARG_GENDER, customer?.gender.orEmpty())
            putString(ARG_BIRTHDAY, customer?.birthday.orEmpty())
            putString(ARG_WECHAT, customer?.wechat.orEmpty())
            putString(ARG_ADDRESS, customer?.address.orEmpty())
            putString(ARG_INTENTION, customer?.intention.orEmpty())
            putString(ARG_NOTES, customer?.notes.orEmpty())
        }

        fun parseVisitCal(raw: String?): Calendar {
            val cal = Calendar.getInstance()
            val text = formatVisitTime(raw)
            val match = Regex("""(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})""").find(text)
            if (match != null) {
                val (y, m, d, h, min) = match.destructured
                cal.set(y.toInt(), m.toInt() - 1, d.toInt(), h.toInt(), min.toInt(), 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            return cal
        }

        fun formatDisplayTime(cal: Calendar): String =
            "%04d-%02d-%02d %02d:%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
            )

        fun toApiDateTime(display: String): String {
            val t = display.trim().replace(' ', 'T')
            return if (t.length == 16) "$t:00" else t
        }
    }
}
