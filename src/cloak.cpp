/**
 * I pray for you to understand any of this code D:
 */
#include "cloak.h"

#define CP_PRESS 1
#define CP_RELEASE 2
#define CP_MOVE 3
#define CP_ENTER 4
#define CP_EXIT 5
#define CP_SCROLL 6

#define GLFW_INCLUDE_NONE
#include <cstring>
#include <iostream>
#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include "EGL/egl.h"
#define GLFW_EXPOSE_NATIVE_WAYLAND
#include <GLFW/glfw3native.h>
#include <queue>
#include <unistd.h>
#include <wayland-client-protocol.h>
#include <wayland-client.h>


static std::queue<CloakEvent> event_queue;
static GLFWwindow *window;

void cloak_set_cursor_position_callback(GLFWwindow *_, double x, double y) {
    int mouseEvent = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT);

    event_queue.push(
        {
            EVENT_MOVE,
            CP_MOVE,
            mouseEvent,
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

void cloak_set_window_char_callback(GLFWwindow* window, unsigned int codepoint) {
    event_queue.push({
        .type = EVENT_CHAR,
        .subtype = GLFW_KEY_DOWN,
        .value = static_cast<int>(codepoint),
        .mod = 0,
        .x = 0.0,
        .y = 0.0,
        .scrollX = 0,
        .scrollY = 0
    });
}

void data_source_send(void *data, wl_data_source *,
                      const char *mime_type, int32_t fd) {
    if (fd < 0 || !data) {
        close(fd);
        return;
    }
    const auto* item = static_cast<GenericClipboardItem *>(data);

    std::cout << "[Cloak-Clipboard] Sending " << item->size << " bytes for " << mime_type << std::endl;

    if (const ssize_t n = write(fd, item->bytes, item->size); n < 0) {
        perror("[Cloak-Clipboard] Write failed");
    }

    close(fd);
}


static void data_source_target(void *data,
                                wl_data_source *,
                               const char *mime_type) {
    std::cerr << "Data source target MIME: "
              << (mime_type ? mime_type : "(null)") << "\n";
}



wl_data_device_manager* data_device_manager = nullptr;
wl_data_device* data_device = nullptr;
uint32_t last_serial = 0;
wl_seat* seat = nullptr;
static wl_data_source* current_clipboard_source = nullptr;

static void data_source_cancelled(void *data, wl_data_source *source) {
    if (data) {
        auto* item = static_cast<GenericClipboardItem *>(data);
        std::cout << "[Cloak-Clipboard] FREEING: " << item->size
               << " bytes at " << item->bytes
               << ", struct at " << item << std::endl;
        free(item->bytes);
        free(item);
    }

    if (source == current_clipboard_source) {
        current_clipboard_source = nullptr;
    }

    std::cout << "[Cloak-Clipboard] Selection cancelled" << std::endl;
    wl_data_source_destroy(source);
}
static const wl_data_source_listener source_listener = {
    .target = data_source_target,
    .send = data_source_send,
    .cancelled = data_source_cancelled,
    .dnd_drop_performed = nullptr,
    .dnd_finished = nullptr
};


static void registry_handle_global(void* data, struct wl_registry* registry, uint32_t name,
                                   const char* interface, uint32_t version) {
    if (strcmp(interface, "wl_data_device_manager") == 0) {
        data_device_manager = static_cast<wl_data_device_manager *>(wl_registry_bind(
            registry, name, &wl_data_device_manager_interface, 3
        ));
    } else if (strcmp(interface, "wl_seat") == 0) {
        seat = static_cast<wl_seat *>(wl_registry_bind(registry, name, &wl_seat_interface, 1));
    }
}

static const wl_registry_listener registry_listener = {
    .global = registry_handle_global,
    .global_remove = [](void*, struct wl_registry*, uint32_t) {}
};

static void register_pointer_listener() {
    wl_pointer* pointer = wl_seat_get_pointer(seat);
    static const struct wl_pointer_listener pointer_listener = {
        .enter = [](void *data, struct wl_pointer *wl_pointer, uint32_t serial,
                    struct wl_surface *surface, wl_fixed_t surface_x, wl_fixed_t surface_y) {
            last_serial = serial; // Capture the serial for clipboard use
        },

        .leave = [](void *data, struct wl_pointer *wl_pointer, uint32_t serial,
                    struct wl_surface *surface) {
            last_serial = serial;
        },

        .motion = [](void *data, struct wl_pointer *wl_pointer, uint32_t time,
                     wl_fixed_t surface_x, wl_fixed_t surface_y) {
        },

        .button = [](void *data, struct wl_pointer *wl_pointer, uint32_t serial,
                     uint32_t time, uint32_t button, uint32_t state) {
            last_serial = serial;
        },

        .axis = [](void *data, struct wl_pointer *wl_pointer, uint32_t time,
                   uint32_t axis, wl_fixed_t value) {
            // No serial
        },
    };

    wl_pointer_add_listener(pointer, &pointer_listener, nullptr);
}
void init_wayland_clipboard() {
    wl_display* display = glfwGetWaylandDisplay();
    if (!display) {
        std::cerr << "Failed to get wayland display." << std::endl;
        return;
    }
    wl_registry* registry = wl_display_get_registry(display);
    if (!registry) {
        std::cerr << "Failed to get wayland registry." << std::endl;
        return;
    }

    wl_registry_add_listener(registry, &registry_listener, nullptr);
    wl_display_roundtrip(display);

    if (!(data_device_manager && seat)) {
        std::cerr << "Failed to get wayland seat." << std::endl;
        return;
    }

    data_device = wl_data_device_manager_get_data_device(data_device_manager, seat);
    register_pointer_listener();
}

#ifdef __cplusplus
extern "C" {
#endif
int cloak_init(const char* title, int width, int height, const char* className) {
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

        return -1;
    }

    glfwWindowHint(GLFW_FLOATING, GLFW_TRUE);
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
        return -1;
    }


    init_wayland_clipboard();

    cloak_make_context_current();
    glfwSetCursorPosCallback(window, cloak_set_cursor_position_callback);
    glfwSetCursorEnterCallback(window, cloak_set_cursor_enter_callback);
    glfwSetMouseButtonCallback(window, cloak_set_mouse_button_callback);
    glfwSetScrollCallback(window, cloak_set_scroll_callback);
    glfwSetWindowSizeCallback(window, cloak_set_window_resize_callback);
    glfwSetKeyCallback(window, cloak_set_window_key_callback);
    glfwSetCharCallback(window, cloak_set_window_char_callback);

    if (!gladLoadGL(glfwGetProcAddress)) {
        std::cerr << "Failed to initialize GLAD" << std::endl;
        return -1;
    }

    if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
        std::cout << "Wayland window created." << std::endl;
    }

    const int client_api = glfwGetWindowAttrib(window, GLFW_CONTEXT_CREATION_API);
    const int creation_api = glfwGetWindowAttrib(window, GLFW_CONTEXT_CREATION_API);
    std::cout << "Client API: " << (client_api == GLFW_OPENGL_API ? "OpenGL" : "OpenGL ES") << std::endl;
    std::cout << "Context API: " << (creation_api == GLFW_EGL_CONTEXT_API ? "EGL" : "GLX") << std::endl;


    cloak_set_swap_interval(0);
    cloak_swap_buffers();


    return 0;
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

void cloak_set_clipboard(GenericClipboardItem* data, const char** mime_types, const int mime_count) {
    if (!data_device_manager || !data_device || !data || !last_serial) return;

    // Destroy previous source if it exists
    if (current_clipboard_source) {
        std::cout << "[Cloak-Clipboard] Destroying previous source" << std::endl;
        wl_data_source_destroy(current_clipboard_source);
        current_clipboard_source = nullptr;
    }
    std::cout << "[DEBUG] Received data pointer: " << data << std::endl;
    std::cout << "[DEBUG] data->bytes: " << data->bytes << std::endl;
    std::cout << "[DEBUG] data->size: " << data->size << std::endl;

    // Freed @ data_source_canceled
    auto* owned = static_cast<GenericClipboardItem *>(malloc(sizeof(GenericClipboardItem)));
    owned->bytes = (unsigned char*)malloc(data->size);
    memcpy(owned->bytes, data->bytes, data->size);
    owned->size = data->size;
    std::cout << "[DEBUG] Copied size: " << owned->size << std::endl;

    current_clipboard_source = wl_data_device_manager_create_data_source(data_device_manager);
    std::cout << "[Cloak-Clipboard] Setting clipboard with " << data->size << " bytes" << std::endl;

    for (int i = 0; i < mime_count; i++) {
        if (mime_types[i]) {
            std::cout << "  - " << mime_types[i] << std::endl;
            wl_data_source_offer(current_clipboard_source, mime_types[i]);
        }
    }

    wl_data_source_add_listener(
        current_clipboard_source,
        &source_listener,
        owned
    );
    std::cout << "Added listener " << std::endl;

    wl_data_device_set_selection(data_device, current_clipboard_source, last_serial);
    wl_display_flush(glfwGetWaylandDisplay());
}

#ifdef __cplusplus
}
#endif
