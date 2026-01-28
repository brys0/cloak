package dev.thecampground.cloak.external.glfw

import dev.thecampground.cloak.external.ApiLayer
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

class GLFWApi(
    linker: Linker,
    symbols: SymbolLookup,
) : ApiLayer(linker, symbols) {
    val initHint = hintFunction("glfwInitHint")

    val init = downcallHandle(
        "glfwInit",
        FunctionDescriptor.of(ValueLayout.JAVA_INT)
    )

    val windowHint = hintFunction("glfwWindowHint")

    val createWindow = downcallHandle(
        "glfwCreateWindow",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        )
    )

    val makeContextCurrent = downcallHandle(
        "glfwMakeContextCurrent",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
        )
    )

    val swapInterval = downcallHandle(
        "glfwSwapInterval",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT
        )
    )

    val windowShouldClose = downcallHandle(
        "glfwWindowShouldClose",
        FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS)
    )

    val setWindowShouldClose = downcallHandle(
        "glfwSetWindowShouldClose",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN)
    )

    val pollEvents = downcallHandle(
        "glfwPollEvents",
        FunctionDescriptor.ofVoid()
    )

    val swapBuffers = downcallHandle(
        "glfwSwapBuffers",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    )

    val getProcAddressPtr =
        symbols.find("glfwGetProcAddress").orElseThrow()

    val getProcAddress = downcallHandle(
        "glfwGetProcAddress",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    val showWindow = downcallHandle(
        "glfwShowWindow",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    )

    val hideWindow = downcallHandle(
        "glfwHideWindow",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    )

    val getFramebufferSize = downcallHandle(
        "glfwGetFramebufferSize",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    val getCurrentContext = downcallHandle(
        "glfwGetCurrentContext",
        FunctionDescriptor.of(ValueLayout.ADDRESS)
    )

    val getCursorPosition = downcallHandle(
        "glfwGetCursorPos",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    )

    val getMouseButton = downcallHandle(
        "glfwGetMouseButton",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
    )

    val setWindowCloseCallback = callbackFunction("glfwSetWindowCloseCallback")
    val setCursorPositionCallback = callbackFunction("glfwSetCursorPosCallback")
    val setCursorEnterCallback = callbackFunction("glfwSetCursorEnterCallback")
    val setMouseButtonCallback = callbackFunction("glfwSetMouseButtonCallback")
    val setScrollCallback = callbackFunction("glfwSetScrollCallback")
    val setWindowSizeCallback = callbackFunction("glfwSetWindowSizeCallback")
    val setKeyCallback = callbackFunction("glfwSetKeyCallback")
    val setCharCallback = callbackFunction("glfwSetCharCallback")
}