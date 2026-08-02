use std::cell::RefCell;
use std::collections::HashMap;
use std::fmt;
use std::hash::{Hash, Hasher};
use std::rc::Rc;

/// A reference-counted, mutable node handle.
pub type NodeRef = Rc<RefCell<Node>>;

/// Kinds of nodes in the DOM tree.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NodeKind {
    Document,
    Element(ElementData),
    Text(String),
}

/// Data for an element node.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ElementData {
    pub tag: String,
    pub attrs: HashMap<String, String>,
}

impl ElementData {
    pub fn id(&self) -> Option<&str> {
        self.attrs.get("id").map(|s| s.as_str())
    }

    pub fn classes(&self) -> Vec<&str> {
        self.attrs
            .get("class")
            .map(|s| s.split_whitespace().collect())
            .unwrap_or_default()
    }

    pub fn get_attr(&self, name: &str) -> Option<&str> {
        self.attrs.get(name).map(|s| s.as_str())
    }
}

/// A DOM node.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Node {
    pub kind: NodeKind,
    pub children: Vec<NodeRef>,
}

impl Node {
    pub fn new(kind: NodeKind) -> Self {
        Node {
            kind,
            children: Vec::new(),
        }
    }

    pub fn new_rc(kind: NodeKind) -> NodeRef {
        Rc::new(RefCell::new(Self::new(kind)))
    }

    pub fn append(&mut self, child: NodeRef) {
        self.children.push(child);
    }

    #[allow(dead_code)]
    pub fn is_element(&self) -> bool {
        matches!(self.kind, NodeKind::Element(_))
    }

    #[allow(dead_code)]
    pub fn is_text(&self) -> bool {
        matches!(self.kind, NodeKind::Text(_))
    }

    pub fn tag_name(&self) -> Option<&str> {
        match &self.kind {
            NodeKind::Element(e) => Some(&e.tag),
            _ => None,
        }
    }

    pub fn element_data(&self) -> Option<&ElementData> {
        match &self.kind {
            NodeKind::Element(e) => Some(e),
            _ => None,
        }
    }

    /// Pretty-print the DOM tree for debugging.
    pub fn dump(&self, f: &mut fmt::Formatter<'_>, depth: usize) -> fmt::Result {
        let indent = "  ".repeat(depth);
        match &self.kind {
            NodeKind::Document => {
                writeln!(f, "{}Document", indent)?;
            }
            NodeKind::Element(e) => {
                write!(f, "{}<{}", indent, e.tag)?;
                for (k, v) in &e.attrs {
                    write!(f, " {}=\"{}\"", k, v)?;
                }
                writeln!(f, ">")?;
            }
            NodeKind::Text(t) => {
                let trimmed = t.trim();
                if !trimmed.is_empty() {
                    writeln!(f, "{}\"{}\"", indent, trimmed)?;
                }
                // skip empty text nodes entirely
                return Ok(());
            }
        }
        for child in &self.children {
            child.borrow().dump(f, depth + 1)?;
        }
        Ok(())
    }
}

impl fmt::Display for Node {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.dump(f, 0)
    }
}

// Manual Hash impl: HashMap doesn't impl Hash, so skip attrs.
// Node's children are Rc<RefCell<Node>> which would require recursive Hash.
impl Hash for NodeKind {
    fn hash<H: Hasher>(&self, state: &mut H) {
        match self {
            NodeKind::Document => 0u8.hash(state),
            NodeKind::Element(e) => {
                1u8.hash(state);
                e.tag.hash(state);
                // skip attrs: HashMap doesn't implement Hash
            }
            NodeKind::Text(t) => {
                2u8.hash(state);
                t.hash(state);
            }
        }
    }
}

impl Hash for Node {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.kind.hash(state);
        // skip children to avoid recursive hash through Rc<RefCell<Node>>
    }
}

/// Convenience: create an element node.
pub fn elem(tag: &str, attrs: HashMap<String, String>, children: Vec<NodeRef>) -> NodeRef {
    let node = Node::new(NodeKind::Element(ElementData {
        tag: tag.to_string(),
        attrs,
    }));
    let rc = Rc::new(RefCell::new(node));
    for child in children {
        rc.borrow_mut().append(child);
    }
    rc
}

/// Convenience: create a text node.
pub fn text(content: &str) -> NodeRef {
    Node::new_rc(NodeKind::Text(content.to_string()))
}
