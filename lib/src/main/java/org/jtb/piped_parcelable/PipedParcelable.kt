package org.jtb.piped_parcelable

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * A [Parcelable] wrapper that uses pipes to marshal and unmarshal parcel data.
 */
class PipedParcelable<V : Parcelable> : Parcelable {
  internal companion object {
    private const val BUFFER_SIZE = 16 * 1024

    private val scope = CoroutineScope(Dispatchers.IO)

    @JvmField
    val CREATOR = object : Parcelable.Creator<PipedParcelable<out Parcelable>> {
      override fun createFromParcel(parcel: Parcel) =
          PipedParcelable<Parcelable>(parcel)

      override fun newArray(size: Int) =
          arrayOfNulls<PipedParcelable<out Parcelable>>(size)
    }
  }

  private var _value: V? = null
  private var _bytes: ByteArray? = null

  val value: V?
    get() {
      if (_value == null) {
        _value = unmarshall(_bytes)
      }
      return _value
    }

  constructor(value: V) {
    this._value = value
    this._bytes = null
  }

  internal constructor(bytes: ByteArray?) {
    this._value = null
    this._bytes = bytes
  }

  constructor(parcel: Parcel) {
    val out = parcel.readParcelable<ParcelFileDescriptor>(javaClass.classLoader)?.let { readFd ->
      ByteArrayOutputStream().apply {
        try {
          ParcelFileDescriptor.AutoCloseInputStream(readFd).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
              write(buffer, 0, bytesRead)
            }
          }
        } catch (e: IOException) {
          throw IllegalStateException(e)
        }
      }
    } ?: ByteArrayOutputStream()

    this._value = null
    this._bytes = out.toByteArray()
  }

  override fun writeToParcel(out: Parcel, flags: Int) {
    try {
      val fds = ParcelFileDescriptor.createPipe()
      out.writeParcelable(fds[0], 0)

      _bytes = (_bytes ?: marshall(_value)).also { b ->
        writeAsync(b, fds[0], fds[1])
      }
    } catch (e: IOException) {
      throw IllegalStateException(e)
    }
  }

  override fun describeContents(): Int = 0

  private fun marshall(value: Any?): ByteArray {
    if (value == null) return ByteArray(0)

    val parcel = Parcel.obtain()
    try {
      parcel.writeValue(value)
      return parcel.marshall()
    } catch (e: Exception) {
      throw IllegalStateException(e)
    } finally {
      parcel.recycle()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun unmarshall(bytes: ByteArray?): V? {
    if (bytes == null) return null

    val parcel = Parcel.obtain()
    try {
      parcel.unmarshall(bytes, 0, bytes.size)
      parcel.setDataPosition(0)
      return parcel.readValue(PipedParcelable::class.java.classLoader) as? V
    } finally {
      parcel.recycle()
    }
  }

  private fun writeAsync(
    data: ByteArray,
    readFd: ParcelFileDescriptor,
    writeFd: ParcelFileDescriptor,
  ): Job {
    return scope.launch {
      withContext(Dispatchers.IO) {
        try {
          ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { fos ->
            fos.write(data)
          }
        } finally {
          try {
            readFd.close()
          } catch (e: IOException) {
            // Ignore
          }
        }
      }
    }
  }
}