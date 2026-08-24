package com.hairclinic.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.Session
import com.hairclinic.app.databinding.ActivityMainBinding
import com.hairclinic.app.update.AppUpdateHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var appUpdate: AppUpdateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appUpdate = AppUpdateHelper(this)
        appUpdate.checkOnLaunch()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isLogin = destination.id == R.id.loginFragment
            val isEdit = destination.id == R.id.customerEditFragment ||
                destination.id == R.id.projectEditFragment ||
                destination.id == R.id.inboundFragment ||
                destination.id == R.id.stockFragment
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

    fun refreshUsername() {
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

    override fun onDestroy() {
        if (::appUpdate.isInitialized) appUpdate.dismiss()
        super.onDestroy()
    }
}
