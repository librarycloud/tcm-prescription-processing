package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.data.paging.StocktakingPagingSource
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
internal class StocktakingViewModel @Inject constructor(
    private val apiClient: ApiClient,
) : ViewModel() {

    val selectedStoreId = MutableStateFlow<Int?>(null)
    val stores = MutableStateFlow<List<JSONObject>>(emptyList())
    private val storesLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val checksFlow: Flow<PagingData<JSONObject>> = selectedStoreId
        .flatMapLatest { storeId ->
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false)
            ) {
                StocktakingPagingSource(apiClient, storeId)
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
