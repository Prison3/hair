package com.hairclinic.app.ui

import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.MainActivity
import com.hairclinic.app.R
import com.hairclinic.app.data.Session
import com.hairclinic.app.data.TokenOut

fun Fragment.enterAccount(token: TokenOut) {
    val ctx = requireContext()
    Session.rememberOriginIfNeeded(ctx)
    Session.saveAuth(ctx, token.access_token, token.username, token.role)
    goHomeAfterAccountChange()
}

fun Fragment.returnToAdminAccount(): Boolean {
    val ctx = requireContext()
    if (!Session.restoreOrigin(ctx)) return false
    goHomeAfterAccountChange()
    return true
}

private fun Fragment.goHomeAfterAccountChange() {
    val activity = activity as? MainActivity
    activity?.applyRoleTabs()
    activity?.refreshUsername()
    val nav = findNavController()
    nav.navigate(
        Session.homeDestination(requireContext()),
        null,
        NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, false)
            .setLaunchSingleTop(true)
            .build(),
    )
}
