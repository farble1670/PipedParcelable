// File: lib/src/androidTest/java/org/jtb/piped_parcelable/PipedParcelableInstrumentedTest.kt

package org.jtb.piped_parcelable

import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

internal class TestData(val content: String) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString() ?: "")

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(content)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TestData> {
        override fun createFromParcel(parcel: Parcel): TestData {
            return TestData(parcel)
        }

        override fun newArray(size: Int): Array<TestData?> {
            return arrayOfNulls(size)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PipedParcelableInstrumentedTest {

    private fun testPipedParcelable_marshal_and_unmarshal(size: Int) {
        // Create test data
        val testString = RandomString.generate(size)
        val originalData = TestData(testString)
        val original = PipedParcelable(originalData)

        // Write to parcel
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        // Read back
        val recreated = PipedParcelable<TestData>(parcel)
        parcel.recycle()

        // Verify
        val recreatedData = recreated.value
        assertNotNull(recreatedData)
        assertEquals(testString, recreatedData.content)
    }
    @Test
    fun testPipedParcelable_empty_marshal_and_unmarshal() {
        testPipedParcelable_marshal_and_unmarshal(0)
    }

    @Test
    fun testPipedParcelable_small_marshal_and_unmarshal() {
        testPipedParcelable_marshal_and_unmarshal(100)
    }

    @Test
    fun testPipedParcelable_large_marshal_and_unmarshal() {
        testPipedParcelable_marshal_and_unmarshal(10_000_000)
    }

    @Test
    fun testPipedParcelable_null_marshal_and_unmarshal() {
        val original = PipedParcelable<TestData>(null)
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreated = PipedParcelable<TestData>(parcel)
        parcel.recycle()

        assertEquals(null, recreated.value)
    }

    @Test(expected = IllegalStateException::class)
    fun testPipedParcelable_closed_parcel() {
        val original = PipedParcelable(TestData("test"))
        val parcel = Parcel.obtain()
        parcel.recycle() // Close the parcel first
        original.writeToParcel(parcel, 0) // Should throw
    }

    @Test
    fun testPipedParcelable_type_mismatch() {
        val parcel = Parcel.obtain()

        // Write an int
        parcel.writeInt(42)
        parcel.setDataPosition(0)

        // Let's dump the parcel state before trying to read
        println("Parcel data position: ${parcel.dataPosition()}")
        println("Parcel data size: ${parcel.dataSize()}")

        // Try to read as PipedParcelable and see what we actually get
        val recreated = PipedParcelable<TestData>(parcel)
        println("Recreated value: ${recreated.value}")
        println("Final parcel position: ${parcel.dataPosition()}")

        // The value should actually be null since we didn't write a valid ParcelFileDescriptor
        assertNull(recreated.value, "Value should be null when reading invalid parcel data")

        parcel.recycle()
    }

    @Test
    fun testPipedParcelable_corrupted_parcelfiledescriptor() {
        val parcel = Parcel.obtain()

        // Write something that looks like a ParcelFileDescriptor but isn't
        parcel.writeInt(1) // Random file descriptor number
        parcel.writeString("fake_path")
        parcel.setDataPosition(0)

        try {
            val recreated = PipedParcelable<TestData>(parcel)
            fail("Should have thrown exception when reading corrupted ParcelFileDescriptor")
        } catch (e: Exception) {
            // Now we should get an exception because we're trying to use an invalid file descriptor
        } finally {
            parcel.recycle()
        }
    }
}