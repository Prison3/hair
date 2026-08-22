package com.hairclinic.app

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Session
import com.hairclinic.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        binding.userChip.setOnClickListener { showUserMenu(it) }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isLogin = destination.id == R.id.loginFragment
            val isEdit = destination.id == R.id.customerEditFragment
            val showChrome = !isLogin
            binding.bottomNav.visibility = if (showChrome && !isEdit) View.VISIBLE else View.GONE
            binding.topBar.visibility = if (showChrome) View.VISIBLE else View.GONE
            if (showChrome) {
                refreshUsername()
            }
        }

        if (Session.token(this).isNotBlank() && navController.currentDestination?.id == R.id.loginFragment) {
            navController.navigate(R.id.customersFragment)
        }
    }

    private fun refreshUsername() {
        val cached = Session.username(this)
        if (cached.isNotBlank()) {
            binding.userChip.text = cached
            return
        }
        binding.userChip.text = "…"
        lifecycleScope.launch {
            try {
                val me = ApiClient.get(this@MainActivity).me()
                Session.saveUsername(this@MainActivity, me.username)
                binding.userChip.text = me.username
            } catch (_: Exception) {
                binding.userChip.text = "用户"
            }
        }
    }

    private fun showUserMenu(anchor: View) {
        val name = Session.username(this).ifBlank { binding.userChip.text?.toString().orEmpty() }
        val popup = PopupMenu(this, anchor)
        if (name.isNotBlank() && name != "…" && name != "用户") {
            popup.menu.add(0, 0, 0, "当前：$name").isEnabled = false
        }
        popup.menu.add(0, 1, 1, "退出登录")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                confirmLogout()
                true
            } else false
        }
        popup.show()
    }

    private fun confirmLogout() {
        val name = Session.username(this).ifBlank { "当前账号" }
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定退出账号 $name ？")
            .setPositiveButton("退出") { _, _ ->
                Session.clear(this)
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                val navController = navHost.navController
                val options = NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .setLaunchSingleTop(true)
                    .build()
                navController.navigate(R.id.loginFragment, null, options)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
