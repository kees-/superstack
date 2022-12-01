# Superstack
Interlocks with [kees-/substack-to-json](https://github.com/kees-/substack-to-json) to build a static mirror of any substack blog.

This is intended to create a local copy of all posts on a substack for personal browsing. It outputs in a minimal, attractive, easily navigable reader mode with a dark/light theme toggle.
I take no responsibility for end use.

## Running

### From source

```sh
clj -X:main

# Optional positional arguments with defaults:
#
# :src-data "posts.json"
# :blog-name "substack
# :output-dir "target"
```

Point `:src-data` to a JSON file outputted by the repo linked above.

And this is the clojure cli... escape weird string arguments with `'" "'`.

## Output

The tool is very rudimentary. The result will look like this:

```
:output-dir ("target")
└── :blog-name ("substack")
    ├── index.html
    ├── index.css
    ├── posts.css
    ├── sun.svg
    ├── moon.svg
    └── posts
        ├── 0001-post-title-a.html
        ├── 0002-post-title-b.html
        └── ...
```

---

I'll rewrite this as a babashka script.
