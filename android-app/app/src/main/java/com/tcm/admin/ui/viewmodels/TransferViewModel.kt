package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.data.paging.TransfersPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
internal class TransferViewModel @Inject constructor(
    private val apiClient: ApiClient,
) : ViewModel() {

    val keyword = MutableStateFlow("")
    val statusFilter = MutableStateFlow<Int?>(null)
    val overdueOnly = MutableStateFlow(false)
    val selectedStoreId = MutableStateFlow<Int?>(null)

    val stores = MutableStateFlow<List<JSONObject>>(emptyList())
    val stats = MutableStateFlow<JSONObject?>(null)
    private val storesLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val transfersFlow: Flow<PagingData<JSONObject>> = combine(
        keyword, statusFilter, overdueOnly, selectedStoreId
    ) { kw, st, od, sid ->
        TransferFilterParams(kw, st, od, sid)
    }.flatMapLatest { params ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            TransfersPagingSource(
                apiClient,
                params.keyword,
                params.status,
                params.storeId,
                params.overdue,
            )
        }.flow
    }.cachedIn(viewModelScope)

    fun refreshStats(storeId: Int?) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { apiClient.transferStats(storeId) }
            }.onSuccess { summary ->
                stats.value = summary
            }
        }
    }

    suspend fun loadStores() {
        if (storesLoaded.value) return
        runCatching {
            withContext(Dispatchers.IO) { apiClient.transferStores() }
        }.onSuccess { storeValues ->
            stores.value = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            storesLoaded.value = true
        }
    }
}

internal data class TransferFilterParams(
    val keyword: String,
    val status: Int?,
    val overdue: Boolean,
    val storeId: Int?,
)
