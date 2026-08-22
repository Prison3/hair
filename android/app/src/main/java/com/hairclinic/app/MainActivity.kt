package com.hairclinic.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.hairclinic.app.data.Session
import com.hairclinic.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBottom = destination.id != R.id.loginFragment
            binding.bottomNav.visibility = if (showBottom) View.VISIBLE else View.GONE
        }

        if (Session.token(this).isNotBlank() && navController.currentDestination?.id == R.id.loginFragment) {
            navController.navigate(R.id.customersFragment)
        }
    }
}
