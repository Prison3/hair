package com.hairclinic.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
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
        applyRoleTabs()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (!Session.isAllowedDestination(this, destination.id) && destination.id != R.id.loginFragment) {
                navController.navigate(Session.homeDestination(this))
                return@addOnDestinationChangedListener
            }
            val isLogin = destination.id == R.id.loginFragment
            val isEdit = destination.id == R.id.customerEditFragment ||
                destination.id == R.id.projectEditFragment ||
                destination.id == R.id.productEditFragment ||
                destination.id == R.id.inboundFragment ||
                destination.id == R.id.stockFragment ||
                destination.id == R.id.staffEditFragment
            val showChrome = !isLogin
            binding.bottomNav.visibility = if (showChrome && !isEdit) View.VISIBLE else View.GONE
            binding.topBar.visibility = if (showChrome) View.VISIBLE else View.GONE
            if (showChrome) {
                refreshUsername()
            }
        }

        if (Session.token(this).isNotBlank() && navController.currentDestination?.id == R.id.loginFragment) {
            navController.navigate(Session.homeDestination(this))
        }
        refreshRole()
    }

    fun applyRoleTabs() {
        binding.bottomNav.setAllowedDestinations(Session.allowedNavIds(this))
    }

    fun refreshRole() {
        if (Session.token(this).isBlank()) return
        lifecycleScope.launch {
            try {
                val me = ApiClient.get(this@MainActivity).me()
                Session.saveUsername(this@MainActivity, me.username)
                Session.saveRole(this@MainActivity, me.role)
                applyRoleTabs()
                binding.userChip.text = me.username
            } catch (_: Exception) {
            }
        }
    }

    fun refreshUsername() {
        val cached = Session.username(this)
        if (cached.isNotBlank()) {
            binding.userChip.text = cached
            return
        }
        binding.userChip.text = "…"
        refreshRole()
    }

    override fun onDestroy() {
        if (::appUpdate.isInitialized) appUpdate.dismiss()
        super.onDestroy()
    }
}
