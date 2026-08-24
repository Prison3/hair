package com.hairclinic.app.ui.inventory

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
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.StockItem
import com.hairclinic.app.data.StockItemWrite
import com.hairclinic.app.databinding.FragmentProductEditBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch

class ProductEditFragment : Fragment() {
    private var _binding: FragmentProductEditBinding? = null
    private val binding get() = _binding!!
    private var itemId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        itemId = arguments?.getInt(ARG_ID, -1) ?: -1
        val isEdit = itemId > 0
        binding.pageTitle.text = if (isEdit) "编辑药品" else "录入药品"
        if (isEdit) {
            binding.inputName.setText(arguments?.getString(ARG_NAME).orEmpty())
        }
        binding.editActions.isVisible = isEdit
        binding.deleteBtn.isVisible = isEdit

        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
        binding.deleteBtn.setOnClickListener { confirmDelete() }
        binding.inboundBtn.setOnClickListener {
            findNavController().navigate(R.id.inboundFragment, InboundFragment.args(itemId))
        }
        binding.stockBtn.setOnClickListener {
            findNavController().navigate(R.id.stockFragment, currentStockArgs())
        }
    }

    private fun currentStockArgs(): Bundle {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
            .ifBlank { arguments?.getString(ARG_NAME).orEmpty() }
        return StockFragment.args(
            StockItem(
                id = itemId,
                name = name,
                stock_qty = arguments?.getInt(ARG_STOCK) ?: 0,
                cost_price = arguments?.getDouble(ARG_COST) ?: 0.0,
            )
        )
    }

    private fun confirmDelete() {
        if (itemId <= 0) return
        val name = binding.inputName.text?.toString()?.trim().orEmpty().ifBlank { "该药品" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除药品")
            .setMessage("确定删除「$name」？相关入库记录也会一起删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> delete() }
            .show()
    }

    private fun delete() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).deleteStockItem(itemId)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), ProjectEditFragment.apiError(e, "删除失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请填写药品名", Toast.LENGTH_SHORT).show()
            return
        }
        val body = StockItemWrite(name = name)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                if (itemId > 0) api.updateStockItem(itemId, body) else api.createStockItem(body)
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), ProjectEditFragment.apiError(e, "保存失败"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "item_id"
        const val ARG_NAME = "name"
        const val ARG_STOCK = "stock"
        const val ARG_COST = "cost"

        fun args(item: StockItem? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, item?.id ?: -1)
            putString(ARG_NAME, item?.name.orEmpty())
            putInt(ARG_STOCK, item?.stock_qty ?: 0)
            putDouble(ARG_COST, item?.cost_price ?: 0.0)
        }
    }
}
