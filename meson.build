project('cloak', 'cpp', version : '0.1.0', default_options : ['cpp_std=c++17'])

# Find dependencies
glfw_dep = dependency('glfw3')
wayland_dep = dependency('wayland-client')
gl_dep = dependency('gl')
egl_dep = dependency('egl')

# Tell Meson where to find your headers
inc = include_directories('include')

# Go into subdirectories
subdir('src')
subdir('demo')