fn main() {
    // NOTE: sdl2-sys does NOT read SDL2_DIR or SDL2_INCLUDE_DIR env vars.
    // We must emit cargo:rustc-link-search ourselves so the linker can find
    // the pre-built SDL2 libraries (installed by the CI workflow).
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