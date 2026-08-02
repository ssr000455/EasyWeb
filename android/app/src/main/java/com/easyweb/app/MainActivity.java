package com.easyweb.app;

import org.libsdl.app.SDLActivity;

/**
 * Main entry point for EasyWeb on Android.
 * SDL2's SDLActivity handles all the native library loading
 * and event loop. The actual Rust code runs via SDL_main.
 */
public class MainActivity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        return new String[]{
            "SDL2",
            "SDL2_ttf",
            "SDL2_image",
            "easyweb"
        };
    }
}