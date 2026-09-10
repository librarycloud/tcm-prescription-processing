package com.tcm.admin.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tcm.admin.ApiClient
import org.json.JSONObject

internal class TransfersPagingSource(
    private val apiClient: ApiClient,
    private val keyword: String,
    private val status: Int?,
    private val storeId: Int?,
    private val overdue: Boolean,
) : PagingSource<Int, JSONObject>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, JSONObject> {
        val page = params.key ?: 1
        return try {
            val response = apiClient.transfersPaged(
                keyword = keyword,
                status = status,
                storeId = storeId,
                overdue = overdue,
                page = page,
                pageSize = 20,
            )
            val list = response.optJSONArray("list")
            val items = buildList {
                for (i in 0 until (list?.length() ?: 0)) {
                    list?.optJSONObject(i)?.let { add(it) }
                }
            }

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (items.isEmpty() || items.size < params.loadSize) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, JSONObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
