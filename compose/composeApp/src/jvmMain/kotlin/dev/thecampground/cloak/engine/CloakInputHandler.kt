package dev.thecampground.cloak.engine

import androidx.compose.foundation.InternalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.IntSize
import dev.thecampground.cloak.external.*
import java.awt.Component
import java.awt.event.KeyEvent.*


internal class CloakInputHandler @OptIn(InternalComposeUiApi::class) constructor(
    private val cloak: CloakLibrary.Companion,
    private val scene: ComposeScene,
    private val composeSceneContext: ComposeSceneContext,
    private val onResize: (IntSize) -> Unit,
    private val onDirty: () -> Unit,
) {
    private class DummyComponent(): Component()
    private val awtDummyComponent = DummyComponent()

    private var lastMousePosition: Offset = Offset.Zero

    // You need some mouse id, this is just an example one with the id of 0.
    // Far as I know it doesn't matter on desktop.
    private val mouseID = PointerId(0L)
    private val cloakKeyToComposeKey = mapOf(
        259 to Key.Backspace,
        257 to Key.Enter,
        258 to Key.Tab,
        262 to Key.DirectionRight,
        263 to Key.DirectionLeft,
        264 to Key.DirectionDown,
        265 to Key.DirectionUp,

        // modifiers: shift, ctrl, alt, meta
        340 to Key.ShiftLeft,
        344 to Key.ShiftRight,
        341 to Key.CtrlLeft,
        345 to Key.CtrlRight,
        342 to Key.AltLeft,
        346 to Key.AltRight,
        343 to Key.MetaLeft,
        347 to Key.MetaRight,
    )
    private val composeKeyToAwt = mapOf(
        // Navigation / editing
        Key.Backspace      to VK_BACK_SPACE,
        Key.Enter          to VK_ENTER,
        Key.Tab            to VK_TAB,
        Key.DirectionLeft  to VK_LEFT,
        Key.DirectionRight to VK_RIGHT,
        Key.DirectionUp    to VK_UP,
        Key.DirectionDown  to VK_DOWN,
        Key.Home           to VK_HOME,
        Key.PageUp         to VK_PAGE_UP,
        Key.PageDown       to VK_PAGE_DOWN,
        Key.Insert         to VK_INSERT,
        Key.Delete         to VK_DELETE,

        // Modifiers
        Key.ShiftLeft      to VK_SHIFT,
        Key.ShiftRight     to VK_SHIFT,
        Key.CtrlLeft       to VK_CONTROL,
        Key.CtrlRight      to VK_CONTROL,
        Key.AltLeft        to VK_ALT,
        Key.AltRight       to VK_ALT,
        Key.MetaLeft       to VK_META,
        Key.MetaRight      to VK_META,

        // Function keys
        Key.F1             to VK_F1,
        Key.F2             to VK_F2,
        Key.F3             to VK_F3,
        Key.F4             to VK_F4,
        Key.F5             to VK_F5,
        Key.F6             to VK_F6,
        Key.F7             to VK_F7,
        Key.F8             to VK_F8,
        Key.F9             to VK_F9,
        Key.F10            to VK_F10,
        Key.F11            to VK_F11,
        Key.F12            to VK_F12,
    )
    /**
     * Polls all input events
     */
    fun pollInputEvents() {
        while (true) {
            val event = this.cloak.pollInputEvent() ?: break

            when (event.type) {
                CloakInputEventTypes.WINDOW_RESIZE -> onWindowResize(event)
                CloakInputEventTypes.MOVE -> onMouseMove(event)
                CloakInputEventTypes.SCROLL -> onMouseScroll(event)
                CloakInputEventTypes.CLICK -> onMouseClick(event)
                CloakInputEventTypes.KEY -> onKey(event)
                CloakInputEventTypes.CHAR -> onCharCallback(event)
                else -> {
                    error("Unexpected event: $event")
                }
            }
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private fun onWindowResize(event: CloakEvent) {
        val newSize = IntSize(
            width = event.x.toInt(),
            height = event.y.toInt()
        )
        this.onDirty()
        this.scene.size = newSize
        this.onResize(newSize)
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    private fun onMouseMove(event: CloakEvent) {
        val position = Offset(event.x, event.y)
        val type = when (event.subtype) {
            CloakMoveTypes.MOVE -> PointerEventType.Move
            CloakMoveTypes.ENTER -> PointerEventType.Enter
            CloakMoveTypes.EXIT -> PointerEventType.Exit
            else -> PointerEventType.Unknown
        }

        this.scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = this.mouseID,
                    position = position,
                    pressed = false
                )
            ),
        )

        this.lastMousePosition = position
    }


    @OptIn(InternalComposeUiApi::class)
    private fun onMouseScroll(event: CloakEvent) {
        this.scene.sendRotaryScrollEvent(
            verticalScrollPixels = event.scrollY,
            horizontalScrollPixels = event.scrollX
        )
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    private fun onMouseClick(event: CloakEvent) {
        val lastPosition = this.lastMousePosition

        val type = when (event.subtype) {
            CloakKeyEvents.RELEASED -> PointerEventType.Release
            CloakKeyEvents.PRESSED -> PointerEventType.Press
            else -> PointerEventType.Unknown
        }

        val pointerButtonsActive = when (event.value) {
            CloakMouseButtons.LEFT -> PointerButtons(isPrimaryPressed = true)
            CloakMouseButtons.RIGHT -> PointerButtons(isSecondaryPressed = true)
            CloakMouseButtons.MIDDLE -> PointerButtons(isTertiaryPressed = true)
            CloakMouseButtons.BACK -> PointerButtons(isBackPressed = true)
            CloakMouseButtons.FORWARD -> PointerButtons(isForwardPressed = true)
            else -> PointerButtons()
        }

        this.scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = this.mouseID,
                    position = lastPosition,
                    pressed = type == PointerEventType.Press
                )
            ),
            buttons = pointerButtonsActive,
        )
    }

    @OptIn(InternalComposeUiApi::class, InternalFoundationApi::class)
    private fun onKey(event: CloakEvent) {
        val type = when (event.subtype) {
            CloakKeyEvents.RELEASED -> KeyEventType.KeyUp
            else -> KeyEventType.KeyDown
        }

        if (type == KeyEventType.KeyDown &&
            event.value == VK_V && // V
            (event.mod and CloakKeyModifiers.CTRL) != 0) {

            println("Should clipboard paste!")
            handlePaste()
        }

        val keyEvent = mapKeyEvent(event, type)

        this.scene.sendKeyEvent(keyEvent)
    }

    // TODO: Doesn't work yet.
    private fun handlePaste() {
        val text = "Example Text"
        println("[Cloak-Clipboard] Pasting ${text.length} characters")

        text.forEach { char ->
            val charEvent = CloakEvent().apply {
                this.type = CloakInputEventTypes.CHAR
                this.subtype = CloakKeyEvents.PRESSED
                this.value = char.code
                this.mod = 0
            }
            onCharCallback(charEvent)
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private fun onCharCallback(event: CloakEvent) {
        val codepoint = event.value.toUInt()
        val awtEvent = java.awt.event.KeyEvent(
            awtDummyComponent,
            KEY_TYPED,
            System.currentTimeMillis(),
            event.mod,
            VK_UNDEFINED,
            codepoint.toInt().toChar(),
            KEY_LOCATION_UNKNOWN
        )
        val composeKeyEvent = KeyEvent(
            key = Key(nativeKeyCode = event.value),
            type = KeyEventType.KeyDown,
            codePoint = codepoint.toInt(),
            isCtrlPressed = false,
            isMetaPressed = false,
            isAltPressed = false,
            isShiftPressed = false,
            nativeEvent = awtEvent,
        )

        this.scene.sendKeyEvent(composeKeyEvent)
    }

    @OptIn(InternalComposeUiApi::class)
    private fun mapKeyEvent(event: CloakEvent, type: KeyEventType): KeyEvent {
        val key = cloakKeyToComposeKey[event.value] ?: Key(nativeKeyCode = event.value)
        val awtKeyCode = composeKeyToAwt[key] ?: event.value
        val isShift = (event.mod and CloakKeyModifiers.SHIFT) != 0
        val isCtrl  = (event.mod and CloakKeyModifiers.CTRL) != 0
        val isAlt   = (event.mod and CloakKeyModifiers.ALT) != 0
        val isMeta  = (event.mod and CloakKeyModifiers.META) != 0

        val awtEvent = java.awt.event.KeyEvent(
            awtDummyComponent,
            if (type == KeyEventType.KeyDown) KEY_PRESSED else KEY_RELEASED,
            System.currentTimeMillis(),
            event.mod,
            awtKeyCode,
            CHAR_UNDEFINED,
            KEY_LOCATION_STANDARD
        )

        return KeyEvent(
            key = key,
            type = type,
            codePoint = key.nativeKeyCode,
            isShiftPressed = isShift,
            isCtrlPressed = isCtrl,
            isAltPressed = isAlt,
            isMetaPressed = isMeta,
            nativeEvent = awtEvent
        )
    }

}