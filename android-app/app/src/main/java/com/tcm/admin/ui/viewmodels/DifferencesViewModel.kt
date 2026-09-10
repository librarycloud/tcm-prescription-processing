package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.data.paging.DifferencesPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
internal class DifferencesViewModel @Inject constructor(
    private val apiClient: ApiClient,
) : ViewModel() {

    val tab = MutableStateFlow("current") // "current" | "logs"
    val registerProducts = MutableStateFlow<List<JSONObject>>(emptyList())
    private val registerProductsLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val differencesFlow: Flow<PagingData<JSONObject>> = tab
        .flatMapLatest { currentTab ->
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false)
            ) {
                DifferencesPagingSource(apiClient, currentTab)
            }.flow
        }.cachedIn(viewModelScope)

    suspend fun loadRegisterProducts() {
        if (registerProductsLoaded.value) return
        runCatching {
            withContext(Dispatchers.IO) { apiClient.productCatalog() }
        }.onSuccess { values ->
            registerProducts.value = (0 until values.length()).map { values.getJSONObject(it) }
            registerProductsLoaded.value = true
        }
    }
}
