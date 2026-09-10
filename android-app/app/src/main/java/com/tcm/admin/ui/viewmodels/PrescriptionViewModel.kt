package com.tcm.admin.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tcm.admin.ApiClient
import com.tcm.admin.data.paging.PrescriptionsPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class PrescriptionViewModel @Inject constructor(
    private val apiClient: ApiClient
) : ViewModel() {

    val keyword = MutableStateFlow("")
    val status = MutableStateFlow<Int?>(null)
    val doctorId = MutableStateFlow<Int?>(null)
    val storeId = MutableStateFlow<Int?>(null)

    val doctors = MutableStateFlow<List<JSONObject>>(emptyList())
    val stores = MutableStateFlow<List<JSONObject>>(emptyList())
    val filtersLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val prescriptionsFlow: Flow<PagingData<JSONObject>> = combine(
        keyword, status, doctorId, storeId
    ) { k, s, d, st ->
        FilterParams(k, s, d, st)
    }.flatMapLatest { params ->
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            PrescriptionsPagingSource(apiClient, params.status, params.keyword, params.storeId, params.doctorId)
        }.flow
    }.cachedIn(viewModelScope)

    fun updateFilters(newKeyword: String? = null, newStatus: Int? = -2, newDoctorId: Int? = -2, newStoreId: Int? = -2) {
        if (newKeyword != null) keyword.value = newKeyword
        if (newStatus != -2) status.value = newStatus
        if (newDoctorId != -2) doctorId.value = newDoctorId
        if (newStoreId != -2) storeId.value = newStoreId
    }

    suspend fun loadFilters() {
        if (filtersLoaded.value) return
        runCatching {
            withContext(Dispatchers.IO) { Pair(apiClient.doctors(), apiClient.availableStores()) }
        }.onSuccess { (doctorValues, storeValues) ->
            doctors.value = (0 until doctorValues.length()).map { doctorValues.getJSONObject(it) }
            stores.value = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            filtersLoaded.value = true
        }
    }

    suspend fun deletePrescription(id: Int) {
        withContext(Dispatchers.IO) {
            apiClient.deletePrescription(id)
        }
    }
}

data class FilterParams(
    val keyword: String,
    val status: Int?,
    val doctorId: Int?,
    val storeId: Int?
)
