package com.xcoder.visual.model

import android.os.Parcel
import android.os.Parcelable

/**
 * Image properties model for ImageView and other image-bearing widgets.
 * Based on Sketchware-IA's image attribute handling in ViewBean.
 */
data class ImageBean(
    /** Drawable resource name (e.g. "@mipmap/ic_launcher", "@drawable/bg_card").
     *  Can also be a URL or file path for runtime loading. */
    var src: String = "",
    /** Scale type: "center", "centerCrop", "centerInside", "fitCenter",
     *  "fitStart", "fitEnd", "fitXY", "matrix" */
    var scaleType: String = "fitCenter",
    /** Tint color as 0xAARRGGBB. 0 means no tint. */
    var tint: Int = 0,
    /** Content description for accessibility. */
    var contentDescription: String = "",
    /** Whether the view should adjust its bounds to preserve the aspect ratio of its drawable. */
    var adjustViewBounds: Boolean = false,
    /** Whether to crop to padding when the image has padding. */
    var cropToPadding: Boolean = false
) : Parcelable {

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ImageBean> = object : Parcelable.Creator<ImageBean> {
            override fun createFromParcel(source: Parcel): ImageBean = ImageBean(source)
            override fun newArray(size: Int): Array<ImageBean?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(
        src = source.readString() ?: "",
        scaleType = source.readString() ?: "fitCenter",
        tint = source.readInt(),
        contentDescription = source.readString() ?: "",
        adjustViewBounds = source.readInt() != 0,
        cropToPadding = source.readInt() != 0
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeString(src)
            writeString(scaleType)
            writeInt(tint)
            writeString(contentDescription)
            writeInt(if (adjustViewBounds) 1 else 0)
            writeInt(if (cropToPadding) 1 else 0)
        }
    }

    override fun describeContents(): Int = 0

    /** Returns true if this ImageBean has any non-default image properties. */
    fun isNotEmpty(): Boolean =
        src.isNotEmpty() || scaleType != "fitCenter" || tint != 0 ||
                contentDescription.isNotEmpty() || adjustViewBounds || cropToPadding

    /** Deep clone. */
    fun clone(): ImageBean = copy()
}
