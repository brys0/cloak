package dev.thecampground.cloak.external

import androidx.compose.ui.unit.IntSize
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import org.jetbrains.skia.impl.NativePointer

private val cloakPath =
    "libcloak.so"
@Structure.FieldOrder("type", "subtype", "value", "mod", "x", "y", "scrollX", "scrollY")
open class CloakEvent : Structure() {
    @JvmField var type: Int = 0
    @JvmField var subtype: Int = 0
    @JvmField var value: Int = 0
    @JvmField var mod: Int = 0
    @JvmField var x: Float = 0f
    @JvmField var y: Float = 0f
    @JvmField var scrollX: Float = 0f
    @JvmField var scrollY: Float = 0f

    class ByReference : CloakEvent(), Structure.ByReference
}
object CloakKeyModifiers {
    const val SHIFT = 1
    const val CTRL = 2
    const val ALT = 4
    const val META = 8
}

object CloakInputEventTypes {
    const val MOVE = 0
    const val SCROLL = 1
    const val CLICK = 2
    const val KEY = 3
    const val WINDOW_RESIZE = 4
    const val CHAR = 5
}

object CloakMoveTypes {
    const val MOVE = 3
    const val ENTER = 4
    const val EXIT = 5
}

object CloakKeyEvents {
    const val RELEASED = 0
    const val PRESSED = 1
}

object CloakMouseButtons {
    const val LEFT = 0
    const val RIGHT = 1
    const val MIDDLE = 2
    const val BACK = 3
    const val FORWARD =4
}

private fun String.toNativePointer(): Memory {
    val ptr = Memory((this.length + 1).toLong());
    ptr.write(0, this.encodeToByteArray(), 0, this.length)
    ptr.setByte(this.length.toLong(), 0.toByte())

    return ptr
}

@Structure.FieldOrder("bytes", "size")
open class GenericClipboardItem : Structure() {
    @JvmField var bytes: Pointer? = null
    @JvmField var size: NativeLong = NativeLong(0)

    class ByReference : GenericClipboardItem(), Structure.ByReference

    companion object {
        fun fromByteArray(data: ByteArray): GenericClipboardItem {
            val item = GenericClipboardItem()
            // Allocate native memory and copy bytes
            val memory = Memory(data.size.toLong())
            memory.write(0, data, 0, data.size)
            item.bytes = memory
            item.size = NativeLong(data.size.toLong())
            item.write()
            return item
        }
    }
}

interface CloakLibrary : Library {
    fun cloak_init(title: String, width: Int, height: Int, className: String): Boolean
    fun cloak_show_window()

    fun cloak_get_current_context(): NativePointer?
    fun cloak_make_context_current()
    fun cloak_get_time(): Double
    fun cloak_set_swap_interval(interval: Int)
    fun cloak_should_close(): Boolean
    fun cloak_get_framebuffer_size(width: IntByReference, height: IntByReference)
    fun cloak_swap_buffers()
    fun cloak_poll_window_events()
    fun cloak_poll_input_event(out_event: CloakEvent.ByReference): Boolean
    fun cloak_set_clipboard(data: Pointer, mime_types: Array<String>, mime_count: Int)
    fun cloak_set_clipboard_text(text: String)

    companion object {
        private val INSTANCE by lazy { Native.load(cloakPath, CloakLibrary::class.java) }
        private val rawLib: NativeLibrary =
            NativeLibrary.getInstance(cloakPath)
        private val pointerBuffer = CloakEvent.ByReference()

        fun getProcAddress(): Long {

            return Pointer.nativeValue(rawLib.getFunction("cloak_get_proc_address"))
        }
        fun init(title: String, width: Int, height: Int, className: String) = INSTANCE.cloak_init(title, width, height, className)
        fun showWindow() = INSTANCE.cloak_show_window()
        fun getCurrentContext() = INSTANCE.cloak_get_current_context()
        fun makeContextCurrent() = INSTANCE.cloak_make_context_current()
        fun getTime() = INSTANCE.cloak_get_time()
        fun setSwapInterval(interval: Int) = INSTANCE.cloak_set_swap_interval(interval)
        fun shouldClose() = INSTANCE.cloak_should_close()
        fun getFramebufferSize(): IntSize {
            val w = IntByReference()
            val h = IntByReference()

            INSTANCE.cloak_get_framebuffer_size(w, h)

            return IntSize(
                width = w.value,
                height = h.value
            )
        }
        fun swapBuffers() = INSTANCE.cloak_swap_buffers()
        fun pollWindowEvents() = INSTANCE.cloak_poll_window_events()
        fun pollInputEvent(): CloakEvent? {
            // no event available
            if (!INSTANCE.cloak_poll_input_event(pointerBuffer)) return null;

            return pointerBuffer;
        }

        // Text convenience method
        fun setClipboardText(text: String) {
            val item = GenericClipboardItem.fromByteArray(text.encodeToByteArray())

            INSTANCE.cloak_set_clipboard(
                item.pointer,
                arrayOf("text/plain;charset=utf-8", "text/plain", "UTF8_STRING"),
                3
            )
        }

        // Generic method for advanced use
        fun setClipboard(data: ByteArray, mimeTypes: Array<String>) {
            val item = GenericClipboardItem.fromByteArray(data)

            INSTANCE.cloak_set_clipboard(
                item.pointer,
                mimeTypes,
                mimeTypes.size
            )
        }
    }
}