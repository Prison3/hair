package com.hairclinic.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.LoginIn
import com.hairclinic.app.data.Session
import com.hairclinic.app.databinding.DialogServerSettingsBinding
import com.hairclinic.app.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBtn.setOnClickListener { showServerSettings() }
        binding.loginBtn.setOnClickListener {
            val username = binding.username.text?.toString()?.trim().orEmpty()
            val password = binding.password.text?.toString().orEmpty()
            binding.errorText.text = ""
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val token = ApiClient.get(requireContext()).login(LoginIn(username, password))
                    Session.saveToken(requireContext(), token.access_token)
                    Session.saveUsername(requireContext(), username)
                    findNavController().navigate(R.id.customersFragment)
                } catch (e: Exception) {
                    val msg = when (e) {
                        is UnknownHostException -> "地址不可达：主机名无法解析"
                        is ConnectException -> "连接被拒绝：请确认电脑已启动服务，且手机与电脑同一 WiFi"
                        is SocketTimeoutException -> "连接超时：请检查服务器地址，或关闭路由器「访客隔离」"
                        else -> e.message ?: "登录失败"
                    }
                    binding.errorText.text = "$msg\n当前: ${Session.baseUrl(requireContext())}"
                    Toast.makeText(requireContext(), "登录失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showServerSettings() {
        val dialogBinding = DialogServerSettingsBinding.inflate(layoutInflater)
        dialogBinding.serverUrlInput.setText(Session.baseUrl(requireContext()))
        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val url = dialogBinding.serverUrlInput.text?.toString().orEmpty()
                Session.saveBaseUrl(requireContext(), url)
                Toast.makeText(
                    requireContext(),
                    "已保存：${Session.baseUrl(requireContext())}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
