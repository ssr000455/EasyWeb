mod dom;
mod html;
mod css;
mod style;
mod layout;
mod render;

use std::env;
use std::fs;
use std::path::Path;

use sdl2::event::Event;
use sdl2::keyboard::Keycode;

const DEFAULT_WIDTH: u32 = 1024;
const DEFAULT_HEIGHT: u32 = 768;
const SCROLL_SPEED: f32 = 40.0;

#[allow(dead_code)]
struct App {
    layout_root: Option<layout::LayoutBox>,
    scroll_x: f32,
    scroll_y: f32,
    max_scroll_y: f32,
    viewport_w: f32,
    viewport_h: f32,
    html: String,
    stylesheet: css::Stylesheet,
    styles: std::collections::HashMap<usize, style::Style>,
    doc: dom::NodeRef,
}

pub fn main() -> Result<(), String> {
    run()
}

pub fn run() -> Result<(), String> {
    let args: Vec<String> = env::args().collect();
    let html = if args.len() > 1 {
        fs::read_to_string(&args[1])
            .map_err(|e| format!("Failed to read {}: {}", args[1], e))?
    } else {
        let mut buf = String::new();
        if std::io::Read::read_to_string(&mut std::io::stdin(), &mut buf).is_ok() && !buf.trim().is_empty() {
            buf
        } else {
            include_str!("../test.html").to_string()
        }
    };

    run_with_html(html)
}

fn run_with_html(html: String) -> Result<(), String> {
    let doc = html::Parser::parse(&html);

    let stylesheet = style::collect_styles(&doc);

    let styles = style::compute_styles(&doc, &stylesheet);

    let sdl_context = sdl2::init()?;
    let video_subsystem = sdl_context.video()?;

    let window = video_subsystem
        .window("EasyWeb", DEFAULT_WIDTH, DEFAULT_HEIGHT)
        .position_centered()
        .resizable()
        .build()
        .map_err(|e| format!("Failed to create window: {}", e))?;

    let mut canvas = window
        .into_canvas()
        .accelerated()
        .build()
        .map_err(|e| format!("Failed to create canvas: {}", e))?;

    let ttf_context = sdl2::ttf::init()
        .map_err(|e| format!("Failed to init TTF: {}", e))?;

    let font_path = find_font()?;
    let font = ttf_context
        .load_font(&font_path, 16)
        .map_err(|e| format!("Failed to load font '{}': {}", font_path, e))?;

    let viewport_w = DEFAULT_WIDTH as f32;
    let viewport_h = DEFAULT_HEIGHT as f32;

    let layout_root = layout::build_layout(&doc, &styles, viewport_w, viewport_h);
    let max_scroll_y = (render::layout_height(&layout_root) - viewport_h).max(0.0);

    let mut app = App {
        layout_root: Some(layout_root),
        scroll_x: 0.0,
        scroll_y: 0.0,
        max_scroll_y,
        viewport_w,
        viewport_h,
        html,
        stylesheet,
        styles,
        doc,
    };

    let mut event_pump = sdl_context.event_pump()?;

    'running: loop {
        for event in event_pump.poll_iter() {
            match event {
                Event::Quit { .. }
                | Event::KeyDown {
                    keycode: Some(Keycode::Escape),
                    ..
                } => break 'running,

                Event::KeyDown {
                    keycode: Some(Keycode::Up),
                    ..
                } => {
                    app.scroll_y = (app.scroll_y - SCROLL_SPEED).max(0.0);
                }
                Event::KeyDown {
                    keycode: Some(Keycode::Down),
                    ..
                } => {
                    app.scroll_y = (app.scroll_y + SCROLL_SPEED).min(app.max_scroll_y);
                }
                Event::KeyDown {
                    keycode: Some(Keycode::PageUp),
                    ..
                } => {
                    app.scroll_y = (app.scroll_y - app.viewport_h * 0.8).max(0.0);
                }
                Event::KeyDown {
                    keycode: Some(Keycode::PageDown),
                    ..
                } => {
                    app.scroll_y = (app.scroll_y + app.viewport_h * 0.8).min(app.max_scroll_y);
                }
                Event::KeyDown {
                    keycode: Some(Keycode::Home),
                    ..
                } => {
                    app.scroll_y = 0.0;
                }
                Event::KeyDown {
                    keycode: Some(Keycode::End),
                    ..
                } => {
                    app.scroll_y = app.max_scroll_y;
                }
                Event::MouseWheel { y, .. } => {
                    app.scroll_y = (app.scroll_y - y as f32 * SCROLL_SPEED)
                        .max(0.0)
                        .min(app.max_scroll_y);
                }
                Event::Window { win_event, .. } => {
                    use sdl2::event::WindowEvent;
                    if let WindowEvent::Resized(w, h) = win_event {
                        app.viewport_w = w as f32;
                        app.viewport_h = h as f32;
                        let layout_root = layout::build_layout(
                            &app.doc,
                            &app.styles,
                            app.viewport_w,
                            app.viewport_h,
                        );
                        app.max_scroll_y = (render::layout_height(&layout_root) - app.viewport_h).max(0.0);
                        app.scroll_y = app.scroll_y.min(app.max_scroll_y);
                        app.layout_root = Some(layout_root);
                    }
                }
                _ => {}
            }
        }

        if let Some(ref root) = app.layout_root {
            render::render(
                &mut canvas,
                &font,
                root,
                app.scroll_x,
                app.scroll_y,
                app.viewport_w,
                app.viewport_h,
            );
        }

        std::thread::sleep(std::time::Duration::from_millis(16));
    }

    Ok(())
}

/// Find a suitable font file on the system.
fn find_font() -> Result<String, String> {
    let candidates = [
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/DroidSansFallback.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/data/data/com.termux/files/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ];

    for path in &candidates {
        if Path::new(path).exists() {
            return Ok(path.to_string());
        }
    }

    for dir in &["/system/fonts", "/usr/share/fonts"] {
        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.extension().map(|e| e == "ttf" || e == "ttc").unwrap_or(false) {
                    return Ok(p.to_string_lossy().to_string());
                }
            }
        }
    }

    Err("No font file found. Please install a font or specify one.".to_string())
}