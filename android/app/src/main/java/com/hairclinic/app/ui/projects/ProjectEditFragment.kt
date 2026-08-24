package com.hairclinic.app.ui.projects

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
import com.hairclinic.app.data.Project
import com.hairclinic.app.databinding.FragmentProjectEditBinding
import kotlinx.coroutines.launch

class ProjectEditFragment : Fragment() {
    private var _binding: FragmentProjectEditBinding? = null
    private val binding get() = _binding!!
    private var projectId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProjectEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        projectId = arguments?.getInt(ARG_ID, -1) ?: -1
        val isEdit = projectId > 0
        binding.pageTitle.text = if (isEdit) "编辑项目" else "新建项目"

        val unitAdapter = ArrayAdapter(requireContext(), R.layout.item_spinner, UNITS)
        binding.inputUnit.setAdapter(unitAdapter)
        binding.inputUnit.keyListener = null
        selectUnit(if (isEdit) arguments?.getString(ARG_UNIT) else "个")

        if (isEdit) {
            binding.inputName.setText(arguments?.getString(ARG_NAME).orEmpty())
            binding.inputPrice.setText(formatPrice(arguments?.getDouble(ARG_PRICE) ?: 0.0))
            binding.inputGraft.setText((arguments?.getInt(ARG_GRAFT) ?: 0).toString())
            binding.inputDesc.setText(arguments?.getString(ARG_DESC).orEmpty())
            binding.inputActive.isChecked = arguments?.getBoolean(ARG_ACTIVE, true) ?: true
        }

        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
    }

    private fun selectUnit(raw: String?) {
        val label = when (val v = raw?.trim().orEmpty()) {
            "", "单位" -> "次"
            else -> v
        }
        binding.inputUnit.setText(if (label in UNITS) label else "个", false)
    }

    private fun selectedUnit(): String {
        val value = binding.inputUnit.text?.toString()?.trim().orEmpty()
        return if (value in UNITS) value else "个"
    }

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请填写套餐名称", Toast.LENGTH_SHORT).show()
            return
        }
        val body = Project(
            id = projectId.takeIf { it > 0 },
            name = name,
            price = binding.inputPrice.text?.toString()?.toDoubleOrNull() ?: 0.0,
            graft_count = binding.inputGraft.text?.toString()?.toIntOrNull() ?: 0,
            unit = selectedUnit(),
            description = binding.inputDesc.text?.toString()?.trim().orEmpty(),
            active = binding.inputActive.isChecked,
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                if (projectId > 0) api.updateProject(projectId, body) else api.createProject(body)
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
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
        val UNITS = listOf("支", "个", "盒", "次")
        const val ARG_ID = "project_id"
        const val ARG_NAME = "name"
        const val ARG_PRICE = "price"
        const val ARG_GRAFT = "graft"
        const val ARG_UNIT = "unit"
        const val ARG_DESC = "desc"
        const val ARG_ACTIVE = "active"

        fun args(project: Project? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, project?.id ?: -1)
            putString(ARG_NAME, project?.name.orEmpty())
            putDouble(ARG_PRICE, project?.price ?: 0.0)
            putInt(ARG_GRAFT, project?.graft_count ?: 0)
            putString(ARG_UNIT, project?.unitLabel() ?: "个")
            putString(ARG_DESC, project?.description.orEmpty())
            putBoolean(ARG_ACTIVE, project?.active ?: true)
        }

        fun formatPrice(price: Double): String =
            if (price % 1.0 == 0.0) price.toLong().toString() else "%.2f".format(price)
    }
}
