package com.hairclinic.app.ui.customers

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Customer
import com.hairclinic.app.databinding.FragmentCustomerEditBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class CustomerEditFragment : Fragment() {
    private var _binding: FragmentCustomerEditBinding? = null
    private val binding get() = _binding!!
    private var customerId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomerEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        customerId = arguments?.getInt(ARG_ID, -1) ?: -1
        val isEdit = customerId > 0
        binding.pageTitle.text = if (isEdit) "编辑客户" else "添加客户"

        if (isEdit) {
            binding.inputName.setText(arguments?.getString(ARG_NAME).orEmpty())
            binding.inputPhone.setText(arguments?.getString(ARG_PHONE).orEmpty())
            binding.inputNotes.setText(arguments?.getString(ARG_NOTES).orEmpty())
            when (arguments?.getString(ARG_GENDER)) {
                "男" -> binding.genderMale.isChecked = true
                "女" -> binding.genderFemale.isChecked = true
            }
            val birthday = arguments?.getString(ARG_BIRTHDAY).orEmpty()
            if (birthday.isNotBlank()) binding.inputBirthday.text = birthday
        }

        binding.inputBirthday.setOnClickListener { pickBirthday() }
        binding.backBtn.setOnClickListener { findNavController().navigateUp() }
        binding.cancelBtn.setOnClickListener { findNavController().navigateUp() }
        binding.saveBtn.setOnClickListener { save() }
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

    private fun save() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val phone = binding.inputPhone.text?.toString()?.trim().orEmpty()
        if (name.isBlank() || phone.isBlank()) {
            Toast.makeText(requireContext(), "请填写姓名和手机", Toast.LENGTH_SHORT).show()
            return
        }
        val gender = when {
            binding.genderMale.isChecked -> "男"
            binding.genderFemale.isChecked -> "女"
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
            notes = binding.inputNotes.text?.toString()?.trim().orEmpty(),
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.get(requireContext())
                if (customerId > 0) api.updateCustomer(customerId, body) else api.createCustomer(body)
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
        const val ARG_ID = "customer_id"
        const val ARG_NAME = "name"
        const val ARG_PHONE = "phone"
        const val ARG_GENDER = "gender"
        const val ARG_BIRTHDAY = "birthday"
        const val ARG_NOTES = "notes"

        fun args(customer: Customer? = null): Bundle = Bundle().apply {
            putInt(ARG_ID, customer?.id ?: -1)
            putString(ARG_NAME, customer?.name.orEmpty())
            putString(ARG_PHONE, customer?.phone.orEmpty())
            putString(ARG_GENDER, customer?.gender.orEmpty())
            putString(ARG_BIRTHDAY, customer?.birthday.orEmpty())
            putString(ARG_NOTES, customer?.notes.orEmpty())
        }
    }
}
