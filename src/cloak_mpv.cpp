#include "cloak_mpv.h"

#define GLFW_INCLUDE_NONE
#include <cloak.h>
#include <mpv/client.h>
#include <mpv/render_gl.h>
#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <iostream>

#ifdef __cplusplus
extern "C" {
#endif
struct CloakMPV {
    mpv_handle *mpv = nullptr;
    GLuint fbId = 0;
    GLuint colorbuffer = 0;
    mpv_render_context *render = nullptr;
    int wakeup;
};

// Update callback
static void on_mpv_render_update(void *ctx) {
    CloakMPV *cloak = static_cast<CloakMPV *>(ctx);
    cloak->wakeup = 1;
}

void check_gl_error(const char *operation) {
    GLenum error = glGetError();
    if (error != GL_NO_ERROR) {
        std::cerr << "[GL ERROR] " << operation << ": 0x"
                << std::hex << error << std::dec << std::endl;
    }
}

/* hardcoded proc lookup */
static void *mpv_gl_get_proc(void *ctx, const char *name) {
    std::cerr << "[mpv] get_proc name " << name << "\n";
    return (void *) cloak_get_proc_address(ctx, name);
}

bool cloak_mpv_validate_texture(CloakMPV *ctx) {
    if (!ctx) return false;

    GLint currentTexture;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &currentTexture);

    glBindTexture(GL_TEXTURE_2D, ctx->colorbuffer);

    GLint width, height, internalFormat;
    glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH, &width);
    glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT, &height);
    glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT, &internalFormat);

    std::cout << "Texture " << ctx->colorbuffer << " validation:\n";
    std::cout << "  Width: " << width << "\n";
    std::cout << "  Height: " << height << "\n";
    std::cout << "  Internal Format: 0x" << std::hex << internalFormat << std::dec << "\n";

    GLint isTexture = glIsTexture(ctx->colorbuffer);
    std::cout << "  Is valid texture: " << (isTexture ? "YES" : "NO") << "\n";

    GLenum error = glGetError();
    if (error != GL_NO_ERROR) {
        std::cout << "  GL Error: 0x" << std::hex << error << std::dec << "\n";
    }

    glBindTexture(GL_TEXTURE_2D, currentTexture);

    return isTexture && width > 0 && height > 0;
}

CloakMPV *cloak_mpv_create() {
    setlocale(LC_NUMERIC, "C");
    auto *ctx = new CloakMPV;
    int width, height;
    cloak_get_framebuffer_size(&width, &height);

    ctx->colorbuffer = 0;
    glGenFramebuffers(1, &ctx->fbId);
    glBindFramebuffer(GL_FRAMEBUFFER, ctx->fbId);

    glGenTextures(1, &ctx->colorbuffer);
    glBindTexture(GL_TEXTURE_2D, ctx->colorbuffer);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ctx->colorbuffer, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        std::cout << "ERROR::FRAMEBUFFER:: VIDEO Framebuffer #" << ctx->fbId << "is not complete!" << std::endl;

    ctx->mpv = mpv_create();
    if (!ctx->mpv) {
        std::cerr << "[mpv] mpv_create failed\n";
        delete ctx;
        return nullptr;
    }
    mpv_set_option_string(ctx->mpv, "terminal", "yes");
    mpv_set_option_string(ctx->mpv, "msg-level", "all=v");
    mpv_set_option_string(ctx->mpv, "hwdec", "auto");
    mpv_set_option_string(ctx->mpv, "vf", "format=rgba64");

    mpv_set_option_string(ctx->mpv, "video-timing-offset", "0");
    mpv_set_option_string(ctx->mpv, "opengl-swapinterval", "0");

    mpv_initialize(ctx->mpv);
    mpv_request_log_messages(ctx->mpv, "debug");
    mpv_set_option_string(ctx->mpv, "vo", "libmpv");

    mpv_opengl_init_params gl_init = {
        .get_proc_address = mpv_gl_get_proc,
        .get_proc_address_ctx = cloak_get_current_context()
    };

    int advanced_ctl = 1;

    mpv_render_param params[]{
        {MPV_RENDER_PARAM_API_TYPE, const_cast<char *>(MPV_RENDER_API_TYPE_OPENGL)},
        {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &gl_init},
        {MPV_RENDER_PARAM_INVALID, nullptr},
        {MPV_RENDER_PARAM_ADVANCED_CONTROL, &advanced_ctl},
    };

    if (mpv_render_context_create(&ctx->render, ctx->mpv, params) < 0) {
        std::cerr << "[mpv] render context create failed\n";
        mpv_terminate_destroy(ctx->mpv);
        delete ctx;
        return nullptr;
    }
    std::cout << "[mpv] Render context\n";
    return ctx;
}

void cloak_mpv_destroy(CloakMPV *ctx) {
    if (!ctx) return;

    if (ctx->render)
        mpv_render_context_free(ctx->render);

    if (ctx->mpv)
        mpv_terminate_destroy(ctx->mpv);

    delete ctx;
}

void cloak_mpv_load(CloakMPV *ctx, const char *path) {
    std::cout << "[mpv] loading file " << path << "\n";
    if (!ctx || !path) return;

    const char *cmd[] = {
        "loadfile",
        path,
        nullptr
    };

    mpv_command(ctx->mpv, cmd);
}


uint64_t cloak_mpv_check_update(CloakMPV *ctx) {
    if (!ctx || !ctx->render) return 0;
    return mpv_render_context_update(ctx->render);
}

void cloak_mpv_render(CloakMPV *ctx, int width, int height) {
    if (!ctx || !ctx->render) return;

    int flip_y = 1;
    mpv_opengl_fbo fbo = {
        .fbo = static_cast<int>(ctx->fbId),
        .w = width,
        .h = height,
        .internal_format = GL_RGBA16F
    };

    mpv_render_param params[] = {
        {MPV_RENDER_PARAM_API_TYPE, (void *) MPV_RENDER_API_TYPE_OPENGL},
        {MPV_RENDER_PARAM_OPENGL_FBO, &fbo},
        {MPV_RENDER_PARAM_FLIP_Y, &flip_y},
        {MPV_RENDER_PARAM_INVALID, nullptr},
    };

    mpv_render_context_render(ctx->render, params);
    mpv_render_context_report_swap(ctx->render);
    glFlush();
}


GLuint cloak_mpv_get_texture(CloakMPV *ctx) {
    if (!ctx) return 0;
    return ctx->colorbuffer;
}

void cloak_mpv_poll(CloakMPV *ctx) {
    if (!ctx) return;

    while (true) {
        mpv_event *ev = mpv_wait_event(ctx->mpv, 0);
        if (ev->event_id == MPV_EVENT_NONE)
            break;
    }
}

mpv_handle *cloak_mpv_get_handle(CloakMPV *ctx) {
    if (!ctx) return nullptr;
    return ctx->mpv;
}

void cloak_mpv_resize_texture(CloakMPV *ctx, int width, int height) {
    if (!ctx || width <= 0 || height <= 0) return;

    glBindTexture(GL_TEXTURE_2D, ctx->colorbuffer);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
}
#ifdef __cplusplus
}
#endif
