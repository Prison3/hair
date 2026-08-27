package com.hairclinic.app.ui.customers

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.data.CustomerPhoto
import com.hairclinic.app.data.CustomerVisit
import com.hairclinic.app.data.Order
import com.hairclinic.app.data.Session
import com.hairclinic.app.data.StaffOption
import com.hairclinic.app.data.formatVisitTime
import com.hairclinic.app.databinding.DialogVisitBinding
import com.hairclinic.app.databinding.FragmentCustomerEditBinding
import com.hairclinic.app.databinding.ItemVisitBinding
import com.hairclinic.app.ui.billing.BillingFragment
import kotlinx.coroutines.launch
import java.util.Calendar

class CustomerEditFragment : Fragment() {
    private var _binding: FragmentCustomerEditBinding? = null
    private val binding get() = _binding!!
    private var customerId: Int = -1
    private val visits = mutableListOf<CustomerVisit>()
    private val orders = mutableListOf<Order>()
    private var staffOptions: List<StaffOption> = emptyList()
    private var selectedAssigneeId: Int? = null
    private lateinit var beforePhotoAdapter: CustomerPhotoAdapter
    private lateinit var afterPhotoAdapter: CustomerPhotoAdapter

    private val pickBeforePhotos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) uploadPhotos(PHOTO_BEFORE, uris)
    }
    private val pickAfterPhotos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) uploadPhotos(PHOTO_AFTER, uris)
    }

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
        binding.inputAssignee.keyListener = null
        applyAssigneeEditable(canEditAssignee())
        if (canEditAssignee()) {
            binding.inputAssignee.setOnItemClickListener { _, _, position, _ ->
                selectedAssigneeId = staffOptions.getOrNull(position)?.id
            }
        }
        loadStaffOptions()
        setupPhotoSection()

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
            selectedAssigneeId = arguments?.getInt(ARG_ASSIGNED_TO, -1)?.takeIf { it > 0 }
            showPhotoSection()
            loadVisits()
            loadOrders()
        }

        binding.inputBirthday.setOnClickListener { pickBirthday() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
        binding.addVisitBtn.setOnClickListener { showVisitDialog(null) }
        binding.billBtn.setOnClickListener { openBilling() }
        binding.billBtn.isVisible = isEdit
        binding.addBeforePhotoBtn.setOnClickListener { pickPhotos(PHOTO_BEFORE) }
        binding.addAfterPhotoBtn.setOnClickListener { pickPhotos(PHOTO_AFTER) }
    }

    private fun setupPhotoSection() {
        beforePhotoAdapter = CustomerPhotoAdapter { confirmDeletePhoto(it) }
        afterPhotoAdapter = CustomerPhotoAdapter { confirmDeletePhoto(it) }
        binding.beforePhotoList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.afterPhotoList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.beforePhotoList.adapter = beforePhotoAdapter
        binding.afterPhotoList.adapter = afterPhotoAdapter
    }

    private fun showPhotoSection() {
        binding.photoSection.isVisible = customerId > 0
        if (customerId > 0) loadPhotos()
    }

    private fun pickPhotos(kind: String) {
        if (customerId <= 0) {
            Toast.makeText(requireContext(), "请先保存客户", Toast.LENGTH_SHORT).show()
            return
        }
        if (kind == PHOTO_BEFORE) pickBeforePhotos.launch("image/*")
        else pickAfterPhotos.launch("image/*")
    }

    private fun loadPhotos() {
        if (customerId <= 0) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                val before = api.listCustomerPhotos(customerId, PHOTO_BEFORE)
                val after = api.listCustomerPhotos(customerId, PHOTO_AFTER)
                beforePhotoAdapter.submit(before)
                afterPhotoAdapter.submit(after)
                binding.beforePhotoEmpty.isVisible = before.isEmpty()
                binding.afterPhotoEmpty.isVisible = after.isEmpty()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "照片加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadPhotos(kind: String, uris: List<Uri>) {
        if (customerId <= 0) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                uris.forEach { uri ->
                    ApiClient.uploadCustomerPhoto(requireContext(), customerId, kind, uri)
                }
                Toast.makeText(requireContext(), "照片已上传", Toast.LENGTH_SHORT).show()
                loadPhotos()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "上传失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeletePhoto(photo: CustomerPhoto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除照片")
            .setMessage("确定删除这张照片？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> deletePhoto(photo) }
            .show()
    }

    private fun deletePhoto(photo: CustomerPhoto) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteCustomerPhoto(customerId, photo.id)
                loadPhotos()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "删除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun canEditAssignee(): Boolean = Session.isAdmin(requireContext())

    private fun applyAssigneeEditable(editable: Boolean) {
        binding.assigneeLayout.hint = if (editable) "归属业务员（可选）" else "归属业务员"
        binding.assigneeLayout.isEnabled = true
        binding.assigneeLayout.alpha = 1f
        binding.assigneeLayout.endIconMode = if (editable) {
            TextInputLayout.END_ICON_DROPDOWN_MENU
        } else {
            TextInputLayout.END_ICON_NONE
        }
        // 只读时不 disable，避免输入框变灰；拦截点击禁止下拉。
        binding.inputAssignee.isEnabled = true
        binding.inputAssignee.alpha = 1f
        binding.inputAssignee.keyListener = null
        binding.inputAssignee.setShowSoftInputOnFocus(false)
        binding.inputAssignee.isCursorVisible = false
        binding.inputAssignee.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink))
        binding.inputAssignee.isFocusable = editable
        binding.inputAssignee.isFocusableInTouchMode = editable
        binding.inputAssignee.isClickable = editable
        binding.inputAssignee.isLongClickable = editable
        if (editable) {
            binding.inputAssignee.setOnTouchListener(null)
        } else {
            binding.inputAssignee.setAdapter(null)
            binding.inputAssignee.setOnItemClickListener(null)
            binding.inputAssignee.setOnClickListener(null)
            binding.inputAssignee.setOnTouchListener { _, _ -> true }
        }
    }

    private fun loadStaffOptions() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                staffOptions = ApiClient.get(requireContext()).listCustomerStaffOptions()
                if (canEditAssignee()) {
                    val labels = staffOptions.map { it.label() }
                    binding.inputAssignee.setAdapter(
                        ArrayAdapter(requireContext(), R.layout.item_spinner, labels),
                    )
                }
                val presetId = selectedAssigneeId
                    ?: arguments?.getInt(ARG_ASSIGNED_TO, -1)?.takeIf { it > 0 }
                val preset = staffOptions.firstOrNull { it.id == presetId }
                if (preset != null) {
                    binding.inputAssignee.setText(preset.label(), false)
                    selectedAssigneeId = preset.id
                } else {
                    val presetName = arguments?.getString(ARG_ASSIGNED_TO_NAME).orEmpty()
                    val byName = staffOptions.firstOrNull { it.username == presetName }
                    if (byName != null) {
                        binding.inputAssignee.setText(byName.label(), false)
                        selectedAssigneeId = byName.id
                    } else if (customerId <= 0) {
                        val me = ApiClient.get(requireContext()).me()
                        val current = staffOptions.firstOrNull { it.id == me.id }
                            ?: staffOptions.firstOrNull { it.username == me.username }
                        if (current != null) {
                            binding.inputAssignee.setText(current.label(), false)
                            selectedAssigneeId = current.id
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载业务员失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openBilling() {
        if (customerId <= 0) {
            Toast.makeText(requireContext(), "请先保存客户", Toast.LENGTH_SHORT).show()
            return
        }
        findNavController().navigate(
            R.id.billingFragment,
            BillingFragment.args(
                customerId = customerId,
                name = binding.inputName.text?.toString()?.trim().orEmpty()
                    .ifBlank { arguments?.getString(ARG_NAME).orEmpty() },
                phone = binding.inputPhone.text?.toString()?.trim().orEmpty()
                    .ifBlank { arguments?.getString(ARG_PHONE).orEmpty() },
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && customerId > 0) {
            loadOrders()
            loadVisits()
            loadPhotos()
        }
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
            val creator = order.creatorText()
            row.visitContent.text = buildString {
                append("¥${"%.2f".format(order.total_amount)}")
                if (time.isNotBlank()) append(" · $time")
                if (creator.isNotBlank()) append(" · 下单 $creator")
                append("\n")
                append(detail.ifBlank { "无项目" })
                if (order.remark.isNotBlank()) append("\n备注 ${order.remark}")
            }
            row.visitDelete.text = "详情"
            row.visitDelete.setTextColor(ContextCompat.getColor(requireContext(), R.color.pine))
            row.visitDelete.isVisible = true
            row.visitDelete.setOnClickListener { showOrderDetail(order) }
            row.root.setOnClickListener { showOrderDetail(order) }
            binding.orderBox.addView(row.root)
        }
    }

    private fun showOrderDetail(order: Order) {
        val itemsText = order.items.joinToString("\n") {
            "· ${it.project_name} × ${it.quantity}（标价 ¥${"%.2f".format(it.unit_price)}）"
        }.ifBlank { "无项目" }
        val message = buildString {
            appendLine("订单号：${order.order_no}")
            appendLine("状态：${orderStatusLabel(order.status)}")
            appendLine("成交金额：¥${"%.2f".format(order.total_amount)}")
            appendLine("下单时间：${formatVisitTime(order.created_at).ifBlank { "—" }}")
            val creator = order.creatorText()
            appendLine("下单账号：${creator.ifBlank { "—" }}")
            if (order.remark.isNotBlank()) appendLine("备注：${order.remark}")
            appendLine()
            appendLine("项目明细：")
            append(itemsText)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("订单详情")
            .setMessage(message.trim())
            .setPositiveButton("关闭", null)
            .show()
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
        val assigneeId = binding.inputAssignee.text?.toString()?.trim().orEmpty().let { text ->
            if (text.isBlank()) null else selectedAssigneeId
                ?: staffOptions.firstOrNull { it.label() == text }?.id
        }
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
            assigned_to = assigneeId,
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
                    binding.billBtn.isVisible = true
                    showPhotoSection()
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
        const val PHOTO_BEFORE = "BEFORE"
        const val PHOTO_AFTER = "AFTER"

        const val ARG_ID = "customer_id"
        const val ARG_NAME = "name"
        const val ARG_PHONE = "phone"
        const val ARG_GENDER = "gender"
        const val ARG_BIRTHDAY = "birthday"
        const val ARG_WECHAT = "wechat"
        const val ARG_ADDRESS = "address"
        const val ARG_INTENTION = "intention"
        const val ARG_NOTES = "notes"
        const val ARG_ASSIGNED_TO = "assigned_to"
        const val ARG_ASSIGNED_TO_NAME = "assigned_to_name"

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
            putInt(ARG_ASSIGNED_TO, customer?.assigned_to ?: -1)
            putString(ARG_ASSIGNED_TO_NAME, customer?.assigned_to_username.orEmpty())
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
