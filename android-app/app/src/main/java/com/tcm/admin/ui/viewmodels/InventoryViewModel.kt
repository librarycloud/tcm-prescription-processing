package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.data.paging.InventoryPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
internal class InventoryViewModel @Inject constructor(
    private val apiClient: ApiClient,
) : ViewModel() {

    val query = MutableStateFlow("")
    val selectedStoreId = MutableStateFlow<Int?>(null)
    val selectedProduct = MutableStateFlow<JSONObject?>(null)

    val stores = MutableStateFlow<List<JSONObject>>(emptyList())
    private val storesLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val productsFlow: Flow<PagingData<JSONObject>> = combine(
        query, selectedStoreId
    ) { q, sid ->
        InventoryFilterParams(q, sid)
    }.flatMapLatest { params ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            InventoryPagingSource(apiClient, params.query, params.storeId)
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun loadStores() {
        if (storesLoaded.value) return
        runCatching {
            withContext(Dispatchers.IO) { apiClient.availableStores() }
        }.onSuccess { storeValues ->
            stores.value = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            storesLoaded.value = true
        }
    }
}

internal data class InventoryFilterParams(
    val query: String,
    val storeId: Int?,
)
