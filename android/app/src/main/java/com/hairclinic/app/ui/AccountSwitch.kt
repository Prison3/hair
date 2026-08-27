package com.hairclinic.app.ui

import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.hairclinic.app.MainActivity
import com.hairclinic.app.R
import com.hairclinic.app.data.Session
import com.hairclinic.app.data.TokenOut

fun Fragment.enterAccount(token: TokenOut, expectedRole: String? = null) {
    val ctx = requireContext()
    Session.rememberOriginIfNeeded(ctx)
    val role = Session.normalizeRole(
        expectedRole?.takeIf { it.isNotBlank() } ?: token.role,
        fallback = Session.ROLE_MANAGER,
    )
    Session.saveAuth(ctx, token.access_token, token.username, role)
    goHomeAfterAccountChange()
    (activity as? MainActivity)?.refreshRole()
}

fun Fragment.returnToAdminAccount(): Boolean {
    val ctx = requireContext()
    if (!Session.restoreOrigin(ctx)) return false
    goHomeAfterAccountChange()
    (activity as? MainActivity)?.refreshRole()
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
            .setPopUpTo(R.id.loginFragment, true)
            .setLaunchSingleTop(true)
            .build(),
    )
}
