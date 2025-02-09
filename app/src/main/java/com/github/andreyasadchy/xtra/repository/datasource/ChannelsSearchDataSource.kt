package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearch
import kotlinx.coroutines.CoroutineScope

class ChannelsSearchDataSource private constructor(
    private val clientId: String?,
    private val userToken: String?,
    private val query: String,
    private val api: HelixApi,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<ChannelSearch>(coroutineScope) {
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<ChannelSearch>) {
        loadInitial(params, callback) {
            val get = api.getChannels(clientId, userToken, query, params.requestedLoadSize, offset)
            val list = mutableListOf<ChannelSearch>()
            get.data?.let { list.addAll(it) }
            val ids = mutableListOf<String>()
            for (i in list) {
                i.id?.let { ids.add(it) }
            }
            if (ids.isNotEmpty()) {
                val users = api.getUserById(clientId, userToken, ids).data
                if (users != null) {
                    for (i in users) {
                        val items = list.filter { it.id == i.id }
                        for (item in items) {
                            item.profileImageURL = i.profile_image_url
                        }
                    }
                }
            }
            offset = get.pagination?.cursor
            list
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<ChannelSearch>) {
        loadRange(params, callback) {
            val get = api.getChannels(clientId, userToken, query, params.loadSize, offset)
            val list = mutableListOf<ChannelSearch>()
            if (offset != null && offset != "") {
                get.data?.let { list.addAll(it) }
                val ids = mutableListOf<String>()
                for (i in list) {
                    i.id?.let { ids.add(it) }
                }
                if (ids.isNotEmpty()) {
                    val users = api.getUserById(clientId, userToken, ids).data
                    if (users != null) {
                        for (i in users) {
                            val items = list.filter { it.id == i.id }
                            for (item in items) {
                                item.profileImageURL = i.profile_image_url
                            }
                        }
                    }
                }
                offset = get.pagination?.cursor
            }
            list
        }
    }

    class Factory(
        private val clientId: String?,
        private val userToken: String?,
        private val query: String,
        private val api: HelixApi,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, ChannelSearch, ChannelsSearchDataSource>() {

        override fun create(): DataSource<Int, ChannelSearch> =
                ChannelsSearchDataSource(clientId, userToken, query, api, coroutineScope).also(sourceLiveData::postValue)
    }
}
