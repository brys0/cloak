#include "cloak.h"

#define CP_PRESS 1
#define CP_RELEASE 2
#define CP_MOVE 3
#define CP_ENTER 4
#define CP_EXIT 5
#define CP_SCROLL 6


#include <iostream>
#include <GLFW/glfw3.h>
#include "EGL/egl.h"
#include <queue>

static std::queue<CloakEvent> event_queue;
static GLFWwindow *window;

void cloak_set_cursor_position_callback(GLFWwindow *_, double x, double y) {
    event_queue.push(
        {
            EVENT_MOVE,
            CP_MOVE,
            0,
            0,
            static_cast<float>(x),
            static_cast<float>(y),
            0,
            0
        }
    );
}

void cloak_set_cursor_enter_callback(GLFWwindow *_, int entered) {
    double x;
    double y;

    glfwGetCursorPos(window, &x, &y);

    int type;

    if (entered == GLFW_TRUE) {
        type = CP_ENTER;
    } else {
        type = CP_EXIT;
    }

    event_queue.push(
        {
            EVENT_MOVE,
            type,
            0,
            0,
            static_cast<float>(x),
            static_cast<float>(y),
            0,
            0
        }
    );
}

void cloak_set_mouse_button_callback(GLFWwindow *_, int button, int action, int mods) {
    event_queue.push(
        {
            .type = EVENT_CLICK,
            .subtype = action,
            .value = button,
            .mod = mods,
            .x = 0,
            .y = 0,
            .scrollX = 0,
            .scrollY = 0,
        }
    );
}

void cloak_set_scroll_callback(GLFWwindow *_, double scrollX, double scrollY) {
    event_queue.push({
        .type = EVENT_SCROLL,
        .subtype = 0,
        .value = 0,
        .mod = 0,
        .x = 0,
        .y = 0,
        .scrollX = static_cast<float>(scrollX),
        .scrollY = static_cast<float>(scrollY),
        }
    );
}

void cloak_set_window_resize_callback(GLFWwindow *_, int width, int height) {
    event_queue.push({
       .type = EVENT_WINDOW_RESIZE,
       .subtype = 0,
       .value = 0,
       .mod = 0,
       .x = static_cast<float>(width),
       .y = static_cast<float>(height),
       .scrollX = 0,
       .scrollY = 0,
       }
   );
}

void cloak_set_window_key_callback(GLFWwindow* window, int key, int scancode, int action, int mods) {
    event_queue.push({
        .type = EVENT_KEY,
        .subtype = action,
        .value = key,
        .mod = mods,
        .x = 0.0,
        .y = 0.0,
        .scrollX = 0,
        .scrollY = 0
    });
}

#ifdef __cplusplus
extern "C" {
#endif
bool cloak_init(const char* title, int width, int height, const char* className) {
    glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);

    if (!glfwInit()) {
        std::cerr << "Failed to initialize GLFW." << std::endl;
        const char* description;
        int code = glfwGetError(&description);

        if (code != GLFW_NO_ERROR) {
            std::cerr << "GLFW Error (0x" << std::hex << code << "): "
                      << (description ? description : "No description available")
                      << std::endl;
        }

        return false;
    }

    glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);
    glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
    glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
    glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
    glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHintString(GLFW_X11_CLASS_NAME, className);
    glfwWindowHintString(GLFW_WAYLAND_APP_ID, className);

    window = glfwCreateWindow(width, height, title, nullptr, nullptr);
    if (!window) {
        std::cerr << "Failed to create GLFW window." << std::endl;
        glfwTerminate();
        return false;
    }

    if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
        std::cout << "Wayland window created." << std::endl;
    }

    const int client_api = glfwGetWindowAttrib(window, GLFW_CONTEXT_CREATION_API);
    const int creation_api = glfwGetWindowAttrib(window, GLFW_CONTEXT_CREATION_API);
    std::cout << "Client API: " << (client_api == GLFW_OPENGL_API ? "OpenGL" : "OpenGL ES") << std::endl;
    std::cout << "Context API: " << (creation_api == GLFW_EGL_CONTEXT_API ? "EGL" : "GLX") << std::endl;

    cloak_make_context_current();
    // glfwSetCursorEnterCallback()
    glfwSetCursorPosCallback(window, cloak_set_cursor_position_callback);
    glfwSetCursorEnterCallback(window, cloak_set_cursor_enter_callback);
    glfwSetMouseButtonCallback(window, cloak_set_mouse_button_callback);
    glfwSetScrollCallback(window, cloak_set_scroll_callback);
    glfwSetWindowSizeCallback(window, cloak_set_window_resize_callback);
    glfwSetKeyCallback(window, cloak_set_window_key_callback);

    cloak_set_swap_interval(0);
    cloak_swap_buffers();


    return true;
}

void cloak_show_window() {
    if (!window) {
        std::cerr << "tried to call cloak_show_window() when window wasn't defined." << std::endl;
        return;
    }
    glfwShowWindow(window);
}
GLFuncPtr cloak_get_proc_address(void *ctx, const char *name) {
    if (!window) {
        std::cerr << "tried to call cloak_get_proc_address when window wasn't defined." << std::endl;
        return nullptr;
    }

    return glfwGetProcAddress(name);
}

void *cloak_get_current_context() {
    if (!window) {
        std::cerr << "tried to call cloak_get_current_context() when window wasn't defined." << std::endl;
        return nullptr;
    }

    return eglGetCurrentContext();
}


void cloak_make_context_current() {
    if (!window) {
        std::cerr << "tried to call cloak_make_context_current() when window wasn't defined." << std::endl;
        return;
    }
    glfwMakeContextCurrent(window);
}

extern "C" double cloak_get_time() {
    return glfwGetTime();
}

void cloak_set_swap_interval(const int interval) {
    glfwSwapInterval(interval);
}

bool cloak_should_close() {
    if (!window) {
        std::cerr << "tried to call cloak_should_close() when window wasn't defined." << std::endl;
        return false;
    }
    return glfwWindowShouldClose(window);
}


void cloak_get_framebuffer_size(int *width, int *height) {
    if (!window) {
        std::cerr << "tried to call cloak_get_framebuffer_size() when window wasn't defined." << std::endl;
        return;
    }

    glfwGetFramebufferSize(window, width, height);
}
void cloak_swap_buffers() {
    if (!window) {
        std::cerr << "tried to call cloak_swap_buffers() when window wasn't defined." << std::endl;
        return;
    }
    glfwSwapBuffers(window);
}

void cloak_poll_window_events() {
    glfwPollEvents();
}

bool cloak_poll_input_event(CloakEvent* out_event) {
    if (event_queue.empty()) return false;

    *out_event = event_queue.front();
    event_queue.pop();
    return true;
}

void hello() {
    std::cout << "Hello, World!" << std::endl;
}

#ifdef __cplusplus
}
#endif
