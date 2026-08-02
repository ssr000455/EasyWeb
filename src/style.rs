use std::collections::HashMap;
use std::rc::Rc;

use crate::css;
use crate::css::Stylesheet;
use crate::dom::{Node, NodeKind, NodeRef};

/// Computed style property values.
#[derive(Debug, Clone)]
pub struct Style {
    pub properties: HashMap<String, String>,
}

impl Style {
    pub fn new() -> Self {
        Style {
            properties: HashMap::new(),
        }
    }

    /// Get a property value, or a default.
    pub fn get(&self, name: &str) -> &str {
        self.properties
            .get(name)
            .map(|s| s.as_str())
            .unwrap_or("")
    }

    /// Parse a length value (e.g., "16px" -> 16.0).
    pub fn length(&self, name: &str, default: f32) -> f32 {
        let val = self.get(name);
        if val.is_empty() {
            return default;
        }
        let val = val.trim();
        if let Some(px) = val.strip_suffix("px") {
            px.trim().parse().unwrap_or(default)
        } else if let Some(pct) = val.strip_suffix('%') {
            // Percentages are resolved later by the layout
            pct.trim().parse().unwrap_or(default)
        } else {
            val.parse().unwrap_or(default)
        }
    }

    /// Parse a length with percentage support (returns (px, is_percentage)).
    pub fn length_pct(&self, name: &str, default: f32) -> LengthValue {
        let val = self.get(name);
        if val.is_empty() {
            return LengthValue::Px(default);
        }
        let val = val.trim();
        if let Some(px) = val.strip_suffix("px") {
            LengthValue::Px(px.trim().parse().unwrap_or(default))
        } else if let Some(pct) = val.strip_suffix('%') {
            LengthValue::Pct(pct.trim().parse().unwrap_or(0.0))
        } else {
            // Try raw number as px
            LengthValue::Px(val.parse().unwrap_or(default))
        }
    }

    /// Parse a color value like "red", "#ff0000", "rgb(255,0,0)".
    pub fn color(&self, name: &str, default: (u8, u8, u8, u8)) -> (u8, u8, u8, u8) {
        let val = self.get(name);
        if val.is_empty() {
            return default;
        }
        parse_color(val).unwrap_or(default)
    }

    /// Get display property.
    pub fn display(&self) -> Display {
        match self.get("display") {
            "none" => Display::None,
            "inline" => Display::Inline,
            "inline-block" => Display::InlineBlock,
            "flex" => Display::Flex,
            "block" | _ => Display::Block,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Display {
    None,
    Block,
    Inline,
    InlineBlock,
    Flex,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum LengthValue {
    Px(f32),
    Pct(f32),
}

impl Default for Style {
    fn default() -> Self {
        Self::new()
    }
}

/// Parse a CSS color string.
pub fn parse_color(s: &str) -> Option<(u8, u8, u8, u8)> {
    let s = s.trim();
    if s.starts_with('#') {
        let hex = &s[1..];
        match hex.len() {
            3 => {
                let r = u8::from_str_radix(&hex[0..1], 16).ok()? * 17;
                let g = u8::from_str_radix(&hex[1..2], 16).ok()? * 17;
                let b = u8::from_str_radix(&hex[2..3], 16).ok()? * 17;
                Some((r, g, b, 255))
            }
            6 => {
                let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
                let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
                let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
                Some((r, g, b, 255))
            }
            8 => {
                let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
                let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
                let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
                let a = u8::from_str_radix(&hex[6..8], 16).ok()?;
                Some((r, g, b, a))
            }
            _ => None,
        }
    } else if s.starts_with("rgb(") {
        let inner = s.trim_start_matches("rgb(").trim_end_matches(')');
        let parts: Vec<&str> = inner.split(',').collect();
        if parts.len() == 3 {
            let r = parts[0].trim().parse().ok()?;
            let g = parts[1].trim().parse().ok()?;
            let b = parts[2].trim().parse().ok()?;
            Some((r, g, b, 255))
        } else {
            None
        }
    } else if s.starts_with("rgba(") {
        let inner = s.trim_start_matches("rgba(").trim_end_matches(')');
        let parts: Vec<&str> = inner.split(',').collect();
        if parts.len() == 4 {
            let r = parts[0].trim().parse().ok()?;
            let g = parts[1].trim().parse().ok()?;
            let b = parts[2].trim().parse().ok()?;
            let a = (parts[3].trim().parse::<f32>().ok()? * 255.0) as u8;
            Some((r, g, b, a))
        } else {
            None
        }
    } else {
        // Named colors (common subset)
        let named: HashMap<&str, (u8, u8, u8)> = [
            ("black", (0, 0, 0)),
            ("white", (255, 255, 255)),
            ("red", (255, 0, 0)),
            ("green", (0, 128, 0)),
            ("blue", (0, 0, 255)),
            ("yellow", (255, 255, 0)),
            ("orange", (255, 165, 0)),
            ("purple", (128, 0, 128)),
            ("gray", (128, 128, 128)),
            ("grey", (128, 128, 128)),
            ("pink", (255, 192, 203)),
            ("brown", (165, 42, 42)),
            ("navy", (0, 0, 128)),
            ("teal", (0, 128, 128)),
            ("transparent", (0, 0, 0)),
        ]
        .iter()
        .cloned()
        .collect();
        named.get(s).map(|&(r, g, b)| {
            if s == "transparent" {
                (r, g, b, 0)
            } else {
                (r, g, b, 255)
            }
        })
    }
}

/// Build a stylesheet from a `<style>` tag content and optional inline styles.
pub fn collect_styles(doc: &NodeRef) -> Stylesheet {
    let mut css_text = String::new();
    collect_style_nodes(&doc.borrow(), &mut css_text);
    css::parse_css(&css_text)
}

fn collect_style_nodes(node: &Node, css: &mut String) {
    if let NodeKind::Element(e) = &node.kind {
        if e.tag == "style" {
            for child in &node.children {
                if let NodeKind::Text(t) = &child.borrow().kind {
                    css.push_str(t);
                }
            }
        }
    }
    for child in &node.children {
        collect_style_nodes(&child.borrow(), css);
    }
}

/// Compute the style for a single node from the stylesheet.
pub fn compute_style(node: &Node, stylesheet: &Stylesheet) -> Style {
    let mut style = Style::new();

    // Apply default styles for block elements
    let tag = node.tag_name();
    apply_defaults(tag, &mut style);

    // Apply matched rules from stylesheet
    let matched = css::match_rules(node, stylesheet);
    for rule in matched {
        for decl in &rule.declarations {
            style.properties.insert(decl.name.clone(), decl.value.clone());
        }
    }

    // Apply inline style attribute
    if let Some(data) = node.element_data() {
        if let Some(inline) = data.get_attr("style") {
            let inline_ss = css::parse_css(&format!("_ {{ {} }}", inline));
            for rule in &inline_ss.rules {
                for decl in &rule.declarations {
                    style.properties.insert(decl.name.clone(), decl.value.clone());
                }
            }
        }
    }

    style
}

fn apply_defaults(tag: Option<&str>, style: &mut Style) {
    match tag {
        Some("body") | Some("html") | Some("div") | Some("p")
        | Some("h1") | Some("h2") | Some("h3") | Some("h4") | Some("h5") | Some("h6")
        | Some("ul") | Some("ol") | Some("li") | Some("table") | Some("tr")
        | Some("td") | Some("th") | Some("header") | Some("footer") | Some("nav")
        | Some("section") | Some("article") | Some("main") | Some("aside") | Some("hr") => {
            style.properties.insert("display".to_string(), "block".to_string());
        }
        Some("span") | Some("a") | Some("b") | Some("i") | Some("strong") | Some("em") => {
            style.properties.insert("display".to_string(), "inline".to_string());
        }
        _ => {}
    }

    // Default font sizes for headings
    match tag {
        Some("h1") => { style.properties.insert("font-size".to_string(), "32px".to_string()); }
        Some("h2") => { style.properties.insert("font-size".to_string(), "24px".to_string()); }
        Some("h3") => { style.properties.insert("font-size".to_string(), "18px".to_string()); }
        Some("h4") => { style.properties.insert("font-size".to_string(), "16px".to_string()); }
        Some("h5") => { style.properties.insert("font-size".to_string(), "14px".to_string()); }
        Some("h6") => { style.properties.insert("font-size".to_string(), "12px".to_string()); }
        _ => {}
    }

    // Default font-weight for bold tags
    match tag {
        Some("b") | Some("strong") => {
            style.properties.insert("font-weight".to_string(), "bold".to_string());
        }
        _ => {}
    }
    // Default margin for body
    if tag == Some("body") {
        style.properties.insert("margin".to_string(), "8px".to_string());
    }
    // Default margin for paragraphs
    if tag == Some("p") {
        style.properties.insert("margin".to_string(), "16px 0".to_string());
    }
    // Default list padding
    if tag == Some("ul") || tag == Some("ol") {
        style.properties.insert("padding-left".to_string(), "40px".to_string());
    }
    // Default hr
    if tag == Some("hr") {
        style.properties.insert("border".to_string(), "1px solid black".to_string());
        style.properties.insert("margin".to_string(), "8px 0".to_string());
    }
}

/// Recursively compute styles for all nodes in the tree.
/// Returns a map keyed by node pointer address to avoid Hash/Eq issues with Rc<RefCell<Node>>.
pub fn compute_styles(doc: &NodeRef, stylesheet: &Stylesheet) -> HashMap<usize, Style> {
    let mut map = HashMap::new();
    compute_styles_recursive(doc, stylesheet, &mut map);
    map
}

fn node_key(node: &NodeRef) -> usize {
    Rc::as_ptr(node) as *const Node as usize
}

fn compute_styles_recursive(
    node: &NodeRef,
    stylesheet: &Stylesheet,
    map: &mut HashMap<usize, Style>,
) {
    let style = compute_style(&node.borrow(), stylesheet);
    map.insert(node_key(node), style);
    for child in &node.borrow().children {
        compute_styles_recursive(child, stylesheet, map);
    }
}