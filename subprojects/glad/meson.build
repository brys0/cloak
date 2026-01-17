project('glad', 'c')

glad_inc = include_directories('include')

glad_lib = static_library(
    'glad',
    'src/gl.c',
    'src/egl.c',
    include_directories: glad_inc,
    install: false
)

glad_dep = declare_dependency(
    include_directories: glad_inc,
    link_with: glad_lib
)