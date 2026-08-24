package com.hairclinic.app.ui.inventory

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.StockInRequest
import com.hairclinic.app.databinding.FragmentInboundBinding
import com.hairclinic.app.ui.projects.ProjectEditFragment
import kotlinx.coroutines.launch
import java.util.Calendar

class InboundFragment : Fragment() {
    private var _binding: FragmentInboundBinding? = null
    private val binding get() = _binding!!
    private var inboundDate: String = today()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInboundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preset = arguments?.getString(ARG_NAME).orEmpty()
        if (preset.isNotBlank()) binding.inputName.setText(preset)
        val spec = arguments?.getString(ARG_SPEC).orEmpty()
        if (spec.isNotBlank()) binding.inputSpec.setText(spec)
        binding.inputUnit.setAdapter(ArrayAdapter(requireContext(), R.layout.item_spinner, ProjectEditFragment.UNITS))
        binding.inputUnit.keyListener = null
        val unit = arguments?.getString(ARG_UNIT).orEmpty().ifBlank { "个" }
        binding.inputUnit.setText(if (unit in ProjectEditFragment.UNITS) unit else "个", false)
        binding.inputDate.text = inboundDate
        binding.inputDate.setOnClickListener { pickDate() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.submitBtn.setOnClickListener { submit() }
    }

    private fun selectedUnit(): String {
        val value = binding.inputUnit.text?.toString()?.trim().orEmpty()
        return if (value in ProjectEditFragment.UNITS) value else "个"
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        val current = binding.inputDate.text?.toString().orEmpty()
        if (current.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            val parts = current.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                inboundDate = "%04d-%02d-%02d".format(y, m + 1, d)
                binding.inputDate.text = inboundDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun submit() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请填写产品名", Toast.LENGTH_SHORT).show()
            return
        }
        val date = binding.inputDate.text?.toString()?.trim().orEmpty()
        if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            Toast.makeText(requireContext(), "请选择进货日期", Toast.LENGTH_SHORT).show()
            return
        }
        val price = binding.inputPrice.text?.toString()?.toDoubleOrNull()
        if (price == null) {
            Toast.makeText(requireContext(), "请填写价格", Toast.LENGTH_SHORT).show()
            return
        }
        val qty = binding.inputQty.text?.toString()?.toIntOrNull() ?: 0
        if (qty <= 0) {
            Toast.makeText(requireContext(), "请填写数量", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ApiClient.get(requireContext()).stockIn(
                    StockInRequest(
                        name = name,
                        spec = binding.inputSpec.text?.toString()?.trim().orEmpty(),
                        quantity = qty,
                        unit = selectedUnit(),
                        unit_cost = price,
                        moved_at = date,
                    )
                )
                Toast.makeText(requireContext(), "已入库", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "入库失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_NAME = "name"
        const val ARG_SPEC = "spec"
        const val ARG_UNIT = "unit"

        fun args(name: String? = null, spec: String? = null, unit: String? = null): Bundle = Bundle().apply {
            putString(ARG_NAME, name.orEmpty())
            putString(ARG_SPEC, spec.orEmpty())
            putString(ARG_UNIT, unit.orEmpty())
        }

        fun today(): String {
            val cal = Calendar.getInstance()
            return "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}
