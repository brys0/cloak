cloak_lib = shared_library('cloak',
                            'cloak.cpp',
                            include_directories : inc,
                            dependencies : [glfw_dep, wayland_dep, egl_dep],
                            install : true
)

# Declare this as an internal dependency for the demo to use
cloak_dep = declare_dependency(
    include_directories : inc,
    link_with : cloak_lib
)