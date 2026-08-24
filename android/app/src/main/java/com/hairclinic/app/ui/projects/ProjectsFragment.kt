package com.hairclinic.app.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Project
import com.hairclinic.app.databinding.FragmentListBinding
import com.hairclinic.app.ui.customers.Item
import com.hairclinic.app.ui.customers.SimpleAdapter
import kotlinx.coroutines.launch

class ProjectsFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = SimpleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pageTitle.text = "项目"
        binding.pageSubtitle.text = "由药品组成的项目与价格"
        binding.addBtn.text = "添加项目"
        binding.searchRow.isVisible = false
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.addBtn.setOnClickListener { openEditor(null) }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) load()
    }

    private fun openEditor(project: Project?) {
        findNavController().navigate(R.id.projectEditFragment, ProjectEditFragment.args(project))
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = ApiClient.get(requireContext()).listProjects()
                adapter.submit(list.map { p ->
                    val price = ProjectEditFragment.formatPrice(p.price)
                    val meds = p.medicineText()
                    Item(
                        title = p.name,
                        subtitle = if (meds.isBlank()) "¥$price" else "¥$price\n$meds",
                        onClick = { openEditor(p) },
                    )
                })
                binding.emptyText.isVisible = list.isEmpty()
                binding.emptyText.text = "暂无项目，点击下方添加"
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
