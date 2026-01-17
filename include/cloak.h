//
// Created by brys0 on 1/2/26.
//

#ifndef CLOAK_H
#define CLOAK_H
#define GLFW_INCLUDE_NONE   // must be before glfw3.h
#include <GLFW/glfw3.h>

#ifdef __cplusplus
extern "C" {
#endif
    typedef void (*GLFuncPtr)();
    GLFuncPtr cloak_get_proc_address(void* ctx, const char* name);

    enum EventType {
        EVENT_MOVE = 0,
        EVENT_SCROLL = 1,
        EVENT_CLICK = 2,
        EVENT_KEY = 3,
        EVENT_WINDOW_RESIZE = 4,

        EVENT_CHAR = 5
    };

    struct CloakEvent {
        int type; // Move, Click, or Keyboard key
        int subtype; // If move, is it enter, exit, etc. if click is it pressed, released? If key is it pressed, released?
        int value; // The actual key
        int mod; // Any key modifiers?
        float x;
        float y;
        float scrollX;
        float scrollY;
    };

    struct GenericClipboardItem {
        void* bytes;
        size_t size;
    };
    /**
     * Creates the underlying window with strict wayland context required.
     * @return true if window creation was successful, otherwise false.
     */
    int cloak_init(const char* title, int width, int height, const char* className = "cloak_app");


    void cloak_show_window();
    // void cloak_set_cursor_position_callback(GLFWwindow *_, double x, double y);

    void* cloak_get_current_context();
    /**
     * Makes the gl context current, needed to use opengl calls
     */
    void cloak_make_context_current();

    /**
     * Returns nanosecond precision time to calculate time animations
     * @return double
     */
    double cloak_get_time();

    /**
     * Sets the swap interval
     * @param interval 0 = No Sync, 1 = VSYNC, = 2 Half of monitor
     */
    void cloak_set_swap_interval(int interval);


    /**
     * Whether the window should be closed or not.
     */
    bool cloak_should_close();

    /**
     * Get the current framebuffer size, to calculate right draw calls
     * @param width Sets ptr to actual width of window
     * @param height Sets ptr to actual height of window
     */
    void cloak_get_framebuffer_size(int* width, int* height);

    /**
     * Commit buffer to window
     */
    void cloak_swap_buffers();
    /**
     * Process GLFW events.
     */
    void cloak_poll_window_events();

    /**
     * Poll any pointer events, gives a copy to the first obj in the queue
     */
    bool cloak_poll_input_event(CloakEvent* event);

    /**
     * Set clipboard text contents, only supports text/plain for now.
     */
    void cloak_set_clipboard(GenericClipboardItem* data, const char** mime_types, int mime_count);

#ifdef __cplusplus
}
#endif
#endif //CLOAK_H