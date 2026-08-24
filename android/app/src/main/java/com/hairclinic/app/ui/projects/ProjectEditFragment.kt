package com.hairclinic.app.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Project
import com.hairclinic.app.data.ProjectMedicine
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.data.stockUnitLabel
import com.hairclinic.app.databinding.FragmentProjectEditBinding
import com.hairclinic.app.databinding.ItemProjectMedicineBinding
import com.hairclinic.app.ui.inventory.InboundFragment
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

class ProjectEditFragment : Fragment() {
    private var _binding: FragmentProjectEditBinding? = null
    private val binding get() = _binding!!
    private var projectId: Int = -1
    private var stockItems: List<StockItem> = emptyList()
    private val medicineRows = mutableListOf<ItemProjectMedicineBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        projectId = arguments?.getInt(ARG_ID, -1) ?: -1
        val isEdit = projectId > 0
        binding.pageTitle.text = if (isEdit) "编辑项目" else "新建项目"
        if (isEdit) {
            binding.inputName.setText(arguments?.getString(ARG_NAME).orEmpty())
            binding.inputPrice.setText(formatPrice(arguments?.getDouble(ARG_PRICE) ?: 0.0))
        }
        binding.deleteBtn.isVisible = isEdit
        binding.addMedicineBtn.setOnClickListener { addMedicineRow() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
        binding.deleteBtn.setOnClickListener { confirmDelete() }
        loadForm()
    }

    private fun loadForm() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                stockItems = api.listInventory()
                val saved = if (projectId > 0) api.getProject(projectId) else null
                if (saved != null) {
                    binding.inputName.setText(saved.name)
                    binding.inputPrice.setText(formatPrice(saved.price))
                }
                binding.medicineBox.removeAllViews()
                medicineRows.clear()
                val presets = saved?.medicines.orEmpty()
                if (presets.isEmpty()) {
                    if (stockItems.isNotEmpty()) addMedicineRow()
                } else {
                    presets.forEach { addMedicineRow(it) }
                }
                refreshEmpty()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMedicineRow(preset: ProjectMedicine? = null) {
        if (stockItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先在库存添加药品", Toast.LENGTH_SHORT).show()
            return
        }
        val row = ItemProjectMedicineBinding.inflate(layoutInflater, binding.medicineBox, false)
        binding.medicineBox.addView(row.root)
        val names = stockItems.map { it.name }
        row.inputMedName.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, names))
        row.inputMedName.keyListener = null
        row.inputUnit.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, InboundFragment.UNITS))
        row.inputUnit.keyListener = null
        row.inputMedName.setOnItemClickListener { _, _, position, _ ->
            val item = stockItems.getOrNull(position)
            if (item != null) selectUnit(row, item.unit)
        }
        if (preset != null) {
            row.inputMedName.setText(preset.item_name, false)
            row.inputDose.setText(preset.quantity.toString())
            selectUnit(row, preset.unit)
        } else {
            row.inputDose.setText("1")
            selectUnit(row, stockItems.first().unit)
        }
        row.removeBtn.setOnClickListener {
            binding.medicineBox.removeView(row.root)
            medicineRows.remove(row)
            refreshEmpty()
        }
        medicineRows += row
        refreshEmpty()
    }

    private fun selectUnit(row: ItemProjectMedicineBinding, raw: String?) {
        val value = stockUnitLabel(raw)
        row.inputUnit.setText(if (value in InboundFragment.UNITS) value else "个", false)
    }

    private fun refreshEmpty() {
        binding.medicineEmpty.isVisible = medicineRows.isEmpty()
    }

    private fun collectMedicines(): List<ProjectMedicine>? {
        val result = mutableListOf<ProjectMedicine>()
        val seen = mutableSetOf<Int>()
        for (row in medicineRows) {
            val name = row.inputMedName.text?.toString()?.trim().orEmpty()
            val item = stockItems.firstOrNull { it.name == name }
            if (item == null) {
                Toast.makeText(requireContext(), "请选择药品", Toast.LENGTH_SHORT).show()
                return null
            }
            if (!seen.add(item.id)) {
                Toast.makeText(requireContext(), "同一药品不能重复添加", Toast.LENGTH_SHORT).show()
                return null
            }
            val qty = row.inputDose.text?.toString()?.toIntOrNull() ?: 0
            if (qty <= 0) {
                Toast.makeText(requireContext(), "请填写「${item.name}」的剂量", Toast.LENGTH_SHORT).show()
                return null
            }
            val unit = row.inputUnit.text?.toString()?.trim().orEmpty()
                .ifBlank { item.unitLabel() }
                .let { if (it in InboundFragment.UNITS) it else "个" }
            result += ProjectMedicine(item_id = item.id, item_name = item.name, quantity = qty, unit = unit)
        }
        if (result.isEmpty()) {
            Toast.makeText(requireContext(), "请至少添加一种药品", Toast.LENGTH_SHORT).show()
            return null
        }
        return result
    }

    private fun confirmDelete() {
        if (projectId <= 0) return
        val name = binding.inputName.text?.toString()?.trim().orEmpty().ifBlank { "该项目" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除项目")
            .setMessage("确定删除「$name」？删除后无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete() }
            .show()
    }

    private fun delete() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteProject(projectId)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), apiError(e, "删除失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请填写项目名", Toast.LENGTH_SHORT).show()
            return
        }
        val medicines = collectMedicines() ?: return
        val body = Project(
            id = projectId.takeIf { it > 0 },
            name = name,
            price = binding.inputPrice.text?.toString()?.toDoubleOrNull() ?: 0.0,
            medicines = medicines,
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                if (projectId > 0) api.updateProject(projectId, body) else api.createProject(body)
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), apiError(e, "保存失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "project_id"
        const val ARG_NAME = "name"
        const val ARG_PRICE = "price"

        fun args(project: Project? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, project?.id ?: -1)
            putString(ARG_NAME, project?.name.orEmpty())
            putDouble(ARG_PRICE, project?.price ?: 0.0)
        }

        fun formatPrice(price: Double): String =
            if (price % 1.0 == 0.0) price.toLong().toString() else "%.2f".format(price)

        fun apiError(e: Exception, fallback: String): String {
            if (e is HttpException) {
                val raw = e.response()?.errorBody()?.string().orEmpty()
                runCatching {
                    val detail = JSONObject(raw).opt("detail")
                    if (detail is String && detail.isNotBlank()) return detail
                    if (detail is org.json.JSONArray && detail.length() > 0) {
                        val first = detail.optJSONObject(0)
                        val msg = first?.optString("msg").orEmpty()
                        if (msg.isNotBlank()) return "请至少添加一种药品"
                    }
                }
            }
            return e.message ?: fallback
        }
    }
}
