(ns ^:no-doc rewrite-cljc.node.reader-macro
  (:require [rewrite-cljc.node.protocols :as node]
            [rewrite-cljc.node.whitespace :as ws]))

;; ## Node

(defrecord ReaderNode [tag prefix suffix
                       sexpr-fn sexpr-count
                       children]
  node/Node
  (tag [_] tag)
  (printable-only? [_]
    (not sexpr-fn))
  (sexpr [_]
    (if sexpr-fn
      (sexpr-fn (node/sexprs children))
      (throw (ex-info "unsupported operation" {}))))
  (length [_]
    (-> (node/sum-lengths children)
        (+ 1 (count prefix) (count suffix))))
  (string [_]
    (str "#" prefix (node/concat-strings children) suffix))

  node/InnerNode
  (inner? [_]
    true)
  (children [_]
    children)
  (replace-children [this children']
    (when sexpr-count
      (node/assert-sexpr-count children' sexpr-count))
    (assoc this :children children'))
  (leader-length [_]
    (inc (count prefix)))
  Object
  (toString [this]
    (node/string this)))

(defrecord ReaderMacroNode [children]
  node/Node
  (tag [_] :reader-macro)
  (printable-only?[_] false)
  (sexpr [this]
    (list 'read-string (node/string this)))
  (length [_]
    (inc (node/sum-lengths children)))
  (string [_]
    (str "#" (node/concat-strings children)))

  node/InnerNode
  (inner? [_]
    true)
  (children [_]
    children)
  (replace-children [this children']
    (node/assert-sexpr-count children' 2)
    (assoc this :children children'))
  (leader-length [_]
    1)
  Object
  (toString [this]
    (node/string this)))

(defrecord DerefNode [children]
  node/Node
  (tag [_] :deref)
  (printable-only?[_] false)
  (sexpr [this]
    (list* 'deref (node/sexprs children)))
  (length [_]
    (inc (node/sum-lengths children)))
  (string [_]
    (str "@" (node/concat-strings children)))

  node/InnerNode
  (inner? [_]
    true)
  (children [_]
    children)
  (replace-children [this children']
    (node/assert-sexpr-count children' 1)
    (assoc this :children children'))
  (leader-length [_]
    1)
  Object
  (toString [this]
    (node/string this)))

(node/make-printable! ReaderNode)
(node/make-printable! ReaderMacroNode)
(node/make-printable! DerefNode)

;; ## Constructors

(defn- ->node
  [tag prefix suffix sexpr-fn sexpr-count children]
  (when sexpr-count
    (node/assert-sexpr-count children sexpr-count))
  (->ReaderNode
    tag prefix suffix
    sexpr-fn sexpr-count
    children))

(defn var-node
  "Create node representing a var with `children`.
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->node :var "'" "" #(list* 'var %) 1 children)
    (recur [children])))

(defn eval-node
  "Create node representing an inline evaluation with `children`. (`#=...`)
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->node
      :eval "=" ""
      #(list 'eval (list* 'quote %))
      1 children)
    (recur [children])))

(defn reader-macro-node
  "Create node representing a reader macro with `children`. (`#... ...`)"
  ([children]
   (->ReaderMacroNode children))
  ([macro-node form-node]
   (->ReaderMacroNode [macro-node (ws/spaces 1) form-node])))

(defn deref-node
  "Create node representing the dereferencing of a form with `children`. (`@...`)
   Takes either a seq of nodes or a single one."
  [children]
  (if (sequential? children)
    (->DerefNode children)
    (->DerefNode [children])))
