package com.hairclinic.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.R
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.LoginIn
import com.hairclinic.app.data.Session
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
        binding.serverUrl.setText(Session.baseUrl(requireContext()))
        binding.loginBtn.setOnClickListener {
            val server = binding.serverUrl.text?.toString().orEmpty()
            val username = binding.username.text?.toString()?.trim().orEmpty()
            val password = binding.password.text?.toString().orEmpty()
            binding.errorText.text = ""
            Session.saveBaseUrl(requireContext(), server)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val token = ApiClient.get(requireContext()).login(LoginIn(username, password))
                    Session.saveToken(requireContext(), token.access_token)
                    findNavController().navigate(R.id.customersFragment)
                } catch (e: Exception) {
                    val msg = when (e) {
                        is UnknownHostException -> "地址不可达：主机名无法解析"
                        is ConnectException -> "连接被拒绝：请确认电脑已启动服务，且手机与电脑同一 WiFi"
                        is SocketTimeoutException -> "连接超时：请检查 IP/端口，或关闭路由器「访客隔离/AP隔离」"
                        else -> e.message ?: "登录失败"
                    }
                    binding.errorText.text = "$msg\n当前: ${Session.baseUrl(requireContext())}"
                    Toast.makeText(requireContext(), "登录失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
