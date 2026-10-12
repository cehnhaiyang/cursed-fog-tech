# GUI art provenance

`heating_structure.png` and `generic.png` in this directory are copied verbatim from
**Tinkers' Construct 3** (`TinkersConstruct-3.12.1.231`), which is licensed under the **MIT License**.

- Upstream project: https://github.com/SlimeKnights/TinkersConstruct
- Files: `src/main/resources/assets/tconstruct/textures/gui/heating_structure.png`,
  `src/main/resources/assets/tconstruct/textures/gui/generic.png`
- SHA-256: `heating_structure.png` = `2178f4888562132940f4cd276066a9789780724a5ee03e21a21ae229b3df3540`
- SHA-256: `generic.png` = `3f0f7b1e4df1111d40589d29258a740973bc8351a79a3ba2e62a937e1f41c3aa`

They are vendored here because the SoulForge controller screen deliberately reproduces the
Tinkers' Construct smeltery layout exactly (panel 176×220, melt tank at 8,16 106×106, fuel tank at
152,32 16×90, flame at 153,15, bucket slots at 125,46 / 125,104, transfer button at 124,69,
inventory at y=138/196). The texture's sub-element atlas coordinates are part of that contract, so
`tools/gen_assets.mjs` ships these files unchanged rather than approximating them.

If the textures are ever redrawn for SoulForge, remove this directory and the `copyGuiRef` calls,
and update `ControllerLayout` in the same change — the Java constants and the texture coordinates
must stay in sync.
