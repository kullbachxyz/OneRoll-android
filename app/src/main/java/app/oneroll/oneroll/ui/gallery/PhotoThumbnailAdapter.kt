package app.oneroll.oneroll.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.oneroll.oneroll.databinding.ItemPhotoThumbnailBinding
import androidx.exifinterface.media.ExifInterface
import java.io.File

class PhotoThumbnailAdapter(
    private val onClick: (File, Int) -> Unit
) : ListAdapter<File, PhotoThumbnailAdapter.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPhotoThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemPhotoThumbnailBinding,
        private val onClick: (File, Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File, position: Int) {
            val bitmap = decodeOrientedBitmap(file, sampleSize = 8)
            binding.thumbnailImage.setImageBitmap(bitmap)
            binding.thumbnailImage.setOnClickListener { onClick(file, position) }
        }
    }

    private fun decodeOrientedBitmap(file: File, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.absolutePath == newItem.absolutePath

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.length() == newItem.length() && oldItem.lastModified() == newItem.lastModified()
        }
    }
}
