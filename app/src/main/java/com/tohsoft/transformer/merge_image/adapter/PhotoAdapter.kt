package com.tohsoft.transformer.merge_image.adapter

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.tohsoft.transformer.R
import java.util.WeakHashMap


class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.ViewHolder>() {
    private var mPhotoList: List<Uri> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mPhotoList[position], position)
    }

    private val attached = WeakHashMap<ViewHolder, Unit>()
    private var mRecyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        mRecyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        mRecyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        attached.remove(holder)
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        attached[holder] = Unit
        super.onViewAttachedToWindow(holder)
    }

    fun notifyCurrentHolderChange() {
        for (holder in attached.keys) {
            holder.changed()
        }
    }

    override fun getItemCount(): Int {
        return mPhotoList.size
    }

    fun setData(list: List<Uri>) {
        val oldSize = mPhotoList.size
        mPhotoList = list
        val added = mPhotoList.size - oldSize
        if (added > 0) notifyItemRangeInserted(oldSize, added)
        else if (added < 0) notifyItemRangeRemoved(mPhotoList.size, -added)
        mRecyclerView?.post {
            notifyCurrentHolderChange()
        }
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {

        private val ivPreview: ImageView = view.findViewById(R.id.iv_photo_avatar)
        private val tvPosition: TextView = view.findViewById(R.id.tv_position)
        private var currentPos = 0

        fun changed() {
            currentPos = absoluteAdapterPosition
            if (currentPos > RecyclerView.NO_POSITION && currentPos < mPhotoList.size) {
                val photo = mPhotoList[currentPos]
                bind(photo, currentPos)
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(uri: Uri, currentPos: Int) {
            Glide.with(view.context.applicationContext).load(uri).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(false)
                .dontAnimate()
                .override(400, 400)
                .into(ivPreview)

            tvPosition.text = (currentPos + 1).toString()
        }
    }
}