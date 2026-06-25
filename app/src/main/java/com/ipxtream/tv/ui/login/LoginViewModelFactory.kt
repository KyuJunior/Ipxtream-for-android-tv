package com.ipxtream.tv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ipxtream.tv.data.local.CredentialStore

/**
 * [ViewModelProvider.Factory] for [LoginViewModel].
 *
 * Required because [LoginViewModel] has a constructor parameter ([CredentialStore])
 * that the default factory cannot supply.
 *
 * Usage in an Activity or Fragment:
 * ```kotlin
 * val store   = CredentialStore(applicationContext)
 * val factory = LoginViewModelFactory(store)
 * val vm      = ViewModelProvider(this, factory)[LoginViewModel::class.java]
 * ```
 *
 * This will be replaced by Hilt in a later phase if dependency injection is added.
 */
class LoginViewModelFactory(
    private val credentialStore: CredentialStore
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(credentialStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
