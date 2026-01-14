//
// Created by brys0 on 1/2/26.
//

#include <iostream>
#include <ostream>
#include <GL/gl.h>

#include "cloak.h"

int main() {
    if (!cloak_init("Cloak Demo App", 600, 400)) {
        return 2;
    }
    CloakEvent ev{};
    cloak_show_window();
    while (!cloak_should_close()) {
        // Render whatever
        // Prime the buffer with "nothing" (transparent)
        // so Weston has something to map
        glClearColor(0, 0.2, 0, .1);
        glClear(GL_COLOR_BUFFER_BIT);

        cloak_swap_buffers();
        cloak_poll_window_events();
    }


}
