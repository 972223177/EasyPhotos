package com.huantansheng.easyphotos.ui.adapter

import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.OnStateChangedListener
import com.github.chrisbanes.photoview.PhotoView
import com.huantansheng.easyphotos.R
import com.huantansheng.easyphotos.constant.Type
import com.huantansheng.easyphotos.databinding.ItemPreviewPhotoEasyPhotosBinding
import com.huantansheng.easyphotos.models.album.entity.Photo
import com.huantansheng.easyphotos.setting.Setting
import com.huantansheng.easyphotos.ui.adapter.PreviewPhotosAdapter.PreviewPhotosViewHolder
import java.io.File
import java.util.*

/**
 * 大图预览界面图片集合的适配器
 * Created by huan on 2017/10/26.
 */
class PreviewPhotosAdapter(
    private val photos: ArrayList<Photo>,
    private val listener: OnClickListener?
) : RecyclerView.Adapter<PreviewPhotosViewHolder>() {

    interface OnClickListener {
        fun onPhotoClick()
        fun onPhotoScaleChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewPhotosViewHolder {
        return PreviewPhotosViewHolder(
            ItemPreviewPhotoEasyPhotosBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: PreviewPhotosViewHolder, position: Int) {
        val uri = photos[position].uri
        val path = photos[position].path
        val type = photos[position].type
        val ratio = photos[position].height.toDouble() / photos[position].width.toDouble()
        holder.ivPlay.visibility = View.GONE
        holder.ivPhotoView.visibility = View.GONE
        holder.ivLongPhoto.visibility = View.GONE
        if (type.contains(Type.VIDEO)) {
            holder.ivPhotoView.visibility = View.VISIBLE
            Setting.imageEngine.loadPhoto(holder.ivPhotoView.context, uri, holder.ivPhotoView)
            holder.ivPlay.visibility = View.VISIBLE
            holder.ivPlay.setOnClickListener { v -> toPlayVideo(v, uri, type) }
        } else if (path.endsWith(Type.GIF) || type.endsWith(Type.GIF)) {
            holder.ivPhotoView.visibility = View.VISIBLE
            Setting.imageEngine.loadGif(holder.ivPhotoView.context, uri, holder.ivPhotoView)
        } else {
            if (ratio > 2.3) {
                holder.ivLongPhoto.visibility = View.VISIBLE
                holder.ivLongPhoto.setImage(ImageSource.uri(path))
            } else {
                holder.ivPhotoView.visibility = View.VISIBLE
                Setting.imageEngine.loadPhoto(
                    holder.ivPhotoView.context, uri,
                    holder.ivPhotoView
                )
            }
        }
        holder.ivLongPhoto.setOnClickListener { listener?.onPhotoClick() }
        holder.ivPhotoView.setOnClickListener { listener?.onPhotoClick() }
        holder.ivLongPhoto.setOnStateChangedListener(object : OnStateChangedListener {
            override fun onScaleChanged(newScale: Float, origin: Int) {
                listener?.onPhotoScaleChanged()
            }

            override fun onCenterChanged(newCenter: PointF, origin: Int) {}
        })
        holder.ivPhotoView.scale = 1f
        holder.ivPhotoView.setOnScaleChangeListener { _, _, _ -> listener?.onPhotoScaleChanged() }
    }

    private fun toPlayVideo(v: View, uri: Uri, type: String) {
        val context = v.context
        val intent = Intent(Intent.ACTION_VIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        intent.setDataAndType(uri, type)
        context.startActivity(intent)
    }

    private fun getUri(context: Context, path: String, intent: Intent): Uri {
        val file = File(path)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            FileProvider.getUriForFile(context, Setting.fileProviderAuthority, file)
        } else {
            Uri.fromFile(file)
        }
    }

    override fun getItemCount(): Int {
        return photos.size
    }

    inner class PreviewPhotosViewHolder internal constructor(binding: ItemPreviewPhotoEasyPhotosBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val ivLongPhoto: SubsamplingScaleImageView = binding.ivLongPhoto
        val ivPlay: ImageView = binding.ivPlay
        val ivPhotoView: PhotoView = binding.ivPhotoView
    }

}