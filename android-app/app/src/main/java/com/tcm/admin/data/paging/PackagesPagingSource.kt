package com.tcm.admin.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcm.admin.ApiClient
import com.tcm.admin.ui.PackageItem
import com.tcm.admin.ui.packageItem

class PackagesPagingSource(
    private val apiClient: ApiClient,
    private val status: Int?,
    private val keyword: String,
    private val storeId: Int?,
    private val sortBy: String
) : PagingSource<Int, PackageItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PackageItem> {
        val page = params.key ?: 1
        return try {
            val response = apiClient.packagesPaged(
                status = status,
                keyword = keyword,
                storeId = storeId,
                sortBy = sortBy,
                page = page,
                pageSize = 20
            )
            val list = response.optJSONArray("list")
            val items = buildList {
                for (i in 0 until (list?.length() ?: 0)) {
                    list?.optJSONObject(i)?.let { add(packageItem(it)) }
                }
            }
            
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.isEmpty() || items.size < params.loadSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, PackageItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
