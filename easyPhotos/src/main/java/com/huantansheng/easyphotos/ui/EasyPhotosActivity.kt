package com.huantansheng.easyphotos.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.huantansheng.easyphotos.EasyPhotos
import com.huantansheng.easyphotos.R
import com.huantansheng.easyphotos.constant.Code
import com.huantansheng.easyphotos.constant.Key
import com.huantansheng.easyphotos.databinding.ActivityEasyPhotosBinding
import com.huantansheng.easyphotos.models.ad.AdListener
import com.huantansheng.easyphotos.models.album.AlbumModel
import com.huantansheng.easyphotos.models.album.AlbumModel.CallBack
import com.huantansheng.easyphotos.models.album.entity.Photo
import com.huantansheng.easyphotos.result.Result
import com.huantansheng.easyphotos.setting.Setting
import com.huantansheng.easyphotos.ui.adapter.AlbumItemsAdapter
import com.huantansheng.easyphotos.ui.adapter.PhotosAdapter
import com.huantansheng.easyphotos.ui.dialog.LoadingDialog
import com.huantansheng.easyphotos.utils.Color.ColorUtils
import com.huantansheng.easyphotos.utils.String.StringUtils
import com.huantansheng.easyphotos.utils.bitmap.BitmapUtils
import com.huantansheng.easyphotos.utils.media.DurationUtils
import com.huantansheng.easyphotos.utils.media.MediaScannerConnectionUtils
import com.huantansheng.easyphotos.utils.permission.PermissionUtil
import com.huantansheng.easyphotos.utils.permission.PermissionUtil.PermissionCallBack
import com.huantansheng.easyphotos.utils.settings.SettingsUtils
import com.huantansheng.easyphotos.utils.system.SystemUtils
import com.huantansheng.easyphotos.utils.uri.UriUtils
import com.huantansheng.easyphotos.utils.view.gone
import com.huantansheng.easyphotos.utils.view.invisible
import com.huantansheng.easyphotos.utils.view.visible
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

open class EasyPhotosActivity : AppCompatActivity(), AlbumItemsAdapter.OnClickListener,
    PhotosAdapter.OnClickListener, AdListener, View.OnClickListener {
    private var mTempImageFile: File? = null
    private var albumModel: AlbumModel? = null
    private val photoList = ArrayList<Any?>()
    private val albumItemList = ArrayList<Any>()
    private val resultList = ArrayList<Photo>()

    private val photosAdapter: PhotosAdapter by lazy(LazyThreadSafetyMode.NONE) {
        PhotosAdapter(this, photoList, this)
    }

    private val albumItemsAdapter: AlbumItemsAdapter by lazy(LazyThreadSafetyMode.NONE) {
        AlbumItemsAdapter(this, albumItemList, 0, this)
    }

    private var gridLayoutManager: GridLayoutManager? = null


    private val binding: ActivityEasyPhotosBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityEasyPhotosBinding.inflate(layoutInflater)
    }

    private val setHide: AnimatorSet by lazy(LazyThreadSafetyMode.NONE) {
        val translationHide = ObjectAnimator.ofFloat(
            binding.rvAlbumItems, "translationY", 0f,
            binding.mBottomBar.top.toFloat()
        )
        val alphaHide = ObjectAnimator.ofFloat(binding.rootViewAlbumItems, "alpha", 1.0f, 0.0f)
        translationHide.duration = 200
        AnimatorSet().also {
            it.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    binding.rootViewAlbumItems.gone()
                }
            })
            it.interpolator = AccelerateInterpolator()
            it.play(translationHide).with(alphaHide)
        }
    }
    private val setShow: AnimatorSet by lazy(LazyThreadSafetyMode.NONE) {
        val translationShow = ObjectAnimator.ofFloat(
            binding.rvAlbumItems, "translationY",
            binding.mBottomBar.top.toFloat(), 0f
        )
        val alphaShow = ObjectAnimator.ofFloat(binding.rootViewAlbumItems, "alpha", 0.0f, 1.0f)
        translationShow.duration = 300
        AnimatorSet().also {
            it.interpolator = AccelerateDecelerateInterpolator()
            it.play(translationShow).with(alphaShow)
        }
    }
    private var currAlbumItemIndex = 0

    private var isQ = false
    var loadingDialog: LoadingDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        hideActionBar()
        adaptationStatusBar()
        loadingDialog = LoadingDialog.get(this)
        isQ = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
        if (!Setting.onlyStartCamera && null == Setting.imageEngine) {
            finish()
            return
        }
        initSomeViews()
        if (PermissionUtil.checkAndRequestPermissionsInActivity(this, *needPermissions)) {
            hasPermissions()
        } else {
            binding.rlPermissionsView.visibility = View.VISIBLE
        }
    }

    private fun adaptationStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var statusColor = window.statusBarColor
            if (statusColor == Color.TRANSPARENT) {
                statusColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)
            }
            if (ColorUtils.isWhiteColor(statusColor)) {
                SystemUtils.getInstance().setStatusDark(this, true)
            }
        }
    }

    private fun initSomeViews() {
        if (Setting.isOnlyVideo()) {
            binding.tvTitle.text = getString(R.string.video_selection_easy_photos)
        }
        binding.ivSecondMenu.visibility =
            if (Setting.showPuzzleMenu || Setting.showCleanMenu || Setting.showOriginalMenu) View.VISIBLE else View.GONE
        binding.ivBack.setOnClickListener(this)
    }

    private fun hasPermissions() {
        binding.rlPermissionsView.visibility = View.GONE
        if (Setting.onlyStartCamera) {
            Code.REQUEST_CAMERA.launchCamera()
            return
        }
        val albumModelCallBack = CallBack {
            runOnUiThread {
                loadingDialog!!.dismiss()
                onAlbumWorkedDo()
            }
        }
        loadingDialog!!.show()
        albumModel = AlbumModel.getInstance()
        albumModel?.query(this, albumModelCallBack)
    }

    protected val needPermissions: Array<String>
        get() {
            if (Setting.isShowCamera) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                } else arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } else {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                } else arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionUtil.onPermissionResult(this, permissions, grantResults,
            object : PermissionCallBack {
                override fun onSuccess() {
                    hasPermissions()
                }

                override fun onShouldShow() {
                    binding.tvPermission.setText(R.string.permissions_again_easy_photos)
                    binding.rlPermissionsView.setOnClickListener {
                        if (PermissionUtil.checkAndRequestPermissionsInActivity(
                                this@EasyPhotosActivity,
                                *needPermissions
                            )
                        ) {
                            hasPermissions()
                        }
                    }
                }

                override fun onFailed() {
                    binding.tvPermission.setText(R.string.permissions_die_easy_photos)
                    binding.rlPermissionsView.setOnClickListener {
                        SettingsUtils.startMyApplicationDetailsForResult(
                            this@EasyPhotosActivity,
                            packageName
                        )
                    }
                }
            })
    }

    /**
     * 启动相机
     *
     * @param this@launchCamera startActivityForResult的请求码
     */
    @Suppress("KDocUnresolvedReference")
    private fun Int.launchCamera() {
        if (TextUtils.isEmpty(Setting.fileProviderAuthority)) throw RuntimeException("AlbumBuilder" + " : 请执行 setFileProviderAuthority()方法")
        if (!cameraIsCanUse()) {
            binding.rlPermissionsView.visibility = View.VISIBLE
            binding.tvPermission.setText(R.string.permissions_die_easy_photos)
            binding.rlPermissionsView.setOnClickListener {
                SettingsUtils.startMyApplicationDetailsForResult(
                    this@EasyPhotosActivity,
                    packageName
                )
            }
            return
        }
        toAndroidCamera(this)
    }

    private var photoUri: Uri? = null

    /**
     * 启动系统相机
     *
     * @param requestCode 请求相机的请求码
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun toAndroidCamera(requestCode: Int) {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null ||
            this.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        ) {
            if (isQ) {
                photoUri = createImageUri()
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                startActivityForResult(cameraIntent, requestCode)
                return
            }
            createCameraTempImageFile()
            if (mTempImageFile != null && mTempImageFile!!.isFile) {
                val imageUri = UriUtils.getUri(this, mTempImageFile)
                cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) //对目标应用临时授权该Uri所代表的文件
                cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) //对目标应用临时授权该Uri所代表的文件
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri) //将拍取的照片保存到指定URI
                startActivityForResult(cameraIntent, requestCode)
            } else {
                Toast.makeText(
                    applicationContext, R.string.camera_temp_file_error_easy_photos,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                applicationContext,
                R.string.msg_no_camera_easy_photos,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 创建图片地址uri,用于保存拍照后的照片 Android 10以后使用这种方法
     */
    private fun createImageUri(): Uri? {
        //设置保存参数到ContentValues中
        val contentValues = ContentValues()
        //设置文件名
        contentValues.put(
            MediaStore.Images.Media.DISPLAY_NAME,
            System.currentTimeMillis().toString()
        )
        //兼容Android Q和以下版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //android Q中不再使用DATA字段，而用RELATIVE_PATH代替
            //RELATIVE_PATH是相对路径不是绝对路径;照片存储的地方为：存储/Pictures
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures")
        }
        //设置文件类型
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/JPEG")
        //执行insert操作，向系统文件夹中添加文件
        //EXTERNAL_CONTENT_URI代表外部存储器，该值不变
        return contentResolver.insert(
            MediaStore.Images.Media.getContentUri("external"),
            contentValues
        )
    }

    @Suppress("DEPRECATION")
    private fun createCameraTempImageFile() {
        var dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (null == dir) {
            dir = File(
                Environment.getExternalStorageDirectory(),
                File.separator + "DCIM" + File.separator + "Camera" + File.separator
            )
        }
        if (!dir.isDirectory) {
            if (!dir.mkdirs()) {
                dir = getExternalFilesDir(null)
                if (null == dir || !dir.exists()) {
                    dir = filesDir
                    if (null == dir || !dir.exists()) {
                        dir = filesDir
                        if (null == dir || !dir.exists()) {
                            val cacheDirPath =
                                File.separator + "data" + File.separator + "data" + File.separator + packageName + File.separator + "cache" + File.separator
                            dir = File(cacheDirPath)
                            if (!dir.exists()) {
                                dir.mkdirs()
                            }
                        }
                    }
                }
            }
        }
        mTempImageFile = try {
            File.createTempFile("IMG", ".jpg", dir)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Code.REQUEST_SETTING_APP_DETAILS) {
            if (PermissionUtil.checkAndRequestPermissionsInActivity(this, *needPermissions)) {
                hasPermissions()
            } else {
                binding.rlPermissionsView.visibility = View.VISIBLE
            }
            return
        }
        when (resultCode) {
            RESULT_OK -> {
                if (Code.REQUEST_CAMERA == requestCode) {
                    if (isQ) {
                        onCameraResultForQ()
                        return
                    }
                    if (mTempImageFile == null || !mTempImageFile!!.isFile) {
                        throw RuntimeException("EasyPhotos拍照保存的图片不存在")
                    }
                    onCameraResult()
                    return
                }
                if (Code.REQUEST_PREVIEW_ACTIVITY == requestCode) {
                    if (data!!.getBooleanExtra(Key.PREVIEW_CLICK_DONE, false)) {
                        done()
                        return
                    }
                    photosAdapter.change()
                    processOriginalMenu()
                    shouldShowMenuDone()
                    return
                }
                if (Code.REQUEST_PUZZLE_SELECTOR == requestCode) {
                    if (data != null) {
                        val puzzlePhoto: Photo? = data.getParcelableExtra(EasyPhotos.RESULT_PHOTOS)
                        if (puzzlePhoto != null) {
                            addNewPhoto(puzzlePhoto)
                        }
                    }
                    return
                }
            }
            RESULT_CANCELED -> {
                if (Code.REQUEST_CAMERA == requestCode) {
                    // 删除临时文件
                    if (mTempImageFile != null && mTempImageFile!!.exists()) {
                        mTempImageFile!!.delete()
                        mTempImageFile = null
                    }
                    if (Setting.onlyStartCamera) {
                        finish()
                    }
                    return
                }
                if (Code.REQUEST_PREVIEW_ACTIVITY == requestCode) {
                    processOriginalMenu()
                    return
                }
            }
            else -> {
            }
        }
    }

    private var folderPath: String? = null
    private var albumName: String? = null
    private fun addNewPhoto(photo: Photo) {
        photo.selectedOriginal = Setting.selectedOriginal
        if (!isQ) {
            MediaScannerConnectionUtils.refresh(this, photo.path)
            folderPath = File(photo.path).parentFile?.absolutePath
            albumName = StringUtils.getLastPathSegment(folderPath)
        }
        val albumNames = albumModel!!.getAllAlbumName(this)
        albumModel?.album?.getAlbumItem(albumNames)?.addImageItem(0, photo)
        albumModel?.album?.addAlbumItem(albumName, folderPath, photo.path, photo.uri)
        albumModel?.album?.getAlbumItem(albumName)?.addImageItem(0, photo)
        albumItemList.clear()
        albumItemList.addAll(albumModel!!.albumItems)
        if (Setting.hasAlbumItemsAd()) {
            var albumItemsAdIndex = 2
            if (albumItemList.size < albumItemsAdIndex + 1) {
                albumItemsAdIndex = albumItemList.size - 1
            }
            albumItemList.add(albumItemsAdIndex, Setting.albumItemsAdView)
        }
        albumItemsAdapter.notifyDataSetChanged()
        if (Setting.count == 1) {
            Result.clear()
            val res = Result.addPhoto(photo)
            onSelectorOutOfMax(res)
        } else {
            if (Result.count() >= Setting.count) {
                onSelectorOutOfMax(null)
            } else {
                val res = Result.addPhoto(photo)
                onSelectorOutOfMax(res)
            }
        }
        binding.rvAlbumItems.scrollToPosition(0)
        albumItemsAdapter.setSelectedPosition(0)
        shouldShowMenuDone()
    }

    @SuppressLint("InlinedApi")
    private fun getPhoto(uri: Uri?): Photo? {
        var p: Photo? = null
        val path: String
        val name: String
        val dateTime: Long
        val type: String
        val size: Long
        var width = 0
        var height = 0
        var orientation = 0
        val projections = AlbumModel.getInstance().projections
        val shouldReadWidth = projections.size > 8
        val cursor = contentResolver.query((uri)!!, projections, null, null, null) ?: return null
        val albumNameCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        if (cursor.moveToFirst()) {
            path = cursor.getString(1)
            name = cursor.getString(2)
            dateTime = cursor.getLong(3)
            type = cursor.getString(4)
            size = cursor.getLong(5)
            if (shouldReadWidth) {
                width = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH))
                height = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT))
                orientation =
                    cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION))
                if (90 == orientation || 270 == orientation) {
                    val temp = width
                    width = height
                    height = temp
                }
            }
            if (albumNameCol > 0) {
                albumName = cursor.getString(albumNameCol)
                folderPath = albumName
            }
            p = Photo(name, uri, path, dateTime, width, height, orientation, size, 0, type)
        }
        cursor.close()
        return p
    }

    private fun onCameraResultForQ() {
        loadingDialog!!.show()
        thread(start = true) {
            val photo = getPhoto(photoUri)
            if (photo == null) {
                Log.e("easyPhotos", "onCameraResultForQ() -》photo = null")
                return@thread
            }
            runOnUiThread {
                loadingDialog!!.dismiss()
                if (Setting.onlyStartCamera || albumModel!!.albumItems.isEmpty()) {
                    val data = Intent()
                    photo.selectedOriginal = Setting.selectedOriginal
                    resultList.add(photo)
                    data.putParcelableArrayListExtra(EasyPhotos.RESULT_PHOTOS, resultList)
                    data.putExtra(
                        EasyPhotos.RESULT_SELECTED_ORIGINAL,
                        Setting.selectedOriginal
                    )
                    setResult(RESULT_OK, data)
                    finish()
                    return@runOnUiThread
                }
                addNewPhoto(photo)
            }
        }

    }

    private fun onCameraResult() {
//        val loading = LoadingDialog.get(this)
        thread(start = true) {
            val dateFormat = SimpleDateFormat(
                "yyyyMMdd_HH_mm_ss",
                Locale.getDefault()
            )
            val imageName = "IMG_%s.jpg"
            val filename = String.format(imageName, dateFormat.format(Date()))
            val reNameFile = File(mTempImageFile!!.parentFile, filename)
            if (!reNameFile.exists()) {
                if (mTempImageFile!!.renameTo(reNameFile)) {
                    mTempImageFile = reNameFile
                }
            }
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(mTempImageFile!!.absolutePath, options)
            MediaScannerConnectionUtils.refresh(this@EasyPhotosActivity, mTempImageFile) //
            // 更新媒体库
            val uri = UriUtils.getUri(this@EasyPhotosActivity, mTempImageFile)
            var width = 0
            var height = 0
            var orientation = 0
            if (Setting.useWidth) {
                width = options.outWidth
                height = options.outHeight
                var exif: ExifInterface? = null
                try {
                    exif = ExifInterface((mTempImageFile)!!)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                if (null != exif) {
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
                    if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                        width = options.outHeight
                        height = options.outWidth
                    }
                }
            }
            val photo = Photo(
                mTempImageFile!!.name, uri,
                mTempImageFile!!.absolutePath,
                mTempImageFile!!.lastModified() / 1000, width, height, orientation,
                mTempImageFile!!.length(),
                DurationUtils.getDuration(mTempImageFile!!.absolutePath),
                options.outMimeType
            )
            runOnUiThread {
                if (Setting.onlyStartCamera || albumModel!!.albumItems.isEmpty()) {
                    val data = Intent()
                    photo.selectedOriginal = Setting.selectedOriginal
                    resultList.add(photo)
                    data.putParcelableArrayListExtra(EasyPhotos.RESULT_PHOTOS, resultList)
                    data.putExtra(
                        EasyPhotos.RESULT_SELECTED_ORIGINAL,
                        Setting.selectedOriginal
                    )
                    setResult(RESULT_OK, data)
                    finish()
                    return@runOnUiThread
                }
                addNewPhoto(photo)
            }

        }
    }

    private fun onAlbumWorkedDo() {
        initView()
    }

    private fun initView() {
        if (albumModel!!.albumItems.isEmpty()) {
            if (Setting.isOnlyVideo()) {
                Toast.makeText(
                    applicationContext,
                    R.string.no_videos_easy_photos,
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return
            }
            Toast.makeText(applicationContext, R.string.no_photos_easy_photos, Toast.LENGTH_LONG)
                .show()
            if (Setting.isShowCamera) Code.REQUEST_CAMERA.launchCamera() else finish()
            return
        }
        EasyPhotos.setAdListener(this)
        if (Setting.hasPhotosAd()) {
            binding.mToolBarBottomLine.gone()
        }

        if (Setting.isShowCamera && Setting.isBottomRightCamera()) {
            binding.fabCamera.visible()
        }
        if (!Setting.showPuzzleMenu) {
            binding.tvPuzzle.gone()
        }
        val columns = resources.getInteger(R.integer.photos_columns_easy_photos)
        binding.tvAlbumItems.text = albumModel!!.albumItems[0].name
        (binding.rvPhotos.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        //去除item更新的闪光
        photoList.clear()
        photoList.addAll(albumModel!!.getCurrAlbumItemPhotos(0))
        var index = 0
        if (Setting.hasPhotosAd()) {
            photoList.add(index, Setting.photosAdView)
        }
        if (Setting.isShowCamera && !Setting.isBottomRightCamera()) {
            if (Setting.hasPhotosAd()) index = 1
            photoList.add(index, null)
        }
        with(binding.rvPhotos) {
            layoutManager = GridLayoutManager(this@EasyPhotosActivity, columns).also {
                if (Setting.hasPhotosAd()) {
                    it.spanSizeLookup = object : SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (position == 0) {
                                gridLayoutManager!!.spanCount //独占一行
                            } else {
                                1 //只占一行中的一列
                            }
                        }
                    }
                }
            }
            adapter = photosAdapter
        }

        if (Setting.showOriginalMenu) {
            processOriginalMenu()
        } else {
            binding.tvOriginal.gone()
        }
        initAlbumItems()
        shouldShowMenuDone()
        with(binding) {
            setClick(
                ivAlbumItems,
                tvClear,
                ivSecondMenu,
                tvPuzzle,
                tvAlbumItems,
                rootViewAlbumItems,
                tvDone,
                tvOriginal,
                tvPreview,
                fabCamera
            )
        }
    }

    private fun hideActionBar() {
        val actionBar = supportActionBar
        actionBar?.hide()
    }

    private fun initAlbumItems() {
        albumItemList.clear()
        albumItemList.addAll(albumModel!!.albumItems)
        if (Setting.hasAlbumItemsAd()) {
            var albumItemsAdIndex = 2
            if (albumItemList.size < albumItemsAdIndex + 1) {
                albumItemsAdIndex = albumItemList.size - 1
            }
            albumItemList.add(albumItemsAdIndex, Setting.albumItemsAdView)
        }
        with(binding.rvAlbumItems) {
            layoutManager = LinearLayoutManager(this@EasyPhotosActivity)
            adapter = albumItemsAdapter
        }


    }

    override fun onClick(v: View) {
        val id = v.id
        if (R.id.tv_album_items == id || R.id.iv_album_items == id) {
            showAlbumItems(View.GONE == binding.rootViewAlbumItems.visibility)
        } else if (R.id.root_view_album_items == id) {
            showAlbumItems(false)
        } else if (R.id.iv_back == id) {
            onBackPressed()
        } else if (R.id.tv_done == id) {
            done()
        } else if (R.id.tv_clear == id) {
            if (Result.isEmpty()) {
                processSecondMenu()
                return
            }
            Result.removeAll()
            photosAdapter.change()
            shouldShowMenuDone()
            processSecondMenu()
        } else if (R.id.tv_original == id) {
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
            processSecondMenu()
        } else if (R.id.tv_preview == id) {
            PreviewActivity.start(this@EasyPhotosActivity, -1, 0)
        } else if (R.id.fab_camera == id) {
            Code.REQUEST_CAMERA.launchCamera()
        } else if (R.id.iv_second_menu == id) {
            processSecondMenu()
        } else if (R.id.tv_puzzle == id) {
            processSecondMenu()
            PuzzleSelectorActivity.start(this)
        }
    }

    open fun processSecondMenu() {
        if (View.VISIBLE == binding.mSecondLevelMenu.visibility) {
            binding.mSecondLevelMenu.visibility = View.INVISIBLE
            if (Setting.isShowCamera && Setting.isBottomRightCamera()) {
                binding.fabCamera.visibility = View.VISIBLE
            }
        } else {
            binding.mSecondLevelMenu.visibility = View.VISIBLE
            if (Setting.isShowCamera && Setting.isBottomRightCamera()) {
                binding.fabCamera.visibility = View.INVISIBLE
            }
        }
    }

    private var clickDone = false
    private fun done() {
        if (clickDone) return
        clickDone = true
        //        if (Setting.useWidth) {
//            resultUseWidth();
//            return;
//        }
        resultFast()
    }

    private fun resultUseWidth() {
        loadingDialog!!.show()
        thread(start = true) {
            val size = Result.photos.size
            try {
                for (i in 0 until size) {
                    val photo = Result.photos[i]
                    if (photo.width == 0 || photo.height == 0) {
                        BitmapUtils.calculateLocalImageSizeThroughBitmapOptions(photo)
                    }
                    if (BitmapUtils.needChangeWidthAndHeight(photo)) {
                        val h = photo.width
                        photo.width = photo.height
                        photo.height = h
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            runOnUiThread {
                loadingDialog!!.dismiss()
                resultFast()
            }
        }
    }

    private fun resultFast() {
        val intent = Intent()
        Result.processOriginal()
        resultList.addAll(Result.photos)
        intent.putParcelableArrayListExtra(EasyPhotos.RESULT_PHOTOS, resultList)
        intent.putExtra(
            EasyPhotos.RESULT_SELECTED_ORIGINAL,
            Setting.selectedOriginal
        )
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun processOriginalMenu() {
        if (!Setting.showOriginalMenu) return
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

    private fun showAlbumItems(isShow: Boolean) {
        if (isShow) {
            binding.rootViewAlbumItems.visible()
            setShow.start()
        } else {
            setHide.start()
        }
    }


    override fun onAlbumItemClick(position: Int, realPosition: Int) {
        updatePhotos(realPosition)
        showAlbumItems(false)
        binding.tvAlbumItems.text = albumModel?.albumItems?.get(realPosition)?.name ?: ""
    }

    private fun updatePhotos(currAlbumItemIndex: Int) {
        this.currAlbumItemIndex = currAlbumItemIndex
        photoList.clear()
        photoList.addAll(albumModel!!.getCurrAlbumItemPhotos(currAlbumItemIndex))
        var index = 0
        if (Setting.hasPhotosAd()) {
            photoList.add(index, Setting.photosAdView)
        }
        if (Setting.isShowCamera && !Setting.isBottomRightCamera()) {
            if (Setting.hasPhotosAd()) index = 1
            photoList.add(index, null)
        }
        photosAdapter.change()
        binding.rvPhotos.scrollToPosition(0)
    }

    private fun shouldShowMenuDone() {
        if (Result.isEmpty()) {
            if (View.VISIBLE == binding.tvDone.visibility) {
                val scaleHide = ScaleAnimation(1f, 0f, 1f, 0f)
                scaleHide.duration = 200
                binding.tvDone.startAnimation(scaleHide)
            }
            binding.tvDone.invisible()
            binding.tvPreview.invisible()
        } else {
            if (View.INVISIBLE == binding.tvDone.visibility) {
                val scaleShow = ScaleAnimation(0f, 1f, 0f, 1f)
                scaleShow.duration = 200
                binding.tvDone.startAnimation(scaleShow)
            }
            binding.tvDone.visible()
            binding.tvPreview.visible()
        }
        binding.tvDone.text = getString(
            R.string.selector_action_done_easy_photos,
            Result.count(),
            Setting.count
        )
    }

    override fun onCameraClick() {
        Code.REQUEST_CAMERA.launchCamera()
    }

    override fun onPhotoClick(position: Int, realPosition: Int) {
        PreviewActivity.start(this@EasyPhotosActivity, currAlbumItemIndex, realPosition)
    }

    override fun onSelectorOutOfMax(result: Int?) {
        if (result == null) {
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
        when (result) {
            Result.PICTURE_OUT -> Toast.makeText(
                applicationContext, getString(
                    R.string.selector_reach_max_image_hint_easy_photos, Setting.complexPictureCount
                ), Toast.LENGTH_SHORT
            ).show()
            Result.VIDEO_OUT -> Toast.makeText(
                applicationContext, getString(
                    R.string.selector_reach_max_video_hint_easy_photos, Setting.complexVideoCount
                ), Toast.LENGTH_SHORT
            ).show()
            Result.SINGLE_TYPE -> Toast.makeText(
                applicationContext, getString(R.string.selector_single_type_hint_easy_photos),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onSelectorChanged() {
        shouldShowMenuDone()
    }

    override fun onBackPressed() {
        if (binding.rootViewAlbumItems.visibility == View.VISIBLE) {
            showAlbumItems(false)
            return
        }
        if (View.VISIBLE == binding.mSecondLevelMenu.visibility) {
            processSecondMenu()
            return
        }
        if (albumModel != null) albumModel!!.stopQuery()
        if (Setting.hasPhotosAd()) {
            photosAdapter.clearAd()
        }
        if (Setting.hasAlbumItemsAd()) {
            albumItemsAdapter.clearAd()
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        if (albumModel != null) albumModel!!.stopQuery()
        super.onDestroy()
    }

    override fun onPhotosAdLoaded() {
        runOnUiThread { photosAdapter.change() }
    }

    override fun onAlbumItemsAdLoaded() {
        runOnUiThread { albumItemsAdapter.notifyDataSetChanged() }
    }

    private fun setClick(vararg views: View) {
        for (v: View in views) {
            v.setOnClickListener(this)
        }
    }

    /**
     * 返回true 表示可以使用  返回false表示不可以使用
     */
    @Suppress("DEPRECATION")
    open fun cameraIsCanUse(): Boolean {
        var isCanUse = true
        val mCamera: Camera? = try {
            Camera.open().also {
                val mParameters = it.parameters //针对魅族手机
                it.parameters = mParameters
            }
        } catch (e: Exception) {
            isCanUse = false
            null
        }
        if (mCamera != null) {
            try {
                mCamera.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return isCanUse
    }

    companion object {
        var startTime: Long = 0
        private fun doubleClick(): Boolean {
            val now = System.currentTimeMillis()
            if (now - startTime < 600) {
                return true
            }
            startTime = now
            return false
        }

        @JvmStatic
        fun start(activity: Activity, requestCode: Int) {
            if (doubleClick()) return
            val intent = Intent(activity, EasyPhotosActivity::class.java)
            activity.startActivityForResult(intent, requestCode)
        }

        @JvmStatic
        @Suppress("DEPRECATION")
        fun start(fragment: Fragment, requestCode: Int) {
            if (doubleClick()) return
            val intent = Intent(fragment.activity, EasyPhotosActivity::class.java)
            fragment.startActivityForResult(intent, requestCode)
        }

        @JvmStatic
        fun start(fragment: androidx.fragment.app.Fragment, requestCode: Int) {
            if (doubleClick()) return
            val intent = Intent(fragment.context, EasyPhotosActivity::class.java)
            fragment.startActivityForResult(intent, requestCode)
        }
    }
}