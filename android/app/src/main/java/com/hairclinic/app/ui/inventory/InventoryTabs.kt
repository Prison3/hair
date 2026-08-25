package com.hairclinic.app.ui.inventory

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.hairclinic.app.R
import com.hairclinic.app.databinding.FragmentListBinding

enum class InventorySection { STOCK, MOVEMENTS, PRODUCTS }

fun FragmentListBinding.setupInventoryTabs(
    fragment: Fragment,
    current: InventorySection,
    addLabel: String? = null,
    onAdd: (() -> Unit)? = null,
) {
    stockBtn.isVisible = true
    extraBtn.isVisible = true
    addBtn.isVisible = true
    stockBtn.text = "库存"
    extraBtn.text = "流水"
    addBtn.text = "产品"
    headerActionBtn.isVisible = onAdd != null
    if (addLabel != null) headerActionBtn.text = addLabel
    headerActionBtn.setOnClickListener { onAdd?.invoke() }

    stockBtn.setInventoryTabSelected(current == InventorySection.STOCK)
    extraBtn.setInventoryTabSelected(current == InventorySection.MOVEMENTS)
    addBtn.setInventoryTabSelected(current == InventorySection.PRODUCTS)

    val nav = fragment.findNavController()
    fun go(dest: Int) {
        if (nav.currentDestination?.id == dest) return
        if (dest == R.id.inventoryFragment) {
            nav.popBackStack(R.id.inventoryFragment, false)
            return
        }
        nav.navigate(
            dest,
            null,
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.inventoryFragment, false)
                .build(),
        )
    }
    stockBtn.setOnClickListener { go(R.id.inventoryFragment) }
    extraBtn.setOnClickListener { go(R.id.inboundListFragment) }
    addBtn.setOnClickListener { go(R.id.productListFragment) }
}

private fun MaterialButton.setInventoryTabSelected(selected: Boolean) {
    val pine = ContextCompat.getColor(context, R.color.pine)
    val paper = ContextCompat.getColor(context, R.color.paper2)
    if (selected) {
        strokeWidth = 0
        backgroundTintList = ColorStateList.valueOf(pine)
        setTextColor(paper)
    } else {
        strokeWidth = (resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.line))
        setTextColor(pine)
    }
}
