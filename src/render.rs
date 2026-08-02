use sdl2::pixels::Color;
use sdl2::rect::Rect as SdlRect;
use sdl2::render::Canvas;
use sdl2::ttf::Font;
use sdl2::video::Window;

use crate::dom::NodeKind;
use crate::layout::{BoxType, LayoutBox, Rect};

/// Render the layout tree to an SDL2 canvas.
pub fn render(
    canvas: &mut Canvas<Window>,
    font: &Font,
    layout_root: &LayoutBox,
    scroll_x: f32,
    scroll_y: f32,
    viewport_w: f32,
    viewport_h: f32,
) {
    canvas.set_draw_color(Color::RGB(255, 255, 255));
    canvas.clear();
    render_box(canvas, font, layout_root, scroll_x, scroll_y, viewport_w, viewport_h);
    canvas.present();
}

fn render_box(
    canvas: &mut Canvas<Window>,
    font: &Font,
    box_: &LayoutBox,
    scroll_x: f32,
    scroll_y: f32,
    viewport_w: f32,
    viewport_h: f32,
) {
    // Skip if off-screen
    let screen_rect = Rect::new(-scroll_x, -scroll_y, viewport_w, viewport_h);
    if !rects_overlap(&box_.rect, &screen_rect) {
        return;
    }

    // Render background and border
    render_background(canvas, box_, scroll_x, scroll_y);
    render_border(canvas, box_, scroll_x, scroll_y);

    // Render text content
    if box_.box_type == BoxType::Text {
        render_text(canvas, font, box_, scroll_x, scroll_y);
    }

    // Render children
    for child in &box_.children {
        render_box(canvas, font, child, scroll_x, scroll_y, viewport_w, viewport_h);
    }
}

fn render_background(canvas: &mut Canvas<Window>, box_: &LayoutBox, scroll_x: f32, scroll_y: f32) {
    if let Some(ref style) = box_.style {
        let bg = style.color("background-color", (0, 0, 0, 0));
        if bg.3 > 0 {
            let pa = box_.padding_area();
            let x = (pa.x - scroll_x).round() as i32;
            let y = (pa.y - scroll_y).round() as i32;
            let w = pa.width.round() as u32;
            let h = pa.height.round() as u32;

            if w > 0 && h > 0 {
                canvas.set_draw_color(Color::RGBA(bg.0, bg.1, bg.2, bg.3));
                let _ = canvas.fill_rect(SdlRect::new(x, y, w, h));
            }
        }
    }
}

fn render_border(canvas: &mut Canvas<Window>, box_: &LayoutBox, scroll_x: f32, scroll_y: f32) {
    if box_.border.top == 0.0 && box_.border.right == 0.0
        && box_.border.bottom == 0.0 && box_.border.left == 0.0
    {
        return;
    }

    let border_color = box_.style
        .as_ref()
        .map(|s| s.color("border-color", (0, 0, 0, 255)))
        .unwrap_or((0, 0, 0, 255));

    canvas.set_draw_color(Color::RGBA(border_color.0, border_color.1, border_color.2, border_color.3));

    let r = &box_.rect;
    let x = (r.x - scroll_x).round() as i32;
    let y = (r.y - scroll_y).round() as i32;
    let w = r.width.round() as i32;
    let h = r.height.round() as i32;
    let b_top = box_.border.top.round() as u32;
    let b_right = box_.border.right.round() as u32;
    let b_bottom = box_.border.bottom.round() as u32;
    let b_left = box_.border.left.round() as u32;

    // Top
    if b_top > 0 {
        let _ = canvas.fill_rect(SdlRect::new(x, y, w as u32, b_top));
    }
    // Bottom
    if b_bottom > 0 {
        let _ = canvas.fill_rect(SdlRect::new(x, y + h - b_bottom as i32, w as u32, b_bottom));
    }
    // Left
    if b_left > 0 {
        let _ = canvas.fill_rect(SdlRect::new(x, y, b_left, h as u32));
    }
    // Right
    if b_right > 0 {
        let _ = canvas.fill_rect(SdlRect::new(x + w - b_right as i32, y, b_right, h as u32));
    }
}

fn render_text(canvas: &mut Canvas<Window>, font: &Font, box_: &LayoutBox, scroll_x: f32, scroll_y: f32) {
    if let Some(ref node) = box_.dom_node {
        if let NodeKind::Text(t) = &node.borrow().kind {
            let text = t.trim();
            if text.is_empty() {
                return;
            }

            // Check if we have a cached texture
            let color = box_.style
                .as_ref()
                .map(|s| s.color("color", (0, 0, 0, 255)))
                .unwrap_or((0, 0, 0, 255));

            let x = (box_.rect.x - scroll_x).round() as i32;
            let y = (box_.rect.y - scroll_y).round() as i32;

            // Render text using SDL2_ttf blit
            if let Ok(surface) = font
                .render(text)
                .blended(Color::RGBA(color.0, color.1, color.2, color.3))
            {
                let w = surface.width();
                let h = surface.height();

                let texture_creator = canvas.texture_creator();
                let texture_result = surface.as_texture(&texture_creator);
                if let Ok(texture) = texture_result {
                    let _ = canvas.copy(
                        &texture,
                        None,
                        SdlRect::new(x, y, w, h),
                    );
                }
            }
        }
    }
}

fn rects_overlap(a: &Rect, b: &Rect) -> bool {
    a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y
}

/// Calculate the total height of the layout tree for scrolling.
pub fn layout_height(root: &LayoutBox) -> f32 {
    root.rect.y + root.rect.height
}