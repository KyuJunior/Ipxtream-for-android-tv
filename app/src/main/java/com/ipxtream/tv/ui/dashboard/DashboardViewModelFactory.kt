package com.ipxtream.tv.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ipxtream.tv.data.local.LibraryStore
import com.ipxtream.tv.data.model.AuthCredentials
import com.ipxtream.tv.data.repository.ContentRepository

/**
 * [ViewModelProvider.Factory] for [DashboardViewModel].
 *
 * Constructs the [ContentRepository] from [AuthCredentials], which are passed
 * in from the hosting [DashboardActivity] after a successful login.
 *
 * Replace with Hilt `@HiltViewModel` + `@Inject constructor` in a later phase.
 */
class DashboardViewModelFactory(
    private val context:     Context,
    private val credentials: AuthCredentials
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val repository = ContentRepository.create(context.applicationContext, credentials)
            val libraryStore = LibraryStore(context.applicationContext, credentials)
            return DashboardViewModel(repository, libraryStore, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
