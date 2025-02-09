package com.github.andreyasadchy.xtra.repository.datasourceGQLquery

import androidx.paging.DataSource
import com.apollographql.apollo3.api.Optional
import com.github.andreyasadchy.xtra.UserClipsQuery
import com.github.andreyasadchy.xtra.di.XtraModule
import com.github.andreyasadchy.xtra.di.XtraModule_ApolloClientFactory.apolloClient
import com.github.andreyasadchy.xtra.model.helix.clip.Clip
import com.github.andreyasadchy.xtra.repository.datasource.BaseDataSourceFactory
import com.github.andreyasadchy.xtra.repository.datasource.BasePositionalDataSource
import com.github.andreyasadchy.xtra.type.ClipsPeriod
import kotlinx.coroutines.CoroutineScope

class ChannelClipsDataSourceGQLquery(
    private val clientId: String?,
    private val channelId: String?,
    private val sort: ClipsPeriod?,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Clip>(coroutineScope) {
    private var offset: String? = null
    private var nextPage: Boolean = true

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Clip>) {
        loadInitial(params, callback) {
            val get1 = apolloClient(XtraModule(), clientId).query(UserClipsQuery(id = Optional.Present(channelId), sort = Optional.Present(sort), first = Optional.Present(params.requestedLoadSize), after = Optional.Present(offset))).execute().data?.user
            val get = get1?.clips?.edges
            val list = mutableListOf<Clip>()
            if (get != null) {
                for (i in get) {
                    list.add(
                        Clip(
                            id = i?.node?.slug ?: "",
                            broadcaster_id = channelId,
                            broadcaster_login = get1.login,
                            broadcaster_name = get1.displayName,
                            video_id = i?.node?.video?.id,
                            videoOffsetSeconds = i?.node?.videoOffsetSeconds,
                            game_id = i?.node?.game?.id,
                            game_name = i?.node?.game?.displayName,
                            title = i?.node?.title,
                            view_count = i?.node?.viewCount,
                            created_at = i?.node?.createdAt,
                            duration = i?.node?.durationSeconds?.toDouble(),
                            thumbnail_url = i?.node?.thumbnailURL,
                            profileImageURL = get1.profileImageURL,
                        )
                    )
                }
                offset = get.lastOrNull()?.cursor
                nextPage = get1.clips.pageInfo?.hasNextPage ?: true
            }
            list
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Clip>) {
        loadRange(params, callback) {
            val get1 = apolloClient(XtraModule(), clientId).query(UserClipsQuery(id = Optional.Present(channelId), sort = Optional.Present(sort), first = Optional.Present(params.loadSize), after = Optional.Present(offset))).execute().data?.user
            val get = get1?.clips?.edges
            val list = mutableListOf<Clip>()
            if (get != null && nextPage && offset != null && offset != "") {
                for (i in get) {
                    list.add(
                        Clip(
                            id = i?.node?.slug ?: "",
                            broadcaster_id = channelId,
                            broadcaster_login = get1.login,
                            broadcaster_name = get1.displayName,
                            video_id = i?.node?.video?.id,
                            videoOffsetSeconds = i?.node?.videoOffsetSeconds,
                            game_id = i?.node?.game?.id,
                            game_name = i?.node?.game?.displayName,
                            title = i?.node?.title,
                            view_count = i?.node?.viewCount,
                            created_at = i?.node?.createdAt,
                            duration = i?.node?.durationSeconds?.toDouble(),
                            thumbnail_url = i?.node?.thumbnailURL,
                            profileImageURL = get1.profileImageURL,
                        )
                    )
                }
                offset = get.lastOrNull()?.cursor
                nextPage = get1.clips.pageInfo?.hasNextPage ?: true
            }
            list
        }
    }

    class Factory(
        private val clientId: String?,
        private val channelId: String?,
        private val sort: ClipsPeriod?,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Clip, ChannelClipsDataSourceGQLquery>() {

        override fun create(): DataSource<Int, Clip> =
                ChannelClipsDataSourceGQLquery(clientId, channelId, sort, coroutineScope).also(sourceLiveData::postValue)
    }
}
