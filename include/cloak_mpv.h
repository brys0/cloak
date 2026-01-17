//
// Created by brys0 on 1/15/26.
//

#ifndef CLOAK_CLOAK_MPV_H
#define CLOAK_CLOAK_MPV_H
#include <glad/gl.h>

#include "mpv/client.h"
#ifdef __cplusplus
extern "C" {
#endif
typedef struct CloakMPV CloakMPV;

// lifecycle
CloakMPV* cloak_mpv_create();
void cloak_mpv_destroy(CloakMPV* mpv);

// playback
void cloak_mpv_load(CloakMPV* mpv, const char* path);

// rendering
void cloak_mpv_render(CloakMPV* mpv, int fb_width, int fb_height);

    GLuint cloak_mpv_get_texture(CloakMPV* ctx);

    mpv_handle* cloak_mpv_get_handle(CloakMPV* ctx);
    void cloak_mpv_resize_texture(CloakMPV* ctx, int width, int height);
    uint64_t cloak_mpv_check_update(CloakMPV* mpv);
// optional
void cloak_mpv_poll(CloakMPV* mpv);
#ifdef __cplusplus
}
#endif
#endif //CLOAK_CLOAK_MPV_H