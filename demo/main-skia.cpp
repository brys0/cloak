#include <iostream>
// Skia headers
#include <glad/gl.h>
#include "CZ/skia/gpu/ganesh/gl/GrGLInterface.h"
#include "CZ/skia/gpu/ganesh/GrDirectContext.h"
#include "CZ/skia/gpu/ganesh/gl/GrGLTypes.h"
#include "CZ/skia/core/SkCanvas.h"
#include "CZ/skia/core/SkSurface.h"
#include "CZ/skia/core/SkImage.h"
#include "CZ/skia/gpu/ganesh/SkImageGanesh.h" // Newer Skia versions use this for MakeFromTexture
// This provides the MakeGL factory function (Ganesh)
#include "CZ/skia/gpu/ganesh/gl/GrGLDirectContext.h"
#include "CZ/skia/gpu/ganesh/gl/GrGLAssembleInterface.h"
#include "CZ/skia/gpu/ganesh/GrBackendSurface.h"
#include "CZ/skia/gpu/ganesh/gl/GrGLBackendSurface.h"
#include "CZ/skia/gpu/ganesh/gl/GrGLBackendSurface.h"
#include "CZ/skia/gpu/ganesh/SkSurfaceGanesh.h"
#include "CZ/skia/gpu/ganesh/gl/GrGLInterface.h"
#include "CZ/skia/gpu/ganesh/gl/GrGLDirectContext.h"
// For wrapping the interface
#include "CZ/skia/gpu/ganesh/gl/GrGLInterface.h"
#include "CZ/skia/core/SkColorSpace.h"
#include <ostream>
#define GLFW_INCLUDE_NONE
#include "cloak.h"
#include "cloak_mpv.h"
#include "../subprojects/mpv/include/mpv/client.h"
#include "CZ/skia/core/SkImageInfo.h"
#include "CZ/skia/core/SkImageInfo.h"

// 1. Create a callback that Skia will use to resolve GL function pointers
// This is the C++ equivalent of your 'procAddressPointer'
auto get_proc = [](void* ctx, const char name[]) -> GrGLFuncPtr {
    // If you are using GLAD:
    return cloak_get_proc_address(ctx, name);

    // If you have a custom proc loader from Cloak:
    // return (GrGLFuncPtr)cloak_get_proc_address(name);
};
static CloakMPV* mpv = nullptr;

int main() {
    // 1. Standard Cloak/GL Init
    cloak_init("Cloak Skia Demo", 600, 400);
    cloak_show_window();
    cloak_make_context_current();

    // 2. Initialize Skia Context
    auto interface = GrGLMakeAssembledInterface(cloak_get_current_context(), get_proc);
    auto skContext = GrDirectContexts::MakeGL(interface);

    mpv = cloak_mpv_create();
    // ... mpv setup ...
    // Create MPV instance
    mpv = cloak_mpv_create();
    cloak_mpv_load(mpv, "http://192.168.1.10:8096/Items/b823e35e2642157f8eff463c6b895484/Download?api_key=e314a158bae94ea68a235d203ef55bc8");
    std::cerr << "[mpv] Cloak Demo App started\n";

    int lastW = 0, lastH = 0;
    while (!cloak_should_close()) {
        cloak_mpv_poll(mpv);

        int w, h;
        cloak_get_framebuffer_size(&w, &h);
        if (w != lastW || h != lastH) {
            cloak_mpv_resize_texture(mpv, w, h);
            lastW = w; lastH = h;
        }

        // 3. Render MPV to texture as usual
        cloak_mpv_render(mpv, w, h);

        // --- SKIA RENDERING START ---

        // Wrap existing texture
        GrGLTextureInfo textureInfo;
        textureInfo.fTarget = GL_TEXTURE_2D;
        textureInfo.fID = cloak_mpv_get_texture(mpv);
        textureInfo.fFormat = GL_RGBA16F; // Match your MPV texture format

        auto backendTex = GrBackendTextures::MakeGL(w, h, skgpu::Mipmapped::kNo, textureInfo);

        sk_sp<SkImage> frameImage = SkImages::BorrowTextureFrom(
            skContext.get(),             // 1. The GrDirectContext (raw pointer)
            backendTex,                  // 2. The GrBackendTexture we just made
            kTopLeft_GrSurfaceOrigin,    // 3. Surface Origin
             kRGBA_F16_SkColorType,      // 4. Color Type
            kPremul_SkAlphaType,         // 5. Alpha Type
            nullptr,                     // 6. SkColorSpace (this is usually where it fails)
            nullptr,                     // 7. ReleaseProc (optional)
            nullptr                      // 8. ReleaseContext (optional)
        );

        // Create a surface to draw INTO the screen (FBO 0)
        auto backendRT = GrBackendRenderTargets::MakeGL(w, h, 0, 0, {0, GL_RGBA8}); // FBO 0
        sk_sp<SkSurface> surface = SkSurfaces::WrapBackendRenderTarget(
            skContext.get(),
            backendRT,
            kTopLeft_GrSurfaceOrigin,
            kRGBA_8888_SkColorType,
            nullptr, nullptr
        );

        if (surface && frameImage) {
            SkCanvas* canvas = surface->getCanvas();
            canvas->clear(SK_ColorBLACK);

            // Draw video frame
            canvas->drawImage(frameImage, 0, 0);

            // Draw some Skia shapes on top to prove it's working
            SkPaint paint;
            paint.setColor(SK_ColorRED);
            canvas->drawCircle(w/2, h/2, 50, paint);

            // 4. Flush and Reset
            skContext->flush();
            // This is the "Don't Crash" magic call. It tells Skia to
            // forget all cached GL state before you do your own GL calls.
            skContext->resetContext();
        }

        // --- SKIA RENDERING END ---

        cloak_swap_buffers();
        cloak_poll_window_events();
    }

    cloak_mpv_destroy(mpv);
    return 0;
}