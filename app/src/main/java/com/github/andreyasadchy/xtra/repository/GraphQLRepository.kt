package com.github.andreyasadchy.xtra.repository

import android.util.Log
import com.github.andreyasadchy.xtra.api.GraphQLApi
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GraphQLRepository"

@Singleton
class GraphQLRepository @Inject constructor(private val graphQL: GraphQLApi) {

    suspend fun loadClipUrls(clientId: String, slug: String): Map<String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading clip urls for clip: $slug")
        val array = JsonArray(1)
        val videoAccessTokenOperation = JsonObject().apply {
            addProperty("operationName", "VideoAccessToken_Clip")
            add("variables", JsonObject().apply {
                addProperty("slug", slug)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "36b89d2507fce29e5ca551df756d27c1cfe079e2609642b4390aa4c35796eb11")
                })
            })
        }
        array.add(videoAccessTokenOperation)
        val response = graphQL.getClipData(clientId, array)
        response.videos.associateBy({ if (it.frameRate != 60) "${it.quality}p" else "${it.quality}p${it.frameRate}" }, { it.url })
    }

    suspend fun loadTopGames(clientId: String?, tags: List<String>?, limit: Int?, cursor: String?): GameDataResponse {
        val array = JsonArray()
        if (tags != null) {
            for (i in tags) {
                array.add(i)
            }
        }
        val json = JsonObject().apply {
            addProperty("operationName", "BrowsePage_AllDirectories")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                add("options", JsonObject().apply {
                    addProperty("sort", "VIEWER_COUNT")
                    add("tags", array)
                })
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "78957de9388098820e222c88ec14e85aaf6cf844adf44c8319c545c75fd63203")
                })
            })
        }
        return graphQL.getTopGames(clientId, json)
    }

    suspend fun loadTopStreams(clientId: String?, tags: List<String>?, limit: Int?, cursor: String?): StreamDataResponse {
        val array = JsonArray()
        if (tags != null) {
            for (i in tags) {
                array.add(i)
            }
        }
        val json = JsonObject().apply {
            addProperty("operationName", "BrowsePage_Popular")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                addProperty("platformType", "all")
                addProperty("sortTypeIsRecency", false)
                add("options", JsonObject().apply {
                    addProperty("sort", "VIEWER_COUNT")
                    add("tags", array)
                })
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "4de7f2166105c1a034ba40251f55593b90500f69cf44c8735db4f62ad2760c39")
                })
            })
        }
        return graphQL.getTopStreams(clientId, json)
    }

    suspend fun loadGameStreams(clientId: String?, game: String?, tags: List<String>?, limit: Int?, cursor: String?): GameStreamsDataResponse {
        val array = JsonArray()
        if (tags != null) {
            for (i in tags) {
                array.add(i)
            }
        }
        val json = JsonObject().apply {
            addProperty("operationName", "DirectoryPage_Game")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                addProperty("name", game)
                addProperty("sortTypeIsRecency", false)
                add("options", JsonObject().apply {
                    addProperty("sort", "VIEWER_COUNT")
                    add("tags", array)
                })
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "d5c5df7ab9ae65c3ea0f225738c08a36a4a76e4c6c31db7f8c4b8dc064227f9e")
                })
            })
        }
        return graphQL.getGameStreams(clientId, json)
    }

    suspend fun loadGameVideos(clientId: String?, game: String?, type: String?, sort: String?, limit: Int?, cursor: String?): GameVideosDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "DirectoryVideos_Game")
            add("variables", JsonObject().apply {
                if (type != null) {
                    addProperty("broadcastTypes", type)
                }
                addProperty("followedCursor", cursor)
                addProperty("gameName", game)
                addProperty("videoLimit", limit)
                addProperty("videoSort", sort)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "c04a45b3adfcfacdff2bf4c4172ca4904870d62d6d19f3d490705c5d0a9e511e")
                })
            })
        }
        return graphQL.getGameVideos(clientId, json)
    }

    suspend fun loadGameClips(clientId: String?, game: String?, sort: String?, limit: Int?, cursor: String?): GameClipsDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "ClipsCards__Game")
            add("variables", JsonObject().apply {
                add("criteria", JsonObject().apply {
                    addProperty("filter", sort)
                })
                addProperty("cursor", cursor)
                addProperty("gameName", game)
                addProperty("limit", limit)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "0d8d0eba9fc7ef77de54a7d933998e21ad7a1274c867ec565ac14ffdce77b1f9")
                })
            })
        }
        return graphQL.getGameClips(clientId, json)
    }

    suspend fun loadChannelVideos(clientId: String?, channel: String?, type: String?, sort: String?, limit: Int?, cursor: String?): ChannelVideosDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "FilterableVideoTower_Videos")
            add("variables", JsonObject().apply {
                addProperty("broadcastType", type)
                addProperty("cursor", cursor)
                addProperty("channelOwnerLogin", channel)
                addProperty("limit", limit)
                addProperty("videoSort", sort)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "a937f1d22e269e39a03b509f65a7490f9fc247d7f83d6ac1421523e3b68042cb")
                })
            })
        }
        return graphQL.getChannelVideos(clientId, json)
    }

    suspend fun loadChannelClips(clientId: String?, channel: String?, sort: String?, limit: Int?, cursor: String?): ChannelClipsDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "ClipsCards__User")
            add("variables", JsonObject().apply {
                add("criteria", JsonObject().apply {
                    addProperty("filter", sort)
                })
                addProperty("cursor", cursor)
                addProperty("login", channel)
                addProperty("limit", limit)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "b73ad2bfaecfd30a9e6c28fada15bd97032c83ec77a0440766a56fe0bd632777")
                })
            })
        }
        return graphQL.getChannelClips(clientId, json)
    }

    suspend fun loadSearchChannels(clientId: String?, query: String?, cursor: String?): SearchChannelDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "SearchResultsPage_SearchResults")
            add("variables", JsonObject().apply {
                add("options", JsonObject().apply {
                    add("targets", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("cursor", cursor)
                            addProperty("index", "CHANNEL")
                        })
                    })
                })
                addProperty("query", query)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "ee977ac21b324669b4c109be49ed3032227e8850bea18503d0ced68e8156c2a5")
                })
            })
        }
        return graphQL.getSearchChannels(clientId, json)
    }

    suspend fun loadSearchGames(clientId: String?, query: String?, cursor: String?): SearchGameDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "SearchResultsPage_SearchResults")
            add("variables", JsonObject().apply {
                add("options", JsonObject().apply {
                    add("targets", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("cursor", cursor)
                            addProperty("index", "GAME")
                        })
                    })
                })
                addProperty("query", query)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "ee977ac21b324669b4c109be49ed3032227e8850bea18503d0ced68e8156c2a5")
                })
            })
        }
        return graphQL.getSearchGames(clientId, json)
    }

    suspend fun loadGameTags(clientId: String?): TagGameDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "SearchCategoryTags")
            add("variables", JsonObject().apply {
                addProperty("limit", 100)
                addProperty("userQuery", "")
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "e8811c72159b644b87971a4f36872028355ce5349eb8b7fb5cc478cd79d4fd92")
                })
            })
        }
        return graphQL.getGameTags(clientId, json)
    }

    suspend fun loadGameStreamTags(clientId: String?, gameName: String? = null): TagGameStreamDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "TopTags")
            add("variables", JsonObject().apply {
                addProperty("categoryName", gameName)
                addProperty("limit", 10000)
                addProperty("showTopTagsByCategory", gameName != null)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "16c030e5b84bf696f9cf25bee7ddde328c93f2b481a0519a806c19d0e91ab9c1")
                })
            })
        }
        return graphQL.getGameStreamTags(clientId, json)
    }

    suspend fun loadStreamTags(clientId: String?): TagStreamDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "TopTags")
            add("variables", JsonObject().apply {
                addProperty("limit", 10000)
                addProperty("showTopTagsByCategory", false)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "16c030e5b84bf696f9cf25bee7ddde328c93f2b481a0519a806c19d0e91ab9c1")
                })
            })
        }
        return graphQL.getStreamTags(clientId, json)
    }

    suspend fun loadSearchGameTags(clientId: String?, gameId: String?, query: String?): TagSearchGameStreamDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "SearchSingleCategoryTags")
            add("variables", JsonObject().apply {
                addProperty("categoryID", gameId)
                addProperty("limit", 100)
                addProperty("userQuery", query)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "0928a2ec27d51a3be8562d1b724a4b03164b94d26e415be1485a7c6230eb5cac")
                })
            })
        }
        return graphQL.getSearchGameTags(clientId, json)
    }

    suspend fun loadSearchAllTags(clientId: String?, query: String?): TagSearchDataResponse {
        val json = JsonObject().apply {
            addProperty("operationName", "SearchLiveTags")
            add("variables", JsonObject().apply {
                addProperty("limit", 100)
                addProperty("userQuery", query)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "e184321982c7a58cb6c819e9b7acdac8892f50a9501cd3a36e8cdbd0e427aeac")
                })
            })
        }
        return graphQL.getSearchStreamTags(clientId, json)
    }

    suspend fun loadChannelPanel(channelId: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading panel for channel: $channelId")
        val array = JsonArray(1)
        val panelOperation = JsonObject().apply {
            addProperty("operationName", "ChannelPanels")
            add("variables", JsonObject().apply {
                addProperty("id", channelId)
            })
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("sha256Hash", "236b0ec07489e5172ee1327d114172f27aceca206a1a8053106d60926a7f622e")
                })
            })
        }
        array.add(panelOperation)
        graphQL.getChannelPanel(array).body()?.string()
    }
}