package org.jtb.piped_parcelable.app_lib

import android.os.Parcel
import android.os.Parcelable

data class TestParcelable(val content: String) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString() ?: "")

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(content)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TestParcelable> {
        override fun createFromParcel(parcel: Parcel) = TestParcelable(parcel)
        override fun newArray(size: Int) = arrayOfNulls<TestParcelable>(size)
    }
}