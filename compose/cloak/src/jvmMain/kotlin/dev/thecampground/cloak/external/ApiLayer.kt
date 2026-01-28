package dev.thecampground.cloak.external

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

open class ApiLayer(
    open val linker: Linker,
    open val symbols: SymbolLookup,
) {
    internal fun downcallHandle(name: String, descriptor: FunctionDescriptor): MethodHandle {
        return linker.downcallHandle(
            symbols.find(name).orElseThrow(),
            descriptor
        )
    }
    internal fun hintFunction(name: String): MethodHandle {
        return this.downcallHandle(
            name,
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        )
    }

    internal fun callbackFunction(name: String): MethodHandle {
        return this.downcallHandle(
            name,
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        )
    }
}