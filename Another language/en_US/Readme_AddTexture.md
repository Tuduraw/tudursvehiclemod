# Readme_AddTexture.md - Adding dummy pilot skins

A guide to building an addon pack that adds new looks (skins) for the
dummy pilot used by a Drone Center. No code changes or rebuild are needed
at all. This is a separate, independent system from the vehicle/weapon
addon system (see `Readme_Addon.md`).

---

## 1. Where to place it

Same as "1. Where to place it" in `Readme_Addon.md`. The simplest method
is to place it, as a plain (uncompressed) folder structure, at:

```
<game directory>/tudursvehiclemod-addons/<any addon name>/
```

(Method A - recommended). Placing it as an ordinary resource pack (Method
B) is also supported.

---

## 2. Directory structure

```
tudursvehiclemod-addons/
└── <addon name>/
    └── textures/
        └── dummy_pilot/
            └── <skin id>.png      … the dummy pilot skin image
```

Note that, unlike a vehicle addon's `assets/<namespace>/` structure, this
`textures/dummy_pilot/` path does **not** go through a namespace at all -
it sits directly under the addon folder itself.

---

## 3. Skin image specification

- **Format**: an ordinary 64×64 pixel PNG, the same as a regular player
  skin (supports the standard Minecraft 1.8+ skin layout, including the
  hat/jacket/sleeve/pants overlay layers, which render correctly)
- The **file name** itself (minus the `.png` extension) becomes the
  **skin id** you select in-game
- Only lowercase ASCII letters, digits, underscore (`_`), hyphen (`-`),
  and period (`.`) are allowed in a skin id. A file name containing any
  other character (uppercase letters, non-ASCII characters, spaces, etc.)
  is ignored
- If you give your skin the same name as one of the ids the mod already
  ships (`default`, `driver`, `soldier`, `ww2_pilot`), **the built-in one
  takes priority and your addon's own file is ignored**. Use a name that
  doesn't collide with anything else for your own custom skins

---

## 4. Selecting it in-game

1. Right-click a Drone Center block to open its configuration screen
2. Use the "<" / ">" buttons in the skin selection field to cycle through
   every available skin (the 4 built-in ones plus every skin from every
   loaded addon)
3. The selected skin id is saved as part of that Drone Center's own data,
   and applies to whichever dummy pilot that Drone Center manages

---

## 5. Reloading

- Running `/reload` in-game rescans any added/changed skin files. No game
  or world restart is needed
- If a previously-selected skin id becomes unavailable (its addon was
  removed, for example), it automatically falls back to the built-in
  `default` skin (no data corruption or load failure occurs)

---

## 6. Note: how this differs from the built-in skins

Of the mod's own 4 built-in skins, `default` is special - it references
Minecraft's own actual, built-in Steve texture directly, and doesn't
exist as an image file in either this mod's own resources or any addon.
A skin you add as an addon should be provided as an ordinary PNG file
(this special reference-only mechanism, like `default` uses, isn't
supported for addon skins).
