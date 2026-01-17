//
// Created by brys0 on 1/17/26.
//

#ifndef CLOAK_CLOAK_MPV_COMPAT_H
#define CLOAK_CLOAK_MPV_COMPAT_H
#include <jni.h>

// 1. Remove "Java_" from here. Use underscores for dots.
#define PACKAGE_NAME dev_thecampground_cloak_mpv
#define CLASS_NAME MPVCompat

// 2. The Glue Macro (Double-wrapping ensures the macros expand before pasting)
#define JNI_CONCAT_INNER(pkg, cls, meth) Java_ ## pkg ## _ ## cls ## _ ## meth
#define JNI_CONCAT(pkg, cls, meth) JNI_CONCAT_INNER(pkg, cls, meth)

// 3. The Final Export Macro
#define JNI_METHOD(RET_TYPE, METHOD_NAME) \
extern "C" JNIEXPORT RET_TYPE JNICALL JNI_CONCAT(PACKAGE_NAME, CLASS_NAME, METHOD_NAME)

#endif //CLOAK_CLOAK_MPV_COMPAT_H