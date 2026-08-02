use std::collections::HashMap;

use crate::dom::{self, NodeRef};

// ── Tokenizer ───────────────────────────────────────────────────────────────

#[derive(Debug, Clone, PartialEq)]
pub enum Token {
    DocType,
    StartTag {
        tag: String,
        attrs: HashMap<String, String>,
        self_closing: bool,
    },
    EndTag(String),
    Comment(String),
    Text(String),
}

struct Tokenizer {
    input: Vec<char>,
    pos: usize,
}

impl Tokenizer {
    fn new(input: &str) -> Self {
        Tokenizer {
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

    fn consume_until(&mut self, pred: impl Fn(char) -> bool) -> String {
        let mut s = String::new();
        while let Some(c) = self.peek() {
            if pred(c) {
                break;
            }
            s.push(c);
            self.pos += 1;
        }
        s
    }

    fn skip_whitespace(&mut self) {
        self.consume_while(|c| c.is_ascii_whitespace());
    }

    fn read_attr_value(&mut self, quote: char) -> String {
        let mut value = String::new();
        loop {
            match self.next() {
                Some(c) if c == quote => break,
                Some(c) => value.push(c),
                None => break,
            }
        }
        value
    }

    fn read_attrs(&mut self) -> HashMap<String, String> {
        let mut attrs = HashMap::new();
        loop {
            self.skip_whitespace();
            match self.peek() {
                None | Some('>') | Some('/') => break,
                _ => {}
            }
            let name = self.consume_while(|c| !c.is_ascii_whitespace() && c != '=' && c != '>' && c != '/');
            if name.is_empty() {
                break;
            }
            self.skip_whitespace();
            let value = if self.peek() == Some('=') {
                self.next();
                self.skip_whitespace();
                match self.next() {
                    Some('"') => self.read_attr_value('"'),
                    Some('\'') => self.read_attr_value('\''),
                    Some(c) => {
                        let mut v = String::new();
                        v.push(c);
                        v.push_str(&self.consume_while(|c| !c.is_ascii_whitespace() && c != '>'));
                        v
                    }
                    None => String::new(),
                }
            } else {
                String::new()
            };
            attrs.insert(name.to_lowercase(), value);
        }
        attrs
    }

    /// Read a tag name, lowercasing it.
    fn read_tag_name(&mut self) -> String {
        self.consume_while(|c| c.is_alphanumeric() || c == '-' || c == '_')
            .to_lowercase()
    }

    fn tokenize(&mut self) -> Vec<Token> {
        let mut tokens = Vec::new();
        loop {
            if self.eof() {
                break;
            }
            match self.peek() {
                Some('<') => {
                    self.next(); // consume '<'
                    if self.peek() == Some('/') {
                        // End tag
                        self.next();
                        self.skip_whitespace();
                        let tag = self.read_tag_name();
                        self.skip_whitespace();
                        let _ = self.consume_until(|c| c == '>');
                        self.next(); // consume '>'
                        tokens.push(Token::EndTag(tag));
                    } else if self.peek() == Some('!') {
                        // Comment or doctype
                        self.next();
                        if self.peek() == Some('-') {
                            self.next();
                            if self.peek() == Some('-') {
                                self.next();
                                let content = self.consume_until_str("-->");
                                tokens.push(Token::Comment(content));
                            } else {
                                // not a comment, treat as text
                                tokens.push(Token::Text("<!-".to_string()));
                            }
                        } else {
                            // doctype or other
                            let _ = self.consume_until(|c| c == '>');
                            self.next();
                            tokens.push(Token::DocType);
                        }
                    } else if self.peek() == Some('?') {
                        // processing instruction, skip
                        let _ = self.consume_until(|c| c == '>');
                        self.next();
                    } else {
                        // Start tag
                        self.skip_whitespace();
                        let tag = self.read_tag_name();
                        let attrs = self.read_attrs();
                        let self_closing = self.peek() == Some('/');
                        if self_closing {
                            self.next(); // consume '/'
                        }
                        if self.peek() == Some('>') {
                            self.next();
                        }
                        tokens.push(Token::StartTag {
                            tag,
                            attrs,
                            self_closing,
                        });
                    }
                }
                _ => {
                    let text = self.consume_until(|c| c == '<');
                    if !text.is_empty() {
                        tokens.push(Token::Text(text));
                    }
                }
            }
        }
        tokens
    }

    fn consume_until_str(&mut self, delim: &str) -> String {
        let d: Vec<char> = delim.chars().collect();
        let mut s = String::new();
        loop {
            if self.pos + d.len() > self.input.len() {
                // Append remaining characters without modifying input
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

// ── Parser ──────────────────────────────────────────────────────────────────

// Void elements that don't have closing tags
const VOID_ELEMENTS: &[&str] = &[
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr",
];

/// HTML parsing modes for certain container elements.
#[derive(PartialEq)]
enum InsertMode {
    Normal,
    InTable,
}

pub struct Parser {
    tokens: Vec<Token>,
    pos: usize,
    tree: NodeRef,
    insert_mode: InsertMode,
}

impl Parser {
    pub fn new(tokens: Vec<Token>) -> Self {
        Parser {
            tokens,
            pos: 0,
            tree: dom::Node::new_rc(dom::NodeKind::Document),
            insert_mode: InsertMode::Normal,
        }
    }

    pub fn parse(html: &str) -> NodeRef {
        let mut tok = Tokenizer::new(html);
        let tokens = tok.tokenize();
        let mut parser = Parser::new(tokens);
        parser.run();
        parser.tree
    }

    #[allow(dead_code)]
    fn eof(&self) -> bool {
        self.pos >= self.tokens.len()
    }

    #[allow(dead_code)]
    fn peek(&self) -> Option<&Token> {
        self.tokens.get(self.pos)
    }

    fn next(&mut self) -> Option<Token> {
        if self.pos < self.tokens.len() {
            let t = self.tokens[self.pos].clone();
            self.pos += 1;
            Some(t)
        } else {
            None
        }
    }

    fn run(&mut self) {
        let mut stack: Vec<NodeRef> = vec![self.tree.clone()];

        while let Some(token) = self.next() {
            match token {
                Token::StartTag {
                    tag,
                    attrs,
                    self_closing,
                } => {
                    let is_void = VOID_ELEMENTS.contains(&tag.as_str());
                    let node = dom::elem(&tag, attrs.clone(), vec![]);

                    // If we're in a table mode and encounter certain tags, close the table
                    if self.insert_mode == InsertMode::InTable && tag == "table" {
                        // nested table, close current table
                        self.pop_until(&mut stack, "table");
                        self.insert_mode = InsertMode::Normal;
                    }

                    if let Some(parent) = stack.last() {
                        parent.borrow_mut().append(node.clone());
                    }

                    if !is_void && !self_closing {
                        stack.push(node.clone());

                        // Track insertion mode for table
                        if tag == "table" {
                            self.insert_mode = InsertMode::InTable;
                        } else if tag == "tbody" || tag == "thead" || tag == "tfoot" || tag == "tr" {
                            // stay in table mode
                        } else if tag == "td" || tag == "th" {
                            // stay in table mode
                        } else if tag == "template" || tag == "select" {
                            // simplified
                        } else if self.insert_mode == InsertMode::InTable && tag != "caption" {
                            // In table mode, most tags pop us out
                            // Simplified: just leave table mode for non-table children
                            self.insert_mode = InsertMode::Normal;
                        }
                    }
                }
                Token::EndTag(tag) => {
                    if tag == "table" {
                        self.insert_mode = InsertMode::Normal;
                    }
                    self.pop_until(&mut stack, &tag);
                }
                Token::Text(text) => {
                    if let Some(parent) = stack.last() {
                        // Don't insert text outside of body
                        let parent_tag = parent.borrow().tag_name().map(|s| s.to_string());
                        if parent_tag.as_deref() != Some("html") && parent_tag.as_deref() != Some("head") {
                            parent.borrow_mut().append(dom::text(&text));
                        }
                    }
                }
                Token::Comment(_) | Token::DocType => { /* ignore */ }
            }
        }
    }

    fn pop_until(&mut self, stack: &mut Vec<NodeRef>, tag: &str) {
        // Pop from stack until we find the matching open tag
        for i in (1..stack.len()).rev() {
            if stack[i].borrow().tag_name() == Some(tag) {
                stack.truncate(i + 1);
                return;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_basic_html() {
        let html = "<html><body><h1>Hello</h1></body></html>";
        let root = Parser::parse(html);
        let doc = root.borrow();
        assert_eq!(doc.children.len(), 1);
        let html_elem = doc.children[0].borrow();
        assert_eq!(html_elem.tag_name(), Some("html"));
    }

    #[test]
    fn test_attrs() {
        let html = r#"<div class="foo" id="bar">text</div>"#;
        let root = Parser::parse(html);
        let div = root.borrow().children[0].clone();
        let div_b = div.borrow();
        let data = div_b.element_data().unwrap();
        assert_eq!(data.get_attr("class"), Some("foo"));
        assert_eq!(data.get_attr("id"), Some("bar"));
    }

    #[test]
    fn test_self_closing() {
        let html = "<br><img src='x.png'>";
        let root = Parser::parse(html);
        // Should not crash, should produce valid tree
        let doc = root.borrow();
        assert_eq!(doc.children.len(), 2);
    }
}
