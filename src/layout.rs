use std::collections::HashMap;
use std::rc::Rc;

use crate::dom::{Node, NodeKind, NodeRef};
use crate::style::{Display, LengthValue, Style};

// ── Box Types ───────────────────────────────────────────────────────────────

/// A rectangular box in the layout tree.
#[derive(Debug, Clone)]
pub struct Rect {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

impl Rect {
    pub fn new(x: f32, y: f32, w: f32, h: f32) -> Self {
        Rect {
            x,
            y,
            width: w,
            height: h,
        }
    }
}

/// Edge sizes (for padding, border, margin).
#[derive(Debug, Clone, Copy, Default)]
pub struct Edges {
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
    pub left: f32,
}

impl Edges {
    pub fn all(v: f32) -> Self {
        Edges {
            top: v,
            right: v,
            bottom: v,
            left: v,
        }
    }

    pub fn horizontal(&self) -> f32 {
        self.left + self.right
    }

    pub fn vertical(&self) -> f32 {
        self.top + self.bottom
    }
}

/// A node in the layout tree.
#[derive(Debug)]
pub struct LayoutBox {
    pub box_type: BoxType,
    pub children: Vec<LayoutBox>,
    pub rect: Rect,
    pub padding: Edges,
    pub border: Edges,
    pub margin: Edges,
    /// Style for rendering (colors, backgrounds, etc.)
    pub style: Option<Style>,
    /// Reference back to the DOM node (for rendering text, etc.)
    pub dom_node: Option<NodeRef>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum BoxType {
    Block,
    Inline,
    InlineBlock,
    Text,
    Anonymous,
}

impl LayoutBox {
    fn new(box_type: BoxType) -> Self {
        LayoutBox {
            box_type,
            children: Vec::new(),
            rect: Rect::new(0.0, 0.0, 0.0, 0.0),
            padding: Edges::default(),
            border: Edges::default(),
            margin: Edges::default(),
            style: None,
            dom_node: None,
        }
    }

    pub fn content_area(&self) -> Rect {
        Rect::new(
            self.rect.x + self.padding.left + self.border.left,
            self.rect.y + self.padding.top + self.border.top,
            self.rect.width - self.padding.horizontal() - self.border.horizontal(),
            self.rect.height - self.padding.vertical() - self.border.vertical(),
        )
    }

    pub fn padding_area(&self) -> Rect {
        Rect::new(
            self.rect.x + self.border.left,
            self.rect.y + self.border.top,
            self.rect.width - self.border.horizontal(),
            self.rect.height - self.border.vertical(),
        )
    }

    #[allow(dead_code)]
    pub fn margin_area(&self) -> Rect {
        Rect::new(
            self.rect.x - self.margin.left,
            self.rect.y - self.margin.top,
            self.rect.width + self.margin.horizontal(),
            self.rect.height + self.margin.vertical(),
        )
    }
}

// ── Layout Engine ───────────────────────────────────────────────────────────

pub struct LayoutEngine {
    styles: HashMap<usize, Style>,
    viewport_width: f32,
    viewport_height: f32,
}

impl LayoutEngine {
    pub fn new(styles: HashMap<usize, Style>, viewport_width: f32, viewport_height: f32) -> Self {
        LayoutEngine {
            styles,
            viewport_width,
            viewport_height,
        }
    }

    /// Build the layout tree from the DOM and run layout.
    pub fn layout(&self, root: &NodeRef) -> LayoutBox {
        let mut layout_root = self.build_layout_tree(root);
        self.calculate_layout(&mut layout_root);
        layout_root
    }

    /// Build a layout tree from the DOM tree.
    fn build_layout_tree(&self, node: &NodeRef) -> LayoutBox {
        let node_borrow = node.borrow();
        let style = {
            let key = Rc::as_ptr(node) as *const Node as usize;
            self.styles.get(&key)
        };

        match &node_borrow.kind {
            NodeKind::Document => {
                // Document node: find the html element
                let mut root_box = LayoutBox::new(BoxType::Block);
                root_box.style = style.cloned();
                for child in &node_borrow.children {
                    let child_box = self.build_layout_tree(child);
                    root_box.children.push(child_box);
                }
                root_box
            }
            NodeKind::Element(_e) => {
                let display = style.map(|s| s.display()).unwrap_or(Display::Block);
                match display {
                    Display::None => LayoutBox {
                        box_type: BoxType::Block,
                        children: Vec::new(),
                        rect: Rect::new(0.0, 0.0, 0.0, 0.0),
                        padding: Edges::default(),
                        border: Edges::default(),
                        margin: Edges::default(),
                        style: style.cloned(),
                        dom_node: Some(node.clone()),
                    },
                    Display::Inline => {
                        let mut box_ = LayoutBox::new(BoxType::Inline);
                        box_.style = style.cloned();
                        box_.dom_node = Some(node.clone());
                        // Inline elements can contain inline children and text
                        for child in &node_borrow.children {
                            let child_box = self.build_layout_tree(child);
                            if child_box.box_type != BoxType::Text || self.has_visible_text(&child_box) {
                                box_.children.push(child_box);
                            }
                        }
                        box_
                    }
                    Display::InlineBlock => {
                        let mut box_ = LayoutBox::new(BoxType::InlineBlock);
                        box_.style = style.cloned();
                        box_.dom_node = Some(node.clone());
                        for child in &node_borrow.children {
                            let child_box = self.build_layout_tree(child);
                            box_.children.push(child_box);
                        }
                        box_
                    }
                    Display::Flex => {
                        let mut box_ = LayoutBox::new(BoxType::Block);
                        box_.style = style.cloned();
                        box_.dom_node = Some(node.clone());
                        // Flex children
                        for child in &node_borrow.children {
                            let child_box = self.build_layout_tree(child);
                            box_.children.push(child_box);
                        }
                        box_
                    }
                    Display::Block => {
                        let mut box_ = LayoutBox::new(BoxType::Block);
                        box_.style = style.cloned();
                        box_.dom_node = Some(node.clone());
                        self.build_block_children(&node_borrow, &mut box_);
                        box_
                    }
                }
            }
            NodeKind::Text(_t) => {
                let mut box_ = LayoutBox::new(BoxType::Text);
                box_.dom_node = Some(node.clone());
                box_
            }
        }
    }

    fn has_visible_text(&self, box_: &LayoutBox) -> bool {
        if let Some(ref node) = box_.dom_node {
            if let NodeKind::Text(t) = &node.borrow().kind {
                return !t.trim().is_empty();
            }
        }
        false
    }

    fn build_block_children(&self, node: &crate::dom::Node, parent: &mut LayoutBox) {
        // Collect inline children into anonymous blocks
        let mut inline_group: Vec<LayoutBox> = Vec::new();

        for child in &node.children {
            let child_box = self.build_layout_tree(child);
            let is_inline = child_box.box_type == BoxType::Inline
                || child_box.box_type == BoxType::InlineBlock
                || child_box.box_type == BoxType::Text;

            if is_inline {
                if self.has_visible_text(&child_box) || child_box.box_type != BoxType::Text {
                    inline_group.push(child_box);
                }
            } else {
                // Flush inline group as anonymous block
                if !inline_group.is_empty() {
                    let mut anon = LayoutBox::new(BoxType::Anonymous);
                    anon.children = std::mem::take(&mut inline_group);
                    parent.children.push(anon);
                }
                parent.children.push(child_box);
            }
        }

        // Flush remaining inline
        if !inline_group.is_empty() {
            let mut anon = LayoutBox::new(BoxType::Anonymous);
            anon.children = inline_group;
            parent.children.push(anon);
        }
    }

    // ── Layout Calculation ──────────────────────────────────────────────────

    fn calculate_layout(&self, layout_box: &mut LayoutBox) {
        self.set_box_properties(layout_box);
        self.layout_children(layout_box);
        self.position_children(layout_box);
    }

    fn set_box_properties(&self, box_: &mut LayoutBox) {
        if let Some(ref style) = box_.style {
            // Parse margin
            let margin = self.parse_box_edge(style, "margin", 0.0);
            box_.margin = margin;
            // Parse padding
            let padding = self.parse_box_edge(style, "padding", 0.0);
            box_.padding = padding;
            // Parse border (simplified: just border-width)
            let border_width = style.length("border-width", 0.0);
            let border_width = if border_width > 0.0 || style.get("border").contains("1px") || style.get("border").contains("solid") {
                border_width.max(1.0)
            } else {
                border_width
            };
            box_.border = Edges::all(border_width);

            // Set width/height from style
            let w = style.length_pct("width", 0.0);
            let h = style.length_pct("height", 0.0);

            match w {
                LengthValue::Px(px) => { box_.rect.width = px; }
                LengthValue::Pct(pct) => { box_.rect.width = self.viewport_width * pct / 100.0; }
            }
            match h {
                LengthValue::Px(px) => { box_.rect.height = px; }
                LengthValue::Pct(pct) => { box_.rect.height = self.viewport_height * pct / 100.0; }
            }
        }
    }

    fn parse_box_edge(&self, style: &Style, base: &str, default: f32) -> Edges {
        // Try shorthand values like "10px 5px 10px 5px" or "10px 5px" or "10px"
        let full = style.get(base);
        if full.is_empty() {
            return Edges::all(default);
        }
        let parts: Vec<&str> = full.split_whitespace().collect();
        let vals: Vec<f32> = parts
            .iter()
            .map(|s| {
                let s = s.trim();
                if let Some(px) = s.strip_suffix("px") {
                    px.parse().unwrap_or(0.0)
                } else {
                    s.parse().unwrap_or(0.0)
                }
            })
            .collect();

        match vals.len() {
            1 => Edges::all(vals[0]),
            2 => Edges {
                top: vals[0],
                right: vals[1],
                bottom: vals[0],
                left: vals[1],
            },
            3 => Edges {
                top: vals[0],
                right: vals[1],
                bottom: vals[2],
                left: vals[1],
            },
            4 => Edges {
                top: vals[0],
                right: vals[1],
                bottom: vals[2],
                left: vals[3],
            },
            _ => Edges::all(default),
        }
    }

    fn layout_children(&self, box_: &mut LayoutBox) {
        // Layout children based on box type
        match box_.box_type {
            BoxType::Block | BoxType::Anonymous => {
                // Check if parent is flex
                let is_flex = box_.style.as_ref().map(|s| s.display() == Display::Flex).unwrap_or(false);
                if is_flex {
                    self.layout_flex_children(box_);
                } else {
                    self.layout_block_children(box_);
                }
            }
            BoxType::Inline | BoxType::InlineBlock => {
                self.layout_inline_children(box_);
            }
            BoxType::Text => {
                // Text boxes have no children to layout
            }
        }
    }

    fn layout_block_children(&self, box_: &mut LayoutBox) {
        let content = box_.content_area();
        let mut cursor_y = content.y;

        for child in &mut box_.children {
            self.calculate_layout(child);

            // Position block child
            child.rect.x = content.x + child.margin.left;
            child.rect.y = cursor_y + child.margin.top;

            // If width is auto, stretch to fill
            let has_style_width = child.style.as_ref().map(|s| !s.get("width").is_empty()).unwrap_or(false);
            if !has_style_width {
                child.rect.width = content.width - child.margin.horizontal();
            }

            cursor_y = child.rect.y + child.rect.height + child.margin.bottom;
        }

        // Auto-height: expand to fit children
        if box_.rect.height == 0.0 && !box_.children.is_empty() {
            let last = box_.children.last().unwrap();
            box_.rect.height = (last.rect.y + last.rect.height + last.margin.bottom + box_.padding.bottom + box_.border.bottom)
                - box_.rect.y;
        }
    }

    fn layout_flex_children(&self, box_: &mut LayoutBox) {
        let content = box_.content_area();
        let direction = box_.style.as_ref().map(|s| s.get("flex-direction")).unwrap_or("");
        let is_row = direction.is_empty() || direction == "row";

        if is_row {
            // Row layout: children side by side
            let mut cursor_x = content.x;
            // First pass: compute natural widths
            let mut flex_items: Vec<usize> = Vec::new();
            let mut total_flex = 0.0;
            let mut total_width = 0.0;

            for child in &mut box_.children {
                self.calculate_layout(child);
                let flex_grow: f32 = child.style.as_ref()
                    .and_then(|s| {
                        let f = s.get("flex-grow");
                        if f.is_empty() { None } else { f.parse().ok() }
                    })
                    .unwrap_or(0.0);
                if flex_grow > 0.0 {
                    flex_items.push(flex_items.len());
                    total_flex += flex_grow;
                } else {
                    total_width += child.rect.width + child.margin.horizontal();
                }
            }

            // Distribute remaining space
            let remaining = (content.width - total_width).max(0.0);
            let _extra = 0.0;
            let extra_per_flex = if total_flex > 0.0 { remaining / total_flex } else { 0.0 };

            for (_i, child) in box_.children.iter_mut().enumerate() {
                let flex_grow: f32 = child.style.as_ref()
                    .and_then(|s| {
                        let f = s.get("flex-grow");
                        if f.is_empty() { None } else { f.parse().ok() }
                    })
                    .unwrap_or(0.0);
                if flex_grow > 0.0 {
                    child.rect.width = extra_per_flex * flex_grow;
                }
                child.rect.x = cursor_x + child.margin.left;
                child.rect.y = content.y + child.margin.top;
                cursor_x = child.rect.x + child.rect.width + child.margin.right;
            }
        } else {
            // Column layout: same as block
            self.layout_block_children(box_);
        }

        // Auto-height
        if box_.rect.height == 0.0 && !box_.children.is_empty() {
            let last = box_.children.last().unwrap();
            box_.rect.height = (last.rect.y + last.rect.height + last.margin.bottom + box_.padding.bottom + box_.border.bottom)
                - box_.rect.y;
        }
    }

    fn layout_inline_children(&self, box_: &mut LayoutBox) {
        // Inline containers don't need to position children themselves;
        // the parent anonymous block handles inline layout.
        for child in &mut box_.children {
            self.calculate_layout(child);
        }
    }

    fn position_children(&self, box_: &mut LayoutBox) {
        // Handle inline layout inside anonymous blocks
        if box_.box_type == BoxType::Anonymous {
            self.layout_inline_line(box_);
        }
        // Handle inline children inside block containers
        if box_.box_type == BoxType::Block {
            let has_inline_children = box_.children.iter().any(|c| c.box_type == BoxType::Anonymous);
            if has_inline_children {
                for child in &mut box_.children {
                    if child.box_type == BoxType::Anonymous {
                        self.layout_inline_line(child);
                    }
                }
            }
        }
    }

    /// Layout inline children as wrapped lines within an anonymous block.
    fn layout_inline_line(&self, box_: &mut LayoutBox) {
        let content = box_.content_area();
        let mut cursor_x = content.x;
        let mut cursor_y = content.y;
        let mut max_line_height = 0.0f32;

        for child in box_.children.iter_mut() {
            self.calculate_layout(child);

            let font_size = child.style.as_ref().map(|s| s.length("font-size", 16.0)).unwrap_or(16.0);
            let estimated_width = match &child.box_type {
                BoxType::Text => {
                    self.measure_text_width(child, font_size)
                }
                _ => {
                    // Inline elements: just use their declared width
                    let char_count = self.get_text_length(child);
                    if char_count > 0 {
                        self.measure_text_width(child, font_size)
                    } else {
                        child.rect.width
                    }
                }
            };

            child.rect.width = estimated_width;
            child.rect.height = font_size * 1.4; // line height

            // Check if we need to wrap
            if cursor_x + estimated_width > content.x + content.width && cursor_x > content.x {
                cursor_x = content.x;
                cursor_y += max_line_height;
                max_line_height = 0.0;
            }

            child.rect.x = cursor_x;
            child.rect.y = cursor_y;

            cursor_x += child.rect.width;
            max_line_height = max_line_height.max(child.rect.height);
        }

        // Set anonymous block height
        if !box_.children.is_empty() {
            let last_child = box_.children.last().unwrap();
            box_.rect.height = (last_child.rect.y + last_child.rect.height + box_.padding.bottom + box_.border.bottom)
                - box_.rect.y;
        }
    }

    /// Estimate text width using per-character width ratios.
    /// Provides much better accuracy than a flat 0.6 multiplier.
    fn measure_text_width(&self, box_: &LayoutBox, font_size: f32) -> f32 {
        if let Some(ref node) = box_.dom_node {
            if let NodeKind::Text(t) = &node.borrow().kind {
                let text = t.trim();
                let mut width = 0.0f32;
                for ch in text.chars() {
                    width += font_size * char_width_ratio(ch);
                }
                return width;
            }
        }
        0.0
    }

    fn get_text_length(&self, box_: &LayoutBox) -> usize {
        if let Some(ref node) = box_.dom_node {
            if let NodeKind::Text(t) = &node.borrow().kind {
                // Count visible characters (trimmed)
                return t.trim().chars().count();
            }
        }
        0
    }
}

// ── Helper: compute layout for the full tree ───────────────────────────────

pub fn build_layout(
    doc: &NodeRef,
    styles: &HashMap<usize, Style>,
    width: f32,
    height: f32,
) -> LayoutBox {
    let engine = LayoutEngine::new(styles.clone(), width, height);
    engine.layout(doc)
}

/// Per-character width ratio relative to font size.
/// Provides significantly better layout accuracy than a flat multiplier.
fn char_width_ratio(c: char) -> f32 {
    match c {
        // Narrow characters
        ' ' => 0.35,
        '!' => 0.33,
        '"' => 0.45,
        '\'' => 0.28,
        '(' | ')' | '[' | ']' | '{' | '}' => 0.35,
        ',' => 0.28,
        '.' => 0.28,
        ':' | ';' => 0.28,
        'I' | 'l' | '|' | '/' | '\\' => 0.33,
        'f' | 'i' | 'j' | 't' => 0.35,
        '`' => 0.28,
        // Medium characters
        '0'..='9' => 0.55,
        'a' | 'b' | 'c' | 'd' | 'e' | 'g' | 'h' | 'k' | 'n' | 'o' | 'p' | 'q' | 's' | 'u' | 'v' | 'x' | 'y' | 'z' => 0.52,
        'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'G' | 'H' | 'J' | 'K' | 'L' | 'N' | 'O' | 'P' | 'Q' | 'R' | 'S' | 'T' | 'U' | 'V' | 'X' | 'Y' | 'Z' => 0.62,
        // Wide characters
        'm' | 'w' => 0.72,
        'M' | 'W' => 0.82,
        // CJK
        '\u{4e00}'..='\u{9fff}' | '\u{3000}'..='\u{303f}' | '\u{ff00}'..='\u{ffef}' => 1.0,
        // Default
        _ => 0.5,
    }
}