package com.huantansheng.easyphotos.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.huantansheng.easyphotos.databinding.ItemAdEasyPhotosBinding
import com.huantansheng.easyphotos.databinding.ItemDialogAlbumItemsEasyPhotosBinding
import com.huantansheng.easyphotos.models.ad.AdViewHolder
import com.huantansheng.easyphotos.models.album.entity.AlbumItem
import com.huantansheng.easyphotos.setting.Setting
import com.huantansheng.easyphotos.utils.view.debounceClick
import com.huantansheng.easyphotos.utils.view.invisible
import com.huantansheng.easyphotos.utils.view.visible
import java.lang.ref.WeakReference
import java.util.*

/**
 * 媒体列表适配器
 * Created by huan on 2017/10/23.
 */
class AlbumItemsAdapter(
    private val dataList: ArrayList<Any>,
    initialPosition: Int,
    private val listener: OnClickListener?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var selectedPosition: Int = initialPosition
    private var adPosition = 0
    private var padding = 0
    private var clearAd = false

    interface OnClickListener {
        fun onAlbumItemClick(position: Int, realPosition: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_AD -> AdViewHolder(
                ItemAdEasyPhotosBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            else -> AlbumItemsViewHolder(
                ItemDialogAlbumItemsEasyPhotosBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AlbumItemsViewHolder -> {
                if (padding == 0) {
                    padding = holder.mRoot.paddingLeft
                }
                if (position == itemCount - 1) {
                    holder.mRoot.setPadding(
                        padding,
                        padding,
                        padding,
                        padding
                    )
                } else {
                    holder.mRoot.setPadding(padding, padding, padding, 0)
                }
                val item = dataList[position] as AlbumItem
                Setting.imageEngine.loadPhoto(
                    holder.ivAlbumCover.context,
                    item.coverImageUri,
                    holder.ivAlbumCover
                )
                holder.tvAlbumName.text = item.name
                holder.tvAlbumPhotosCount.text = item.photos.size.toString()
                if (selectedPosition == position) {
                    holder.ivSelected.visible()
                } else {
                    holder.ivSelected.invisible()
                }
                holder.itemView.debounceClick {
                    var realPosition = position
                    if (Setting.hasAlbumItemsAd()) {
                        if (position > adPosition) {
                            realPosition--
                        }
                    }
                    val tempSelected = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(tempSelected)
                    notifyItemChanged(position)
                    listener?.onAlbumItemClick(position, realPosition)
                }
            }
            is AdViewHolder -> {
                if (clearAd) {
                    holder.adFrame.removeAllViews()
                    holder.adFrame.visibility = View.GONE
                    return
                }
                adPosition = position
                if (!Setting.albumItemsAdIsOk) {
                    holder.adFrame.visibility = View.GONE
                    return
                }
                val weakReference =
                    dataList[position] as? WeakReference<*>
                if (null != weakReference) {
                    val adView = weakReference.get() as? View
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
        }

    }

    fun clearAd() {
        clearAd = true
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        var realPosition = position
        if (Setting.hasAlbumItemsAd()) {
            if (position > adPosition) {
                realPosition--
            }
        }
        val tempSelected = selectedPosition
        selectedPosition = position
        notifyItemChanged(tempSelected)
        notifyItemChanged(position)
        listener?.onAlbumItemClick(position, realPosition)
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun getItemViewType(position: Int): Int {
        val item = dataList[position]
        return if (item is WeakReference<*>) {
            TYPE_AD
        } else {
            TYPE_ALBUM_ITEMS
        }
    }

    inner class AlbumItemsViewHolder internal constructor(binding: ItemDialogAlbumItemsEasyPhotosBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val ivAlbumCover: AppCompatImageView = binding.ivAlbumCover
        val tvAlbumName: AppCompatTextView = binding.tvAlbumName
        val tvAlbumPhotosCount: AppCompatTextView = binding.tvAlbumPhotosCount
        val ivSelected: AppCompatImageView = binding.ivSelected
        val mRoot: ConstraintLayout = binding.mRootView
    }

    companion object {
        private const val TYPE_AD = 0
        private const val TYPE_ALBUM_ITEMS = 1
    }

}