//
// Created by brys0 on 1/17/26.
//

#include "cloak_mpv_compat.h"

#include <iostream>
#include <glad/gl.h>
#include <GLFW/glfw3.h>

#include "jni.h"
// Static Cache
static jclass COMPAT_CLASS = nullptr;
static jclass CONTEXT_CLASS = nullptr;

static jfieldID COMPAT_CONTEXT_FIELD = nullptr; // MPVCompat.renderContext
static jfieldID CONTEXT_TEXID_FIELD = nullptr;  // MPVRenderContext.textureID
static jfieldID CONTEXT_FBO_FIELD = nullptr;    // MPVRenderContext.framebuffer
static jfieldID CONTEXT_TEX_FORMAT_FIELD = nullptr;    // MPVRenderContext.textureFormat

void logError(const std::string &message) {
    std::cerr << "[Cloak->MPVCompat] "<< message << std::endl;
}

bool checkGLInit() {
    if (!gladLoadGL(glfwGetProcAddress)) {
        logError("You must establish an OpenGL context first.");
        return false;
    }

    return true;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass localCompat = env->FindClass("dev/thecampground/cloak/mpv/MPVCompat");
    if (!localCompat) return JNI_ERR;
    COMPAT_CLASS = reinterpret_cast<jclass>(env->NewGlobalRef(localCompat));

    COMPAT_CONTEXT_FIELD = env->GetFieldID(COMPAT_CLASS, "renderContext", "Ldev/thecampground/cloak/mpv/MPVRenderContext;");

    jclass localContext = env->FindClass("dev/thecampground/cloak/mpv/MPVRenderContext");
    if (!localContext) return JNI_ERR;
    CONTEXT_CLASS = reinterpret_cast<jclass>(env->NewGlobalRef(localContext));

    CONTEXT_TEXID_FIELD = env->GetFieldID(CONTEXT_CLASS, "textureID", "I");
    CONTEXT_FBO_FIELD = env->GetFieldID(CONTEXT_CLASS, "framebuffer", "I");
    CONTEXT_TEX_FORMAT_FIELD = env->GetFieldID(CONTEXT_CLASS, "textureFormat", "I");

    if (!COMPAT_CONTEXT_FIELD || !CONTEXT_TEXID_FIELD || !CONTEXT_FBO_FIELD) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

JNI_METHOD(jboolean, createRenderContext)(JNIEnv* env, jobject thiz, jint format) {
    if (!checkGLInit()) {
        return false;
    }

    jclass cls = env->FindClass("dev/thecampground/cloak/mpv/MPVRenderContext");
    if (!cls) {
        logError("Could not find MPVRenderContext class");
        return false;
    }

    jmethodID constructor = env->GetMethodID(cls, "<init>", "(II)V");
    if (!constructor) {
        logError("Could not find constructor for MPVRenderContext class");
        return false;
    }

    GLuint fbo = 0;
    GLuint textureID = 0;

    glGenFramebuffers(1, &fbo);
    if (!fbo) {
        logError("Could not generate framebuffer, or framebuffer is invalid.");
        return false;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);

    glGenTextures(1, &textureID);
    glBindTexture(GL_TEXTURE_2D, textureID);
    glTexImage2D(GL_TEXTURE_2D, 0, format, 0, 0, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureID, 0);

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        logError("Video framebuffer #" + std::to_string(fbo) + " is not complete");
        return false;
    }

    const jint generatedFbo = static_cast<int>(fbo);
    const jint generatedTexture = static_cast<int>(textureID);
    const jobject renderContext = env->NewObject(cls, constructor, generatedFbo, generatedTexture, format);

    const jclass compatCls = env->GetObjectClass(thiz);
    const jfieldID fieldId = env->GetFieldID(compatCls, "renderContext", "Ldev/thecampground/cloak/mpv/MPVRenderContext;");
    if (fieldId == nullptr) {
        logError("Could not find renderContext field!");
        return false;
    }

    env->SetObjectField(thiz, fieldId, renderContext);

    return true;
}

JNI_METHOD(jboolean, resizeMPVTexture)(JNIEnv* env, jobject thiz, jint width, jint height) {
    if (!checkGLInit() || width <= 0 || height <= 0) {
        return false;
    }
    jobject renderContextObj = env->GetObjectField(thiz, COMPAT_CONTEXT_FIELD);

    if (renderContextObj == nullptr) {
        return false;
    }

    const jint textureID = env->GetIntField(renderContextObj, CONTEXT_TEXID_FIELD);

    glBindTexture(GL_TEXTURE_2D, static_cast<GLuint>(textureID));
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    return true;
}

JNI_METHOD(jboolean, cleanup)(JNIEnv* env, jobject thiz) {
    if (!checkGLInit()) {
        return false;
    }
    const jobject renderContextObj = env->GetObjectField(thiz, COMPAT_CONTEXT_FIELD);

    if (renderContextObj == nullptr) {
        return false;
    }

    const GLuint fbo = env->GetIntField(renderContextObj, CONTEXT_FBO_FIELD);
    const GLuint texID = env->GetIntField(renderContextObj, CONTEXT_TEXID_FIELD);

    glDeleteFramebuffers(1, &fbo);
    glDeleteTextures(1, &texID);

    env->SetObjectField(thiz, COMPAT_CONTEXT_FIELD, nullptr);

    return true;
}