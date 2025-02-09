package com.github.andreyasadchy.xtra.ui.follow.channels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.User
import com.github.andreyasadchy.xtra.model.helix.follows.Follow
import com.github.andreyasadchy.xtra.model.helix.follows.Order
import com.github.andreyasadchy.xtra.model.helix.follows.Sort
import com.github.andreyasadchy.xtra.repository.Listing
import com.github.andreyasadchy.xtra.repository.TwitchService
import com.github.andreyasadchy.xtra.ui.common.PagedListViewModel
import javax.inject.Inject

class FollowedChannelsViewModel @Inject constructor(
        context: Application,
        private val repository: TwitchService) : PagedListViewModel<Follow>() {

    private val _sortText = MutableLiveData<CharSequence>()
    val sortText: LiveData<CharSequence>
        get() = _sortText
    private val filter = MutableLiveData<Filter>()
    override val result: LiveData<Listing<Follow>> = Transformations.map(filter) {
        repository.loadFollowedChannels(it.gqlClientId, it.helixClientId, it.user.token, it.user.id, it.sort, it.order, viewModelScope)
    }
    val sort: Sort
        get() = filter.value!!.sort
    val order: Order
        get() = filter.value!!.order

    init {
        _sortText.value = context.getString(R.string.sort_and_order, context.getString(R.string.last_broadcast), context.getString(R.string.descending))
    }

    fun setUser(gqlClientId: String?, helixClientId: String?, user: User) {
        if (filter.value == null) {
            filter.value = Filter(gqlClientId, helixClientId, user)
        }
    }

    fun filter(gqlClientId: String?, helixClientId: String?, user: User, sort: Sort, order: Order, text: CharSequence) {
        filter.value = filter.value?.copy(gqlClientId = gqlClientId, helixClientId = helixClientId, user = user, sort = sort, order = order)
        _sortText.value = text
    }

    private data class Filter(
        val gqlClientId: String?,
        val helixClientId: String?,
        val user: User,
        val sort: Sort = Sort.LAST_BROADCAST,
        val order: Order = Order.DESC)
}
