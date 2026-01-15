#!/bin/bash
echo 'Running meson setup...'
meson setup buildDir
echo 'Finished.'

echo 'Running ninja...'
ninja -C ./buildDir
echo 'Finished building cloak.'

echo 'Adding libcloak.so to system path...'
# Get the absolute path to the compiled library
LIB_PATH=$(readlink -f ./buildDir/src/libcloak.so)

# Remove old links from both common locations
sudo rm -f /usr/lib/libcloak.so

# Create the absolute symlink
sudo ln -s "$LIB_PATH" /usr/lib/libcloak.so


# Refresh the library cache so the system "sees" the new file
sudo ldconfig
echo 'Complete.'