project('cloak', 'cpp', version : '0.1.0', default_options : ['cpp_std=c++17'])


# Find dependencies
glfw_dep = dependency('glfw3')
wayland_dep = dependency('wayland-client')
egl_dep = dependency('egl')
glew_dep = dependency('glew')
mpv_sp = subproject(
    'mpv',
    default_options : [
        'cplayer=false',
        'libmpv=true',
        'lua=disabled',
        'javascript=disabled',
        'tests=false',
        'drm=disabled',        # optional
        'wayland=enabled',     # if you need it
        'x11=disabled',        # if Wayland-only
    ]
)

glad_proj = subproject('glad')
glad_dep = glad_proj.get_variable('glad_dep')
skia_proj = subproject('skia')
cz_skia_dep = skia_proj.get_variable('cz_skia_dep')

mpv_dep = mpv_sp.get_variable('libmpv_dep')

includes = ['include', 'include/jni']



target = target_machine.system()

if target == 'windows'
    includes += 'include/jni/win32'
elif target == 'linux'
    includes += 'include/jni/linux'
endif

# Tell Meson where to find your headers
inc = include_directories(includes)

# Go into subdirectories
subdir('src')
subdir('demo')