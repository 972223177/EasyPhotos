package com.huantansheng.easyphotos.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.huantansheng.easyphotos.R
import com.huantansheng.easyphotos.constant.Code
import com.huantansheng.easyphotos.constant.Key
import com.huantansheng.easyphotos.databinding.ActivityPreviewEasyPhotosBinding
import com.huantansheng.easyphotos.models.album.AlbumModel
import com.huantansheng.easyphotos.models.album.entity.Photo
import com.huantansheng.easyphotos.result.Result
import com.huantansheng.easyphotos.setting.Setting

import com.huantansheng.easyphotos.ui.PreviewFragment.OnPreviewFragmentClickListener
import com.huantansheng.easyphotos.ui.adapter.PreviewPhotosAdapter
import com.huantansheng.easyphotos.utils.Color.ColorUtils
import com.huantansheng.easyphotos.utils.system.SystemUtils
import com.huantansheng.easyphotos.utils.view.gone
import com.huantansheng.easyphotos.utils.view.visible
import java.util.*

/**
 * 预览页
 */
class PreviewActivity : AppCompatActivity(), PreviewPhotosAdapter.OnClickListener,
    View.OnClickListener, OnPreviewFragmentClickListener {
    private val mHideHandler = Handler(Looper.getMainLooper())
    private val mHidePart2Runnable =
        Runnable { SystemUtils.getInstance().systemUiHide(this@PreviewActivity, decorView) }

    private val mShowPart2Runnable = Runnable { // 延迟显示UI元素
        binding.mBottomBar.visible()
        binding.mTopBarLayout.visible()
    }
    private var mVisible = false

    private val decorView: View by lazy(LazyThreadSafetyMode.NONE) {
        window.decorView
    }

    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPreviewEasyPhotosBinding.inflate(layoutInflater)
    }

    private val previewAdapter: PreviewPhotosAdapter by lazy(LazyThreadSafetyMode.NONE) {
        PreviewPhotosAdapter(photos, this)
    }
    private val snapHelper: PagerSnapHelper by lazy(LazyThreadSafetyMode.NONE) {
        PagerSnapHelper()
    }
    private var index = 0
    private val photos = ArrayList<Photo>()
    private var resultCode = RESULT_CANCELED
    private var lastPosition = 0 //记录recyclerView最后一次角标位置，用于判断是否转换了item
    private val isSingle = Setting.count == 1
    private var unable = Result.count() == Setting.count

    private var previewFragment: PreviewFragment? = null
    private var statusColor = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemUtils.getInstance().systemUiInit(this, decorView)
        setContentView(binding.root)
        hideActionBar()
        adaptationStatusBar()
        if (null == AlbumModel.instance) {
            finish()
            return
        }
        initData()
        initView()
    }
    @Suppress("DEPRECATION")
    private fun adaptationStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            statusColor = ContextCompat.getColor(this, R.color.easy_photos_status_bar)
            if (ColorUtils.isWhiteColor(statusColor)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            }
        }
    }

    private fun hideActionBar() {
        val actionBar = supportActionBar
        actionBar?.hide()
    }

    private fun initData() {
        val intent = intent
        val albumItemIndex = intent.getIntExtra(Key.PREVIEW_ALBUM_ITEM_INDEX, 0)
        photos.clear()
        if (albumItemIndex == -1) {
            photos.addAll(Result.photos)
        } else {
            photos.addAll(AlbumModel.instance.getCurrAlbumItemPhotos(albumItemIndex))
        }
        index = intent.getIntExtra(Key.PREVIEW_PHOTO_INDEX, 0)
        lastPosition = index
        mVisible = true
    }

    private fun toggle() {
        if (mVisible) {
            hide()
        } else {
            show()
        }
    }

    private fun hide() {
        // Hide UI first
        val hideAnimation = AlphaAnimation(1.0f, 0.0f)
        hideAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding.mBottomBar.gone()
                binding.mTopBarLayout.gone()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        hideAnimation.duration = UI_ANIMATION_DELAY.toLong()
        binding.mBottomBar.startAnimation(hideAnimation)
        binding.mTopBarLayout.startAnimation(hideAnimation)
        mVisible = false

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable)
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY.toLong())
    }

    private fun show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 16) {
            SystemUtils.getInstance().systemUiShow(this, decorView)
        }
        mVisible = true

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable)
        mHideHandler.post(mShowPart2Runnable)
    }

    override fun onPhotoClick() {
        toggle()
    }

    override fun onPhotoScaleChanged() {
        if (mVisible) hide()
    }

    override fun onBackPressed() {
        doBack()
    }

    private fun doBack() {
        val intent = Intent()
        intent.putExtra(Key.PREVIEW_CLICK_DONE, false)
        setResult(resultCode, intent)
        finish()
    }

    private fun initView() {
        if (!SystemUtils.getInstance().hasNavigationBar(this)) {
            val mRootView = findViewById<View>(R.id.m_root_view) as FrameLayout
            mRootView.fitsSystemWindows = true
            binding.mTopBarLayout.setPadding(
                0,
                SystemUtils.getInstance().getStatusBarHeight(this),
                0,
                0
            )
            if (ColorUtils.isWhiteColor(statusColor)) {
                SystemUtils.getInstance().setStatusDark(this, true)
            }
        }
        previewFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_preview) as PreviewFragment?
        if (Setting.showOriginalMenu) {
            processOriginalMenu()
        } else {
            binding.tvOriginal.gone()
        }
        with(binding) {
            setClick(ivBack, tvEdit, tvSelector, tvOriginal, tvDone, ivSelector)
        }
        initRecyclerView()
        shouldShowMenuDone()
    }

    private fun initRecyclerView() {
        with(binding.rvPhotos) {
            adapter = previewAdapter
            val lm =
                LinearLayoutManager(this@PreviewActivity, LinearLayoutManager.HORIZONTAL, false)
            layoutManager = lm
            scrollToPosition(index)
            toggleSelector()
            snapHelper.attachToRecyclerView(this)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    val view = snapHelper.findSnapView(lm) ?: return
                    val position = lm.getPosition(view)
                    if (lastPosition == position) {
                        return
                    }
                    lastPosition = position
                    previewFragment!!.setSelectedPosition(-1)
                    binding.tvNumber.text = getString(
                        R.string.preview_current_number_easy_photos,
                        lastPosition + 1, photos.size
                    )
                    toggleSelector()
                }
            })
            binding.tvNumber.text = getString(
                R.string.preview_current_number_easy_photos, index + 1,
                photos.size
            )
        }
    }

    private var clickDone = false
    override fun onClick(v: View) {
        when (v.id) {
            R.id.iv_back -> {
                doBack()
            }
            R.id.tv_selector -> {
                updateSelector()
            }
            R.id.iv_selector -> {
                updateSelector()
            }
            R.id.tv_original -> {
                if (!Setting.originalMenuUsable) {
                    Toast.makeText(
                        applicationContext,
                        Setting.originalMenuUnusableHint,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                Setting.selectedOriginal = !Setting.selectedOriginal
                processOriginalMenu()
            }
            R.id.tv_done -> {
                if (clickDone) return
                clickDone = true
                val intent = Intent()
                intent.putExtra(Key.PREVIEW_CLICK_DONE, true)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun processOriginalMenu() {
        if (Setting.selectedOriginal) {
            binding.tvOriginal.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.easy_photos_fg_accent
                )
            )
        } else {
            if (Setting.originalMenuUsable) {
                binding.tvOriginal.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.easy_photos_fg_primary
                    )
                )
            } else {
                binding.tvOriginal.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.easy_photos_fg_primary_dark
                    )
                )
            }
        }
    }

    private fun toggleSelector() {
        if (photos[lastPosition].selected) {
            binding.ivSelector.setImageResource(R.drawable.ic_selector_true_easy_photos)
            if (!Result.isEmpty()) {
                val count = Result.count()
                for (i in 0 until count) {
                    if (photos[lastPosition].path == Result.getPhotoPath(i)) {
                        previewFragment!!.setSelectedPosition(i)
                        break
                    }
                }
            }
        } else {
            binding.ivSelector.setImageResource(R.drawable.ic_selector_easy_photos)
        }
        previewFragment!!.notifyDataSetChanged()
        shouldShowMenuDone()
    }

    private fun updateSelector() {
        resultCode = RESULT_OK
        val item = photos[lastPosition]
        if (isSingle) {
            singleSelector(item)
            return
        }
        if (unable) {
            if (item.selected) {
                Result.removePhoto(item)
                if (unable) {
                    unable = false
                }
                toggleSelector()
                return
            }
            when {
                Setting.isOnlyVideo() -> {
                    Toast.makeText(
                        applicationContext, getString(
                            R.string.selector_reach_max_video_hint_easy_photos, Setting.count
                        ), Toast.LENGTH_SHORT
                    ).show()
                }
                Setting.showVideo -> {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.selector_reach_max_hint_easy_photos),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        applicationContext, getString(
                            R.string.selector_reach_max_image_hint_easy_photos,
                            Setting.count
                        ), Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }
        item.selected = !item.selected
        if (item.selected) {
            val res = Result.addPhoto(item)
            if (res != 0) {
                item.selected = false
                when (res) {
                    Result.PICTURE_OUT -> Toast.makeText(
                        applicationContext,
                        getString(
                            R.string.selector_reach_max_image_hint_easy_photos,
                            Setting.complexPictureCount
                        ), Toast.LENGTH_SHORT
                    ).show()
                    Result.VIDEO_OUT -> Toast.makeText(
                        applicationContext,
                        getString(
                            R.string.selector_reach_max_video_hint_easy_photos,
                            Setting.complexVideoCount
                        ), Toast.LENGTH_SHORT
                    ).show()
                    Result.SINGLE_TYPE -> Toast.makeText(
                        applicationContext,
                        getString(R.string.selector_single_type_hint_easy_photos),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            if (Result.count() == Setting.count) {
                unable = true
            }
        } else {
            Result.removePhoto(item)
            previewFragment!!.setSelectedPosition(-1)
            if (unable) {
                unable = false
            }
        }
        toggleSelector()
    }

    private fun singleSelector(photo: Photo) {
        if (!Result.isEmpty()) {
            if (Result.getPhotoPath(0) == photo.path) {
                Result.removePhoto(photo)
            } else {
                Result.removePhoto(0)
                Result.addPhoto(photo)
            }
        } else {
            Result.addPhoto(photo)
        }
        toggleSelector()
    }

    private fun shouldShowMenuDone() {
        if (Result.isEmpty()) {
            if (View.VISIBLE == binding.tvDone.visibility) {
                val scaleHide = ScaleAnimation(1f, 0f, 1f, 0f)
                scaleHide.duration = 200
                binding.tvDone.startAnimation(scaleHide)
            }
            binding.tvDone.gone()
            binding.flFragment.gone()
        } else {
            if (View.GONE == binding.tvDone.visibility) {
                val scaleShow = ScaleAnimation(0f, 1f, 0f, 1f)
                scaleShow.duration = 200
                binding.tvDone.startAnimation(scaleShow)
            }
            binding.flFragment.visible()
            binding.tvDone.visible()
            binding.tvDone.text = getString(
                R.string.selector_action_done_easy_photos,
                Result.count(),
                Setting.count
            )
        }
    }

    override fun onPreviewPhotoClick(position: Int) {
        val path = Result.getPhotoPath(position)
        val size = photos.size
        for (i in 0 until size) {
            if (TextUtils.equals(path, photos[i].path)) {
                binding.rvPhotos.scrollToPosition(i)
                lastPosition = i
                binding.tvNumber.text = getString(
                    R.string.preview_current_number_easy_photos,
                    lastPosition + 1, photos.size
                )
                previewFragment!!.setSelectedPosition(position)
                toggleSelector()
                return
            }
        }
    }

    private fun setClick(vararg views: View) {
        for (v in views) {
            v.setOnClickListener(this)
        }
    }

    companion object {
        fun start(act: Activity, albumItemIndex: Int, currIndex: Int) {
            val intent = Intent(act, PreviewActivity::class.java)
            intent.putExtra(Key.PREVIEW_ALBUM_ITEM_INDEX, albumItemIndex)
            intent.putExtra(Key.PREVIEW_PHOTO_INDEX, currIndex)
            act.startActivityForResult(intent, Code.REQUEST_PREVIEW_ACTIVITY)
        }

        /**
         * 一些旧设备在UI小部件更新之间需要一个小延迟
         * and a change of the status and navigation bar.
         */
        private const val UI_ANIMATION_DELAY = 300
    }
}