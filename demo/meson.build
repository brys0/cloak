# The original simple version
executable('cloak-simple',
           'main-simple.cpp',
           dependencies : [cloak_dep, glad_dep, mpv_dep, cloak_mpv_dep]
)

# The new Skia version
executable('cloak-skia',
           'main-skia.cpp',
           cpp_args: ['-DSK_GL', '-DSK_GANESH'],
           dependencies : [cloak_dep, glad_dep, mpv_dep, cz_skia_dep, cloak_mpv_dep]
)