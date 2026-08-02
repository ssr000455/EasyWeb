fn main() {
    // For Android cross-compilation, the SDL2_DIR must be set by the workflow
    // after building SDL2 from source for the Android target.
    // The sdl2-sys crate will pick up SDL2_DIR automatically.
    if let Ok(dir) = std::env::var("SDL2_DIR") {
        println!("cargo:rustc-link-search=native={}/lib", dir);
    }
    if let Ok(dir) = std::env::var("SDL2_INCLUDE_DIR") {
        println!("cargo:include={}", dir);
    }
    if let Ok(dir) = std::env::var("SDL2_LIB_DIR") {
        println!("cargo:rustc-link-search=native={}", dir);
    }
}