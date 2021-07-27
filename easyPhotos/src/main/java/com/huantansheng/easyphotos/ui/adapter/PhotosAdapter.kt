package com.huantansheng.easyphotos.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.huantansheng.easyphotos.R
import com.huantansheng.easyphotos.constant.Type
import com.huantansheng.easyphotos.databinding.ItemAdEasyPhotosBinding
import com.huantansheng.easyphotos.databinding.ItemCameraEasyPhotosBinding
import com.huantansheng.easyphotos.databinding.ItemRvPhotosEasyPhotosBinding
import com.huantansheng.easyphotos.models.ad.AdViewHolder
import com.huantansheng.easyphotos.models.album.entity.Photo
import com.huantansheng.easyphotos.result.Result
import com.huantansheng.easyphotos.setting.Setting
import com.huantansheng.easyphotos.ui.widget.PressedImageView
import com.huantansheng.easyphotos.utils.media.DurationUtils
import com.huantansheng.easyphotos.utils.view.debounceClick
import java.lang.ref.WeakReference
import java.util.*

/**
 * 专辑相册适配器
 * Created by huan on 2017/10/23.
 */
class PhotosAdapter(
    private val dataList: ArrayList<Any?>,
    private val listener: OnClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var unable: Boolean = Result.count() == Setting.count
    private val isSingle: Boolean = Setting.count == 1
    private var singlePosition = 0
    private var clearAd = false


    fun change() {
        unable = Result.count() == Setting.count
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_AD -> return AdViewHolder(
                ItemAdEasyPhotosBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            TYPE_CAMERA -> return CameraViewHolder(
                ItemCameraEasyPhotosBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            else -> return PhotoViewHolder(
                ItemRvPhotosEasyPhotosBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PhotoViewHolder -> {
                val item: Photo = dataList[position] as? Photo
                    ?: return
                updateSelector(holder.tvSelector, item.selected, item, position)
                val path = item.path
                val uri = item.uri
                val type = item.type
                val duration = item.duration
                val isGif = path.endsWith(Type.GIF) || type.endsWith(Type.GIF)
                if (Setting.showGif && isGif) {
                    Setting.imageEngine.loadGifAsBitmap(holder.ivPhoto.context, uri, holder.ivPhoto)
                    holder.tvType.setText(R.string.gif_easy_photos)
                    holder.tvType.visibility = View.VISIBLE
                    holder.ivVideo.visibility = View.GONE
                } else if (Setting.showVideo && type.contains(Type.VIDEO)) {
                    Setting.imageEngine.loadPhoto(holder.ivPhoto.context, uri, holder.ivPhoto)
                    holder.tvType.text = DurationUtils.format(duration)
                    holder.tvType.visibility = View.VISIBLE
                    holder.ivVideo.visibility = View.VISIBLE
                } else {
                    Setting.imageEngine.loadPhoto(holder.ivPhoto.context, uri, holder.ivPhoto)
                    holder.tvType.visibility = View.GONE
                    holder.ivVideo.visibility = View.GONE
                }
                holder.vSelector.visibility = View.VISIBLE
                holder.tvSelector.visibility = View.VISIBLE
                holder.ivPhoto.debounceClick {
                    var realPosition = position
                    if (Setting.hasPhotosAd()) {
                        realPosition--
                    }
                    if (Setting.isShowCamera && !Setting.isBottomRightCamera()) {
                        realPosition--
                    }
                    listener.onPhotoClick(position, realPosition)
                }
                holder.vSelector.debounceClick {
                    if (isSingle) {
                        singleSelector(item, position)
                        return@debounceClick
                    }
                    if (unable) {
                        if (item.selected) {
                            Result.removePhoto(item)
                            if (unable) {
                                unable = false
                            }
                            listener.onSelectorChanged()
                            notifyDataSetChanged()
                            return@debounceClick
                        }
                        listener.onSelectorOutOfMax(null)
                        return@debounceClick
                    }
                    item.selected = !item.selected
                    if (item.selected) {
                        val res = Result.addPhoto(item)
                        if (res != 0) {
                            listener.onSelectorOutOfMax(res)
                            item.selected = false
                            return@debounceClick
                        }
                        holder.tvSelector.setBackgroundResource(R.drawable.bg_select_true_easy_photos)
                        holder.tvSelector.text = Result.count().toString()
                        if (Result.count() == Setting.count) {
                            unable = true
                            notifyDataSetChanged()
                        }
                    } else {
                        Result.removePhoto(item)
                        if (unable) {
                            unable = false
                        }
                        notifyDataSetChanged()
                    }
                    listener.onSelectorChanged()
                }
                return
            }
            is AdViewHolder -> {
                if (clearAd) {
                    holder.adFrame.removeAllViews()
                    holder.adFrame.visibility = View.GONE
                    return
                }
                if (!Setting.photoAdIsOk) {
                    holder.adFrame.visibility = View.GONE
                    return
                }
                val weakReference = dataList[position] as? WeakReference<*>
                if (null != weakReference) {
                    val adView = weakReference.get() as View?
                    if (null != adView) {
                        if (null != adView.parent) {
                            if (adView.parent is FrameLayout) {
                                (adView.parent as FrameLayout).removeAllViews()
                            }
                        }
                        holder.adFrame.visibility = View.VISIBLE
                        holder.adFrame.removeAllViews()
                        holder.adFrame.addView(adView)
                    }
                }
            }
            is CameraViewHolder -> {
                holder.flCamera.debounceClick { listener.onCameraClick() }
            }
        }
    }

    fun clearAd() {
        clearAd = true
        notifyDataSetChanged()
    }

    private fun singleSelector(photo: Photo, position: Int) {
        if (!Result.isEmpty()) {
            if ((Result.getPhotoPath(0) == photo.path)) {
                Result.removePhoto(photo)
            } else {
                Result.removePhoto(0)
                Result.addPhoto(photo)
                notifyItemChanged(singlePosition)
            }
        } else {
            Result.addPhoto(photo)
        }
        notifyItemChanged(position)
        listener.onSelectorChanged()
    }

    private fun updateSelector(
        tvSelector: TextView,
        selected: Boolean,
        photo: Photo,
        position: Int
    ) {
        if (selected) {
            val number = Result.getSelectorNumber(photo)
            if ((number == "0")) {
                tvSelector.setBackgroundResource(R.drawable.bg_select_false_easy_photos)
                tvSelector.text = null
                return
            }
            tvSelector.text = number
            tvSelector.setBackgroundResource(R.drawable.bg_select_true_easy_photos)
            if (isSingle) {
                singlePosition = position
                tvSelector.text = "1"
            }
        } else {
            if (unable) {
                tvSelector.setBackgroundResource(R.drawable.bg_select_false_unable_easy_photos)
            } else {
                tvSelector.setBackgroundResource(R.drawable.bg_select_false_easy_photos)
            }
            tvSelector.text = null
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun getItemViewType(position: Int): Int {
        if (0 == position) {
            if (Setting.hasPhotosAd()) {
                return TYPE_AD
            }
            if (Setting.isShowCamera && !Setting.isBottomRightCamera()) {
                return TYPE_CAMERA
            }
        }
        if (1 == position && !Setting.isBottomRightCamera()) {
            if (Setting.hasPhotosAd() && Setting.isShowCamera) {
                return TYPE_CAMERA
            }
        }
        return TYPE_ALBUM_ITEMS
    }

    interface OnClickListener {
        fun onCameraClick()
        fun onPhotoClick(position: Int, realPosition: Int)
        fun onSelectorOutOfMax(result: Int?)
        fun onSelectorChanged()
    }

    private inner class CameraViewHolder(binding: ItemCameraEasyPhotosBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val flCamera: FrameLayout = binding.flCamera

    }

    inner class PhotoViewHolder internal constructor(binding: ItemRvPhotosEasyPhotosBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val ivPhoto: PressedImageView = binding.ivPhoto
        val tvSelector: AppCompatTextView = binding.tvSelector
        val vSelector: View = binding.vSelector
        val tvType: AppCompatTextView = binding.tvType
        val ivVideo: AppCompatImageView = binding.ivPlay

    }

    companion object {
        private const val TYPE_AD = 0
        private const val TYPE_CAMERA = 1
        private const val TYPE_ALBUM_ITEMS = 2
    }


}