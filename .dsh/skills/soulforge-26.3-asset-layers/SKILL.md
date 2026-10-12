---
name: soulforge-26.3-asset-layers
description: How Minecraft 26.3 resolves item, block and fluid models for this project (锻魂 / SoulForge, mod_id=soulforge), and why a missing resource layer renders as a purple-black error block. Applies to this project only.
whenToUse: Read when any item, block or fluid renders as a purple-black error block, or when the log shows "Missing item model", "Missing FluidModel" or "Missing model for variant" for soulforge.
---

# SoulForge 26.3 asset layers

26.3 splits item rendering into **two** layers. Supplying only the old one produces a purple-black
error block even though the texture PNG is present and correct.

| Layer | Path | Purpose |
|---|---|---|
| Item definition (**new in 26.3**) | `assets/soulforge/items/<item_id>.json` | Binds an item id to a model |
| Item model (old layer) | `assets/soulforge/models/item/<name>.json` | Texture layers |
| Block state | `assets/soulforge/blockstates/<block_id>.json` | Variant → model |
| Block model | `assets/soulforge/models/block/<name>.json` | Cube/texture |

## The item definition layer is mandatory

`ClientItemInfoLoader` reads `FileToIdConverter.json("items")`, i.e. `assets/<ns>/items/<id>.json`.
With that directory absent, **every** item falls back to `missingModels.item()` and
`ModelManager.getItemModel` logs:

```
Missing item model for location soulforge:glove
```

This repo shipped 45 `models/item/*.json` and 45 texture PNGs but no `items/` directory at all,
so all 48 registered items rendered as error blocks. Textures never entered the atlas either,
because an un-baked model does not stitch its textures.

Format — the `model` value must be namespaced and points at the *other* layer:

```json
{ "model": { "type": "minecraft:model", "model": "soulforge:item/glove" } }
```

For a **block item** the target is the block model, not an item model:

```json
{ "model": { "type": "minecraft:model", "model": "soulforge:block/netherite_fuel_tank" } }
```

Note that the block item's registry name is `netherite_fuel_tank_block` while the block it points at
is `netherite_fuel_tank` — the file is named after the **item id**, the `model` value after the
**block**. There are 50 item definitions: 15 plain items, 17 block items, 18 buckets.

Adding an item means touching four places, all of which must agree: `SoulForgeItems` (the
`DeferredItem`), `SoulForgeCreativeTabs` (or it never appears in the tab), `items/<id>.json` +
`models/item/<id>.json` (or it renders as an error block), and both `lang/en_us.json` and
`lang/zh_cn.json`. For a **block** item the `items/` file targets `soulforge:block/<blockid>` and no
`models/item/` file is needed.

The two casting molds (`ingot_cast`, `gem_cast`) are the worked example of a plain item added
end-to-end: `SoulForgeItems` registers them with `stacksTo(1)`, the tab lists them, the generator
draws them from `CAST_INGOT_MAP` / `CAST_GEM_MAP`, and the casting recipes reference them through the
`cast` field.

## Model parents may omit the namespace

`"parent": "item/generated"` and `"parent": "block/cube_all"` still resolve; a missing namespace
defaults to `minecraft`. Do not "fix" these to `minecraft:item/generated`. All parents in this repo
were verified to resolve against the 4192 vanilla models.

## Blockstates must cover every property combination

A blockstate with no matching variant logs
`Missing model for variant: 'Block{soulforge:<id>}[...]'` and renders as an error block.

`SmelteryControllerBlock` declares three properties — `FACING`, `IN_STRUCTURE`, `ACTIVE` — so each of
the five controller blockstates needs 4 × 2 × 2 = **16** variants. This repo originally had only the
four `facing=*` keys, leaving 12 combinations per block uncovered. Variant keys are comma-joined and
alphabetically ordered:

```
"facing=north,in_structure=false,active=false"
```

Two combinations sharing one model make that property invisible in game. `active=true` now points at
`<name>_active` (a brighter front texture) so a formed, running smeltery looks different from an idle
one.

## Fluid models are registered in Java, and do not replace blockstates

Two independent mechanisms, both required:

| Mechanism | Serves | Where |
|---|---|---|
| `FluidModel` registration | the fluid itself (source, flowing) | Java, `RegisterFluidModelsEvent` |
| `blockstates/` + `models/block/` | the fluid's `LiquidBlock` | 18 × 2 JSON files |

`FluidType.initializeClient` is **deleted** in 26.3, so a fluid with no registration logs:

```
Missing FluidModel for fluid 'soulforge:molten_iron'
```

Register every fluid in `SoulForgeFluids.ALL` from `SoulForgeClient`:

```java
@SubscribeEvent
public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
    for (SoulForgeFluids.FluidSet fluid : SoulForgeFluids.ALL) {
        String path = "block/fluid/" + fluid.name();
        FluidModel.Unbaked model = new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, path + "_still")),
                new Material(Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, path + "_flowing")),
                null,
                FluidTintSources.constant(0xFFFFFFFF));
        event.register(model, fluid.still(), fluid.flowing());
    }
}
```

`FluidModel.Unbaked(Material, Material, Material, FluidTintSource)` takes `Material`, constructed as
`new Material(Identifier.fromNamespaceAndPath("soulforge", "block/fluid/<name>_still"))`.

**The tint is a multiply, so `0xFFFFFFFF` means "no tint".** `FluidRenderer.tesselate` passes the
tint straight into `ARGB.scaleRGB(tintColor, lighting)` as the vertex colour. Each fluid now has its
own still/flowing sprite with the colour already painted in, so tinting again would double-apply it.
Only use a non-white tint if the sprite is greyscale.

Fluid sprites are animated: `textures/block/fluid/<name>_{still,flowing}.png` are vertical frame
strips (16x128 = 8 frames of 16x16; 32x256 = 8 frames of 32x32) with a sibling
`<name>_{still,flowing}.png.mcmeta` holding `{"animation":{"frametime":3}}`. A strip without its
`.mcmeta` renders as one squashed frame.

**Do not delete `blockstates/<fluid>.json` or `models/block/<fluid>.json`.** They look unreferenced
from Java because `FluidModel` replaced the client hook, but they drive the `LiquidBlock`. Removing
all 36 of them produced 288 warnings — 18 fluids × 16 `level=N` variants:

```
Missing model for variant: 'Block{soulforge:molten_iron}[level=0]'
```

## Block-item models live in `items/`, not `models/item/`

`items/<id>.json` pointing at `soulforge:block/<name>` is the **whole** definition for a block item.
Vanilla 26.3 has 832 item definitions whose model targets `minecraft:block/...` and **not one** of them
has a matching `models/item/<id>.json`. Adding one creates a file nothing references; in this repo 14
such files existed and were removed.

Only plain items (`glove`, `magnetic_coil`, buckets, …) need `models/item/<id>.json`.

## `render_type` is obsolete

No block model in vanilla 26.3 contains `render_type` (measured: 0 of the `assets/minecraft/models/block`
entries). It was a Forge/NeoForge 1.20-era key. Transparency now comes from the sprite itself, so a
fluid block model only needs the particle texture:

```json
{ "parent": "block/water", "textures": { "particle": "soulforge:block/fluid/molten_iron_still" } }
```

## Blockstate variants need one entry per property combination

`SmelteryControllerBlock` has `FACING` × `IN_STRUCTURE` × `ACTIVE` = 4 × 2 × 2 = **16** variants.
Keys are comma-joined and alphabetically ordered: `facing=north,in_structure=false,active=false`.
A missing combination logs `Missing model for variant`.

If two combinations share one model, the property is invisible in game. `active=true` now points at
`<name>_active` (a brighter front texture) so a formed, running smeltery looks different from an
idle one.

## GUI texture is 256×256, and the panel is 176×220

`ControllerScreen` is 176×220 (`super(menu, inventory, title, 176, 220)`), the TiC3
`HeatingStructureScreen` size, not the vanilla 176×166 — the taller panel makes room for the 106×106
tank and the two bucket slots.

`graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight,
256, 256)` samples only the top-left 176×220 of the sheet, so **the PNG must be 256×256 even though
the panel is smaller**; the remaining area is spare sprite space. The current screen draws progress
and tank levels with `graphics.fill` rather than a second blit, so nothing else samples the sheet yet
— but the size is still required, and the spare region is where a fill sprite would go.

`tools/gen_assets.mjs`'s `buildGui()` draws every element at the same coordinates the Java uses.
Keep the three in step; a mismatch is invisible until a slot or gauge lands on the wrong pixel:

| Element | Java constant | Texture coordinate |
|---|---|---|
| Melt tank | `TANK_X/Y/W/H = 8,16,106,106` | `recessed(8, 16, 106, 106)` |
| Fuel tank | `FUEL_X/Y/W/H = 152,32,16,90` | `recessed(152, 32, 16, 90)` |
| Melting inputs | `44 + i*18, 35` (i = 0..3) | `recessed(44 + i*18, 35, 18, 18)` |
| Bucket in / out | `125,46` / `125,104` | `recessed(125, 46, 18, 18)` / `(125, 104, …)` |
| Direction button | `BUTTON_X/Y = 124,69`, size 18 | `c.fill(124, 69, 18, 18, …)` |
| Player rows / hotbar | `138 + row*18` / `196` | matching `recessed` calls |

Verify by decoding the PNG and asserting each slot's **centre** (`slot + 9, slot + 9`) is the
recessed colour `55,55,55` — sampling `slot + 1, slot + 1` hits the bevel edge instead
(`shade('#373737', -0.3)` = `39,39,39`) and reports a false mismatch.

## Regenerating the assets

`tools/gen_assets.mjs` is the single source for every PNG, model, blockstate and `.mcmeta` in
`assets/soulforge`. Run `node tools/gen_assets.mjs`. It reads fluid names and tint colours straight out
of `SoulForgeFluids.java`, so the generator and the registry cannot drift.

It is deterministic (re-running produces byte-identical files) and preserves each directory's EOL
convention: `models/` and `blockstates/` are CRLF, `items/` is LF. Writing the wrong EOL rewrites every
line in git.

## Recipe JSON ingredient syntax changed

`Ingredient.CODEC` accepts only a **tag string** or an **array of item ids**. The pre-26.3 object
forms are rejected, producing a long `Failed to parse either ... No key neoforge:ingredient_type`
error and dropping the recipe:

| Wrong | Right |
|---|---|
| `{ "item": "minecraft:leather" }` | `"minecraft:leather"` |
| `{ "tag": "minecraft:planks" }` | `"#minecraft:planks"` |

This applies inside a `crafting_shaped` `key` object, a `crafting_shapeless` `ingredients` array, and
this mod's own `soulforge:melting` `input` / `soulforge:casting` `cast` fields. Note that a
single-line `"key": { "L": { "item": "x" } }` needs its own regex — a line-oriented replacement that
expects a closing brace on its own line will silently skip it.

## Recipe outputs must use the *Template types

`ItemStack.CODEC` and `FluidStack.CODEC` go through `*_WITH_BOUND_COMPONENTS`, which rejects any entry
whose data components are not yet bound. Recipes are parsed during registry loading, **before** any
item or fluid has components bound, so even vanilla entries fail:

```
Item minecraft:copper_ingot does not have components yet; Fluid soulforge:molten_copper does not have components yet
```

Use `ItemStackTemplate` / `FluidStackTemplate` in the recipe codec and stream codec, and materialise
with `.create()` when the recipe is used. `FluidStackTemplate` has no `matches`; compare with
`FluidStack.isSameFluid(stack, template)`.

## "can't be placed" warnings are harmless but avoidable

`RecipeManager.finalizeRecipeLoading` warns once per recipe whose `placementInfo()` is impossible:

```
Recipe soulforge:casting/iron_ingot can't be placed due to empty ingredients and will be ignored
```

"ignored" only skips recipe-book property collection — the recipe stays in `this.recipes` and
`getRecipesFor` still returns it. This mod's casting and alloying recipes take fluid input, so they
genuinely have no item ingredients; override `isSpecial()` to return `true` to silence the warning.
