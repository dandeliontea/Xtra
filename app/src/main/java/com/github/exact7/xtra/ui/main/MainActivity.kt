package com.github.exact7.xtra.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.NetworkInfo
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.github.exact7.xtra.R
import com.github.exact7.xtra.di.Injectable
import com.github.exact7.xtra.model.OfflineVideo
import com.github.exact7.xtra.model.User
import com.github.exact7.xtra.model.clip.Clip
import com.github.exact7.xtra.model.game.Game
import com.github.exact7.xtra.model.stream.Stream
import com.github.exact7.xtra.model.video.Video
import com.github.exact7.xtra.ui.Scrollable
import com.github.exact7.xtra.ui.clips.BaseClipsFragment
import com.github.exact7.xtra.ui.common.OnChannelClickedListener
import com.github.exact7.xtra.ui.downloads.DownloadsFragment
import com.github.exact7.xtra.ui.games.GamesFragment
import com.github.exact7.xtra.ui.menu.MenuFragment
import com.github.exact7.xtra.ui.pagers.FollowPagerFragment
import com.github.exact7.xtra.ui.pagers.GamePagerFragment
import com.github.exact7.xtra.ui.pagers.TopPagerFragment
import com.github.exact7.xtra.ui.player.BasePlayerFragment
import com.github.exact7.xtra.ui.player.clip.ClipPlayerFragment
import com.github.exact7.xtra.ui.player.offline.OfflinePlayerFragment
import com.github.exact7.xtra.ui.player.stream.StreamPlayerFragment
import com.github.exact7.xtra.ui.player.video.VideoPlayerFragment
import com.github.exact7.xtra.ui.streams.BaseStreamsFragment
import com.github.exact7.xtra.ui.videos.BaseVideosFragment
import com.github.exact7.xtra.ui.view.draggableview.DraggableListener
import com.github.exact7.xtra.util.C
import com.ncapdevi.fragnav.FragNavController
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject


class MainActivity : AppCompatActivity(), GamesFragment.OnGameSelectedListener, BaseStreamsFragment.OnStreamSelectedListener, OnChannelClickedListener, BaseClipsFragment.OnClipSelectedListener, BaseVideosFragment.OnVideoSelectedListener, HasSupportFragmentInjector, DraggableListener, DownloadsFragment.OnVideoSelectedListener, Injectable {

    companion object {
        private const val PLAYER_TAG = "player"
        const val INDEX_GAMES = FragNavController.TAB1
        const val INDEX_TOP = FragNavController.TAB2
        const val INDEX_FOLLOWED = FragNavController.TAB3
        const val INDEX_DOWNLOADS = FragNavController.TAB4
        const val INDEX_MENU = FragNavController.TAB5
    }

    @Inject lateinit var dispatchingFragmentInjector: DispatchingAndroidInjector<Fragment>
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: MainViewModel
    private var playerFragment: BasePlayerFragment? = null
    private val handler by lazy { Handler() }
    private val fragNavController = FragNavController(supportFragmentManager, R.id.fragmentContainer)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.setNetworkAvailable(intent?.let {
                it.getParcelableExtra<NetworkInfo>("networkInfo").state == NetworkInfo.State.CONNECTED
            } == true)
        }
    }

    //Lifecycle methods

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initNavigation()
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(MainViewModel::class.java)
        viewModel.user.observe(this, Observer {
            if (it == null) {
                navBar.selectedItemId = R.id.fragment_top
            } else {
                navBar.selectedItemId = R.id.fragment_follow
            }
        })
        val user = intent.getParcelableExtra<User>(C.USER)
        fragNavController.initialize(if (user == null) INDEX_TOP else INDEX_FOLLOWED, savedInstanceState)
        viewModel.setUser(user)
        if (viewModel.isPlayerOpened) {
            playerFragment = supportFragmentManager.findFragmentByTag(PLAYER_TAG) as BasePlayerFragment?
        }
        viewModel.playerMaximized().observe(this, Observer {
            if (viewModel.isPlayerOpened) {
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE //TODO change
                if (!isLandscape) {
                    if (it == true) {
                        handler.post { hideNavigationBar() }
                    } else {
                        handler.post { playerFragment?.minimize() } //TODO add minimize fast without callback
                    }
                } else {
                    navBarContainer.visibility = View.GONE
                }
            }
        })
        var notFirst = false
        viewModel.isNetworkAvailable().observe(this, Observer {
            if (notFirst) {
                Toast.makeText(this, getString(if (it) R.string.connection_restored else R.string.no_connection), Toast.LENGTH_LONG).show()
            } else {
                notFirst = true
            }
//            if (it) {
//                offlineView.animate().translationY(offlineView.height.toFloat()).setListener(object : Animator.AnimatorListener {
//                    override fun onAnimationRepeat(animation: Animator?) {
//                    }
//
//                    override fun onAnimationEnd(animation: Animator?) {
//                offlineView.visibility = View.GONE
//                    }
//
//                    override fun onAnimationCancel(animation: Animator?) {
//                    }
//
//                    override fun onAnimationStart(animation: Animator?) {
//                    }
//                })

//            } else {
//                offlineView.visibility = View.VISIBLE
//                offlineView.animate().translationY(0f)
//            }
        })
        registerReceiver(receiver, IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"))
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        fragNavController.onSaveInstanceState(outState)
    }

    /**
     * Result of LoginActivity
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //TODO reset fragments and restart chatview if it's running
        fun update() {
            for (i in 0..2) {
                fragNavController.getStack(i)?.clear()
            }
            viewModel.setUser(data?.getParcelableExtra(C.USER))
        }

        when (requestCode) {
            1 -> { //Was not logged in
                when (resultCode) {
                    RESULT_OK -> { //Logged in
                        update()
                        handler.postDelayed( {fragNavController.replaceFragment(FollowPagerFragment()) }, 5000)
                    }
                }
            }
            2 -> { //Was logged in
                update()
            }
        }
    }

    override fun onBackPressed() {
        if (viewModel.isPlayerOpened && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) { //TODO change
            closePlayer()
            navBarContainer.visibility = View.VISIBLE
            return
        }
        if (!viewModel.isPlayerMaximized) {
            if (fragNavController.isRootFragment) {
                if (viewModel.user.value != null) {
                    if (fragNavController.currentStackIndex != INDEX_FOLLOWED) {
                        navBar.selectedItemId = R.id.fragment_follow
                    } else {
                        super.onBackPressed()
                    }
                } else {
                    if (fragNavController.currentStackIndex != INDEX_TOP) {
                        navBar.selectedItemId = R.id.fragment_top
                    } else {
                        super.onBackPressed()
                    }
                }
            } else {
                fragNavController.popFragment()
            }
        } else {
            playerFragment?.minimize()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        playerFragment?.onWindowFocusChanged(hasFocus)
    }

    //Navigation listeners

    override fun openGame(game: Game) {
        fragNavController.pushFragment(GamePagerFragment.newInstance(game))
    }

    override fun startStream(stream: Stream) {
//        playerFragment?.play(stream)
        startPlayer(StreamPlayerFragment().apply { arguments = bundleOf("stream" to stream) })
    }

    override fun startVideo(video: Video) {
        startPlayer(VideoPlayerFragment().apply { arguments = bundleOf("video" to video) })
    }

    override fun startClip(clip: Clip) {
        startPlayer(ClipPlayerFragment().apply { arguments = bundleOf("clip" to clip) })
    }

    override fun startOfflineVideo(video: OfflineVideo) {
        startPlayer(OfflinePlayerFragment().apply { arguments = bundleOf("video" to video) })
    }

    override fun viewChannel(channelName: String) {
        //TODO
    }

    //DraggableListener

    override fun onMaximized() {
        viewModel.onMaximize()
    }

    override fun onMinimized() {
        viewModel.onMinimize()
    }

    override fun onClosedToLeft() {
        closePlayer()
    }

    override fun onClosedToRight() {
        closePlayer()
    }

    override fun onMoved(horizontalDragOffset: Float, verticalDragOffset: Float) {
        navBarContainer.translationY = -verticalDragOffset * navBarContainer.height + navBarContainer.height
    }

    //Player methods

    private fun startPlayer(fragment: BasePlayerFragment) {
//        if (playerFragment == null) {
        playerFragment = fragment
        supportFragmentManager.beginTransaction().replace(R.id.playerContainer, fragment, PLAYER_TAG).commit()
//        }
        viewModel.onPlayerStarted()
    }

    private fun closePlayer() {
        supportFragmentManager.beginTransaction().remove(playerFragment!!).commit()
        viewModel.onPlayerClosed()
    }

    private fun hideNavigationBar() {
        navBarContainer.translationY = navBarContainer.height.toFloat()
    }



    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return dispatchingFragmentInjector
    }


    private fun initNavigation() {
        fragNavController.apply {
            rootFragments = listOf(GamesFragment(), TopPagerFragment(), FollowPagerFragment(), DownloadsFragment(), MenuFragment())
            fragmentHideStrategy = FragNavController.DETACH_ON_NAVIGATE_HIDE_ON_SWITCH
        }
        navBar.apply {
            setOnNavigationItemSelectedListener {
                val index = when (it.itemId) {
                    R.id.fragment_games -> INDEX_GAMES
                    R.id.fragment_top -> INDEX_TOP
                    R.id.fragment_follow -> INDEX_FOLLOWED
                    R.id.fragment_downloads -> INDEX_DOWNLOADS
                    R.id.fragment_menu -> INDEX_MENU
                    else -> throw IllegalArgumentException()
                }
                fragNavController.switchTab(index)
                true
            }

            setOnNavigationItemReselectedListener {
                val currentFragment = fragNavController.currentFrag
                when (it.itemId) {
                    R.id.fragment_games -> {
                        when (currentFragment) {
                            is GamesFragment -> currentFragment.scrollToTop()
                            else -> fragNavController.popFragment()
                        }
                    }
                    else -> {
                        if (currentFragment is Scrollable) {
                            currentFragment.scrollToTop()
                        }
                    }
                }
            }
        }
    }
}
