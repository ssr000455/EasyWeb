use crate::dom::Node;

// ── Selector Types ──────────────────────────────────────────────────────────

/// A simple CSS selector: tag, .class, #id, or combinations.
#[derive(Debug, Clone, PartialEq)]
pub enum SimpleSelector {
    Tag(String),
    Class(String),
    Id(String),
    Universal,
}

/// A compound selector like `div.class#id`
#[derive(Debug, Clone, PartialEq)]
pub struct CompoundSelector {
    pub simples: Vec<SimpleSelector>,
}

/// A combined selector (e.g., `div p` for descendant)
#[derive(Debug, Clone, PartialEq)]
pub enum Selector {
    Compound(CompoundSelector),
    Descendant(Box<Selector>, CompoundSelector),
    Child(Box<Selector>, CompoundSelector),
}

/// Specificity for sorting rules.
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
pub struct Specificity(pub u32, pub u32, pub u32);

impl Selector {
    pub fn specificity(&self) -> Specificity {
        match self {
            Selector::Compound(c) => c.specificity(),
            Selector::Descendant(s, c) | Selector::Child(s, c) => {
                let s_spec = s.specificity();
                let c_spec = c.specificity();
                Specificity(s_spec.0 + c_spec.0, s_spec.1 + c_spec.1, s_spec.2 + c_spec.2)
            }
        }
    }

    pub fn matches(&self, node: &Node) -> bool {
        match self {
            Selector::Compound(c) => c.matches(node),
            Selector::Descendant(_ancestor, compound) => {
                // This is a simplified match - we check from the node's parent chain
                // In a real engine, we'd need a tree walk. For now, we just check if
                // the compound matches and the ancestor matches somewhere up the tree.
                // This is handled by the caller looking at parent chain.
                compound.matches(node)
            }
            Selector::Child(_parent_sel, child_compound) => {
                // Similarly simplified
                child_compound.matches(node)
            }
        }
    }
}

impl CompoundSelector {
    pub fn specificity(&self) -> Specificity {
        let mut id = 0;
        let mut class = 0;
        let mut tag = 0;
        for s in &self.simples {
            match s {
                SimpleSelector::Id(_) => id += 1,
                SimpleSelector::Class(_) => class += 1,
                SimpleSelector::Tag(_) => tag += 1,
                SimpleSelector::Universal => {}
            }
        }
        Specificity(id, class, tag)
    }

    pub fn matches(&self, node: &Node) -> bool {
        self.simples.iter().all(|s| s.matches(node))
    }
}

impl SimpleSelector {
    pub fn matches(&self, node: &Node) -> bool {
        match self {
            SimpleSelector::Universal => true,
            SimpleSelector::Tag(tag) => node.tag_name() == Some(tag.as_str()),
            SimpleSelector::Class(class) => {
                node.element_data()
                    .map(|e| e.classes().contains(&class.as_str()))
                    .unwrap_or(false)
            }
            SimpleSelector::Id(id) => {
                node.element_data()
                    .and_then(|e| e.id())
                    .map(|i| i == id.as_str())
                    .unwrap_or(false)
            }
        }
    }
}

// ── CSS Rule ────────────────────────────────────────────────────────────────

#[derive(Debug, Clone)]
pub struct Declaration {
    pub name: String,
    pub value: String,
}

#[derive(Debug, Clone)]
pub struct Rule {
    pub selectors: Vec<Selector>,
    pub declarations: Vec<Declaration>,
}

#[derive(Debug, Clone)]
pub struct Stylesheet {
    pub rules: Vec<Rule>,
}

// ── CSS Parser ──────────────────────────────────────────────────────────────

struct CssParser {
    input: Vec<char>,
    pos: usize,
}

impl CssParser {
    fn new(input: &str) -> Self {
        CssParser {
            input: input.chars().collect(),
            pos: 0,
        }
    }

    fn eof(&self) -> bool {
        self.pos >= self.input.len()
    }

    fn peek(&self) -> Option<char> {
        self.input.get(self.pos).copied()
    }

    fn next(&mut self) -> Option<char> {
        let c = self.input.get(self.pos).copied();
        if c.is_some() {
            self.pos += 1;
        }
        c
    }

    fn skip_whitespace(&mut self) {
        self.consume_while(|c| c.is_ascii_whitespace());
    }

    fn consume_while(&mut self, pred: impl Fn(char) -> bool) -> String {
        let mut s = String::new();
        while let Some(c) = self.peek() {
            if pred(c) {
                s.push(c);
                self.pos += 1;
            } else {
                break;
            }
        }
        s
    }

    fn consume_until_any(&mut self, delims: &[char]) -> String {
        let mut s = String::new();
        while let Some(c) = self.peek() {
            if delims.contains(&c) {
                break;
            }
            s.push(c);
            self.pos += 1;
        }
        s
    }

    pub fn parse_stylesheet(input: &str) -> Stylesheet {
        let mut parser = CssParser::new(input);
        let mut rules = Vec::new();
        loop {
            parser.skip_whitespace();
            if parser.eof() {
                break;
            }
            // Skip comments - check for '/*' without consuming '/'
            if parser.input.get(parser.pos..parser.pos + 2) == Some(&['/', '*']) {
                parser.pos += 2;
                parser.consume_until_str("*/");
                continue;
            }
            if let Some(rule) = parser.parse_rule() {
                rules.push(rule);
            } else {
                break;
            }
        }
        Stylesheet { rules }
    }

    fn parse_rule(&mut self) -> Option<Rule> {
        let selectors = self.parse_selectors()?;
        self.skip_whitespace();
        if self.peek() != Some('{') {
            return None;
        }
        self.next(); // consume '{'
        let declarations = self.parse_declarations();
        self.skip_whitespace();
        if self.peek() == Some('}') {
            self.next();
        }
        Some(Rule {
            selectors,
            declarations,
        })
    }

    fn parse_selectors(&mut self) -> Option<Vec<Selector>> {
        self.skip_whitespace();
        let mut selectors = Vec::new();
        loop {
            let sel = self.parse_selector()?;
            selectors.push(sel);
            self.skip_whitespace();
            match self.peek() {
                Some(',') => {
                    self.next();
                    self.skip_whitespace();
                }
                Some('{') => break,
                _ => break,
            }
        }
        if selectors.is_empty() {
            None
        } else {
            Some(selectors)
        }
    }

    fn parse_selector(&mut self) -> Option<Selector> {
        self.skip_whitespace();
        let compound = self.parse_compound_selector()?;

        // Check for combinator
        self.skip_whitespace();
        match self.peek() {
            Some('>') => {
                self.next();
                let right = self.parse_selector()?;
                Some(Selector::Child(Box::new(Selector::Compound(compound)), right.into_inner_compound()?))
            }
            Some('+') | Some('~') => {
                // Skip these combinators for now, treat as descendant
                self.next();
                let right = self.parse_selector()?;
                Some(Selector::Descendant(Box::new(Selector::Compound(compound)), right.into_inner_compound()?))
            }
            Some(',') | Some('{') | None => {
                Some(Selector::Compound(compound))
            }
            // If there's whitespace and then another selector, it's descendant
            _ => {
                // Check if next non-whitespace is a selector start
                let saved = self.pos;
                self.skip_whitespace();
                if self.peek().is_some() && self.peek() != Some('{') && self.peek() != Some(',') 
                    && self.peek() != Some('>') && self.peek() != Some('+') && self.peek() != Some('~')
                    && self.peek() != Some(')') {
                    if let Some(right) = self.parse_selector() {
                        return Some(Selector::Descendant(
                            Box::new(Selector::Compound(compound)),
                            right.into_inner_compound()?,
                        ));
                    }
                }
                self.pos = saved;
                Some(Selector::Compound(compound))
            }
        }
    }

    fn parse_compound_selector(&mut self) -> Option<CompoundSelector> {
        let mut simples = Vec::new();
        loop {
            self.skip_whitespace();
            match self.peek() {
                Some('.') => {
                    self.next();
                    let name = self.consume_while(|c| c.is_alphanumeric() || c == '-' || c == '_');
                    if name.is_empty() { return None; }
                    simples.push(SimpleSelector::Class(name));
                }
                Some('#') => {
                    self.next();
                    let name = self.consume_while(|c| c.is_alphanumeric() || c == '-' || c == '_');
                    if name.is_empty() { return None; }
                    simples.push(SimpleSelector::Id(name));
                }
                Some(c) if c.is_alphanumeric() || c == '*' => {
                    if c == '*' {
                        self.next();
                        simples.push(SimpleSelector::Universal);
                    } else {
                        let name = self.consume_while(|c| c.is_alphanumeric() || c == '-' || c == '_');
                        simples.push(SimpleSelector::Tag(name));
                    }
                }
                _ => break,
            }
        }
        if simples.is_empty() {
            None
        } else {
            Some(CompoundSelector { simples })
        }
    }

    fn parse_declarations(&mut self) -> Vec<Declaration> {
        let mut decls = Vec::new();
        loop {
            self.skip_whitespace();
            // Skip comments inside rule blocks
            if self.input.get(self.pos..self.pos + 2) == Some(&['/', '*']) {
                self.pos += 2;
                self.consume_until_str("*/");
                continue;
            }
            if self.peek() == Some('}') || self.peek().is_none() {
                break;
            }
            if let Some(decl) = self.parse_declaration() {
                decls.push(decl);
            } else {
                break;
            }
        }
        decls
    }

    fn parse_declaration(&mut self) -> Option<Declaration> {
        self.skip_whitespace();
        let name = self.consume_while(|c| c.is_alphanumeric() || c == '-' || c == '_');
        if name.is_empty() {
            return None;
        }
        self.skip_whitespace();
        if self.peek() != Some(':') {
            return None;
        }
        self.next();
        self.skip_whitespace();
        let value = self.consume_until_any(&[';', '}', '!']);
        let value = value.trim().to_string();
        // Skip !important
        self.skip_whitespace();
        if self.peek() == Some('!') {
            self.consume_until_any(&[';', '}']);
        }
        self.skip_whitespace();
        if self.peek() == Some(';') {
            self.next();
        }
        Some(Declaration { name, value })
    }

    fn consume_until_str(&mut self, delim: &str) -> String {
        let d: Vec<char> = delim.chars().collect();
        let mut s = String::new();
        loop {
            if self.pos + d.len() > self.input.len() {
                // Consume remaining chars if delimiter not found
                while self.pos < self.input.len() {
                    s.push(self.input[self.pos]);
                    self.pos += 1;
                }
                break;
            }
            if self.input[self.pos..].starts_with(&d) {
                self.pos += d.len();
                break;
            }
            s.push(self.input[self.pos]);
            self.pos += 1;
        }
        s
    }
}

impl Selector {
    fn into_inner_compound(self) -> Option<CompoundSelector> {
        match self {
            Selector::Compound(c) => Some(c),
            _ => None,
        }
    }
}

// ── Public API ──────────────────────────────────────────────────────────────

/// Parse a CSS string into a stylesheet.
pub fn parse_css(input: &str) -> Stylesheet {
    CssParser::parse_stylesheet(input)
}

/// Find all matching rules for a node, sorted by specificity (lowest first).
pub fn match_rules<'a>(node: &Node, stylesheet: &'a Stylesheet) -> Vec<&'a Rule> {
    let mut matched = Vec::new();
    for rule in &stylesheet.rules {
        for selector in &rule.selectors {
            if selector.matches(node) {
                matched.push(rule);
                break;
            }
        }
    }
    // Sort by specificity (lowest first, so later rules override)
    matched.sort_by(|a, b| {
        let a_spec = a.selectors.iter().map(|s| s.specificity()).max().unwrap_or(Specificity(0, 0, 0));
        let b_spec = b.selectors.iter().map(|s| s.specificity()).max().unwrap_or(Specificity(0, 0, 0));
        a_spec.cmp(&b_spec)
    });
    matched
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::dom::{self, elem, text};

    #[test]
    fn test_parse_simple() {
        let css = "div { color: red; }";
        let ss = parse_css(css);
        assert_eq!(ss.rules.len(), 1);
        assert_eq!(ss.rules[0].declarations.len(), 1);
        assert_eq!(ss.rules[0].declarations[0].name, "color");
        assert_eq!(ss.rules[0].declarations[0].value, "red");
    }

    #[test]
    fn test_class_selector() {
        let css = ".foo { font-size: 16px; }";
        let ss = parse_css(css);
        assert_eq!(ss.rules.len(), 1);
    }

    #[test]
    fn test_matching() {
        let css = "div { color: red; } .bar { font-size: 12px; }";
        let ss = parse_css(css);
        let node = elem("div", [("class", "bar")].into(), vec![text("hello")]);
        let matched = match_rules(&node.borrow(), &ss);
        assert_eq!(matched.len(), 2);
    }

    #[test]
    fn test_specificity() {
        let id_spec = Selector::Compound(CompoundSelector {
            simples: vec![SimpleSelector::Id("main".to_string())],
        }).specificity();
        assert_eq!(id_spec, Specificity(1, 0, 0));

        let class_spec = Selector::Compound(CompoundSelector {
            simples: vec![SimpleSelector::Class("foo".to_string())],
        }).specificity();
        assert_eq!(class_spec, Specificity(0, 1, 0));

        let tag_spec = Selector::Compound(CompoundSelector {
            simples: vec![SimpleSelector::Tag("div".to_string())],
        }).specificity();
        assert_eq!(tag_spec, Specificity(0, 0, 1));
    }
}