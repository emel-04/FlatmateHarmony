package com.example.flatmateharmony.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FinanceViewModelFactory(private val homeCode: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(homeCode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
