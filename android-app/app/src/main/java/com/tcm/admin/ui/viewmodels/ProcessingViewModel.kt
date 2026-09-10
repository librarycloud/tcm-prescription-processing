package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.PackageItem
import com.tcm.admin.data.paging.ProcessingPickupPagingSource
import com.tcm.admin.data.paging.ProcessingPlansPagingSource
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
internal class ProcessingViewModel @Inject constructor(
    private val apiClient: ApiClient
) : ViewModel() {

    val mode = MutableStateFlow("plans")

    // Plans Filters
    val activeView = MutableStateFlow("today-all")
    val plansKeyword = MutableStateFlow("")
    val plansStoreId = MutableStateFlow<Int?>(null)

    // Pickup Filters
    val pickupStatus = MutableStateFlow(0)
    val pickupKeyword = MutableStateFlow("")
    val pickupStoreId = MutableStateFlow<Int?>(null)

    val stats = MutableStateFlow<JSONObject?>(null)
    
    val stores = MutableStateFlow<List<JSONObject>>(emptyList())
    val storesLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val plansFlow: Flow<PagingData<JSONObject>> = combine(
        activeView, plansKeyword, plansStoreId
    ) { v, k, s ->
        ProcessingPlanFilterParams(v, k, s)
    }.flatMapLatest { params ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            ProcessingPlansPagingSource(apiClient, params.view, params.keyword, params.storeId)
        }.flow
    }.cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pickupFlow: Flow<PagingData<PackageItem>> = combine(
        pickupStatus, pickupKeyword, pickupStoreId
    ) { s, k, st ->
        ProcessingPickupFilterParams(s, k, st)
    }.flatMapLatest { params ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            ProcessingPickupPagingSource(apiClient, params.status, params.keyword, params.storeId)
        }.flow
    }.cachedIn(viewModelScope)

    fun refreshStats(storeId: Int?) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { apiClient.processingStats(storeId) }
            }.onSuccess { summary ->
                stats.value = summary
            }
        }
    }

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

internal data class ProcessingPlanFilterParams(
    val view: String,
    val keyword: String,
    val storeId: Int?
)

internal data class ProcessingPickupFilterParams(
    val status: Int,
    val keyword: String,
    val storeId: Int?
)
