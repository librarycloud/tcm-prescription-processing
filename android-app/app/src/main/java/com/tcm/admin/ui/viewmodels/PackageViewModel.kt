package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.data.paging.PackagesPagingSource
import com.tcm.admin.PackageItem
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
class PackageViewModel @Inject constructor(
    private val apiClient: ApiClient
) : ViewModel() {

    val keyword = MutableStateFlow("")
    val status = MutableStateFlow<Int?>(null)
    val storeId = MutableStateFlow<Int?>(null)
    val sortBy = MutableStateFlow("createdAt")

    val stores = MutableStateFlow<List<JSONObject>>(emptyList())
    val storesLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val packagesFlow: Flow<PagingData<PackageItem>> = combine(
        keyword, status, storeId, sortBy
    ) { k, s, st, sb ->
        PackageFilterParams(k, s, st, sb)
    }.flatMapLatest { params ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            PackagesPagingSource(apiClient, params.status, params.keyword, params.storeId, params.sortBy)
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

    fun updateFilters(newKeyword: String? = null, newStatus: Int? = -2, newStoreId: Int? = -2, newSortBy: String? = null) {
        if (newKeyword != null) keyword.value = newKeyword
        if (newStatus != -2) status.value = newStatus
        if (newStoreId != -2) storeId.value = newStoreId
        if (newSortBy != null) sortBy.value = newSortBy
    }
}

data class PackageFilterParams(
    val keyword: String,
    val status: Int?,
    val storeId: Int?,
    val sortBy: String
)
