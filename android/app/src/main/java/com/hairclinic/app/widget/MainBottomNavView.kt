package com.hairclinic.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavOptions
import com.hairclinic.app.R

class MainBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private var selectedId = 0
    private var onItemSelected: ((Int) -> Boolean)? = null

    init {
        orientation = HORIZONTAL
        val popup = PopupMenu(context, this)
        popup.inflate(R.menu.bottom_nav)
        val menu = popup.menu
        val inflater = LayoutInflater.from(context)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val tab = inflater.inflate(R.layout.item_main_nav_tab, this, false)
            tab.findViewById<ImageView>(R.id.icon).setImageDrawable(item.icon)
            tab.findViewById<TextView>(R.id.label).text = item.title
            tab.contentDescription = item.title
            tab.tag = item.itemId
            tab.setOnClickListener { select(item.itemId, fromUser = true) }
            addView(tab, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }

        val initialTop = paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, initialTop, v.paddingRight, sys.bottom)
            insets
        }
    }

    fun setAllowedDestinations(ids: Set<Int>) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val id = child.tag as Int
            child.visibility = if (id in ids) VISIBLE else GONE
        }
    }

    fun setupWithNavController(navController: NavController) {
        onItemSelected = { destId ->
            val currentId = navController.currentDestination?.id
            if (destId == selectedId) {
                if (destId == R.id.inventoryFragment && currentId != R.id.inventoryFragment) {
                    navController.popBackStack(R.id.inventoryFragment, false)
                }
                true
            } else {
                try {
                    val builder = NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(findStartDestination(navController.graph).id, false, true)
                    navController.navigate(destId, null, builder.build())
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val destId = when (destination.id) {
                R.id.inboundListFragment, R.id.productListFragment -> R.id.inventoryFragment
                else -> destination.id
            }
            val match = (0 until childCount)
                .map { getChildAt(it).tag as Int }
                .firstOrNull { id -> destId == id || destination.matchDestination(id) }
            if (match != null) select(match, fromUser = false)
        }
    }

    private fun select(id: Int, fromUser: Boolean) {
        if (fromUser) {
            val handled = onItemSelected?.invoke(id) ?: true
            if (!handled) return
        }
        selectedId = id
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.isSelected = child.tag == id
        }
    }

    private fun NavDestination.matchDestination(destId: Int): Boolean {
        var current: NavDestination? = this
        while (current != null) {
            if (current.id == destId) return true
            current = current.parent
        }
        return false
    }

    private fun findStartDestination(graph: NavGraph): NavDestination {
        var start: NavDestination = graph
        while (start is NavGraph) {
            start = start.findNode(start.startDestinationId) ?: break
        }
        return start
    }
}
