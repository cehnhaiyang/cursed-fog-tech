---
name: soulforge-smeltery-multiblock
description: How the 锻魂/SoulForge smeltery multiblock, its fluid tank, fuel model, melting and alloying modules, and its controller GUI are wired together (D:\MDK-26.3-ModDevGradle-main, mod_id=soulforge). Applies to this project only.
whenToUse: Read before changing anything under src/main/java/com/soulforge/soulforge/smeltery/, SmelteryControllerBlock(Entity), ControllerMenu/Screen/Layout, the fuel registry, or the alloying/fusion/melting recipes — and before claiming multiblock detection or the GUI works.
---

# SoulForge smeltery: structure, tank, fuel, melting, alloying, GUI

Design reference is the local, gitignored tree `TinkersConstruct-3.12.1.231/` (TiC3 3.12.1.231, MIT).
Read it for **mechanism**, not for code to copy: it targets 1.21.1/Forge and none of its imports
resolve here. The GUI art is the one exception — it is vendored verbatim (see the GUI section).

## The controller is a master; there are no servant block entities

`SmelteryControllerBlockEntity` re-validates the structure by **full rescan every 20 ticks**
(`STRUCTURE_RECHECK_INTERVAL`), plus immediately when `updateStructure()` is called from
`neighborChanged`.

This is deliberate. TiC3 gives every wall/tank block a `ServantBlockEntity` that calls back to the
master, so a wall broken far from the controller is noticed instantly. Reproducing that here means
attaching a block entity to `smeltery_bricks`, `netherite_bricks`, `trinity_alloy_bricks`,
`singularity_bricks` and all three fuel tanks — hundreds of block entities per furnace. The periodic
rescan costs O(shell blocks) once a second and **cannot miss a change**, which is what actually
matters: the failure it prevents is "player breaks a wall and the furnace keeps smelting forever".

`IMasterLogic` / `IServantLogic` / `ServantBlockEntity` were written and then deleted for this reason.
Do not reintroduce them without also accepting the per-block-entity cost.

## MultiblockCuboid: layer scan, and the controller is unconditionally valid

`MultiblockCuboid.detectMultiblock(level, master, facing, validator)` is a port of TiC3's
`MultiblockCuboid`:

1. `center = master.relative(facing.getOpposite())` — **`FACING` points outward, the interior is
   behind it.** Getting this backwards makes the scan walk away from the furnace and, in open air,
   run to the limit and report `multiblock.soulforge.too_large`.
2. Walk N/S/E/W to the first non-air block to find the four walls.
3. `detectLayer` on the centre layer, then downward until a layer fails, then `detectCap` for the
   floor; then upward, then the ceiling.
4. `hasFloor=true, hasCeiling=false` for the smeltery, so the top is open and it can be built upward.

`DEFAULT_INNER_LIMIT = 11` (max interior edge) and `DEFAULT_MAX_HEIGHT = 32`.

**`SmelteryMultiblock.isValid` must return `true` for the controller's own position, first line.**
The controller is embedded in the wall, and it is neither a brick nor a tank, so without that check
*every* structure fails validation with `invalid_wall_block` and the furnace can never form. This was
the single cause of "multiblock detection does not work at all".

Tier rules: a wall block is legal if it is the controller tier's representative block **or any higher
tier's** (so a furnace can be upgraded in place without tearing it down), or a fuel tank.

## `BlockValidator` must be implemented, never passed as a method reference

```java
public interface BlockValidator {
    boolean isValid(Level world, BlockPos pos, CuboidSide side);
    default boolean isTank(BlockState state) { return false; }
}
```

`SmelteryMultiblock implements MultiblockCuboid.BlockValidator` and passes `this`. Passing
`this::isValidBlock` instead **silently drops `isTank`** — a method reference only satisfies the
single abstract method, so the default `return false` is what runs. The symptom is precise and
misleading: fuel tanks are accepted as legal wall blocks (so the structure forms) but are never
recorded, `MultiblockStructure.getTanks()` stays empty, and the furnace reports "no fuel" no matter
how many filled tanks you build into it. Verified by probe: `tanks=0` before, `tanks=1` after.

When you add a method to a validator-style interface, check every call site for a method reference.

## SmelteryTank: one shared capacity, one slot per fluid

`smeltery/tank/SmelteryTank` is why alloying is possible at all. A single-slot
`FluidStacksResourceHandler` can hold only one fluid, and trinity alloy needs molten netherite +
diamond + quartz present simultaneously.

- `List<FluidStack> fluids` in pour order; index 0 is what a drain pulls first.
- `capacity` is shared; `size()` returns `fluids.size() + 1` while there is remaining space, so a
  caller sees the empty slot for a new fluid. Do not assume a fixed slot count.
- `canFit(fluid, removed)` exists so an alloy recipe can confirm the output fits **before** draining
  its inputs; without it a recipe can consume inputs and then fail to store the product.
- It extends `SnapshotJournal<List<FluidStack>>` and implements `ResourceHandler<FluidResource>`.
- `getCapacityAsLong(index, resource)` returns `own + remainingSpace`, matching TiC3.
- `moveFluidToBottom(index)` is what the GUI's empty-handed tank click drives; it changes which fluid
  a drain pulls first.

## The controller holds NO fuel; fuel comes from tank blocks in the wall

`SoulForgeCapabilities` registers `Capabilities.Fluid.BLOCK` for the controller as `be.getTank()` —
the **melt** tank. Registering a fuel tank there was the original bug: molten metal then had no
extraction path at all and the casting chain was dead.

Fuel lives in `NETHERITE_FUEL_TANK` / `TRINITY_ALLOY_FUEL_TANK` / `MAGVAC_FUEL_TANK` blocks placed in
the furnace wall. `FuelModule.findFuel(level, tanks, consume)` iterates
`MultiblockStructure.getTanks()` and reads each with
`level.getCapability(Capabilities.Fluid.BLOCK, pos, null)`.

## FuelModule: four numbers per fuel, not just a temperature

`smeltery/fuel/FuelRegistry` maps each combustible fluid to `FuelInfo(amount, duration, temperature,
rate)` — the same four fields as TiC3's `melting_fuel` recipe:

| Field | Meaning |
|---|---|
| `amount` | mB drawn per burn unit; a full unit is drawn at a time, so removing the tank does not instantly extinguish the furnace |
| `duration` | burn ticks per unit |
| `temperature` | furnace temperature while burning |
| `rate` | melting progress per tick |

**A temperature-only model silently destroys fuel balance.** If every fuel is one unit for one
duration, a hotter fuel is strictly better and the cheaper ones are pure loss. Registering only a
temperature and hardcoding the draw amount is the mistake to look for.

Values are scaled from TiC3's lava (1000 °C / 100 ticks / rate 10) and blazing blood (1500 / 150 / 15):
blazing blood 1500/150/15, activated 3500/200/25, ionized 5000/250/35, plasma 10000/300/50, eternal
100000000/500/100 — all 50 mB per unit.

A fuel whose temperature exceeds the controller tier's `canHandleTemperature` is **skipped**, not
consumed — that is what enforces "a weak furnace cannot burn a hotter fuel".

`FuelModule` takes only the tier in its constructor and receives `Level` as a **method parameter**.
A block entity's `level` field is not assigned yet when its constructor runs, so capturing it there
gives a permanently-null world. It also remembers the last tank it drew from (`lastPos`) so it does
not rescan the whole wall every tick.

`getDisplay(level, tanks)` plus `fill`/`drain` exist for the GUI and bucket interaction.

## MeltingSlot: items heat up, and cool down when fuel is gone

`smeltery/module/MeltingSlot` mirrors TiC3's `MeltingModule`, one instance per melting slot.

- **Recipe time is multiplied by `TIME_SCALE = 10`** in `setStack`. The JSON `time` is the tick count
  at the baseline rate; the multiply is what lets a better fuel melt the same recipe faster. Skipping
  it makes every progress bar fill instantly.
- Recipe time/temperature are resolved in `setStack`, not on the heating path, so the GUI can show
  "this needs 1539 °C" the moment an item lands and heating does no recipe lookup per tick.
- `heatItem(temperature, rate)` advances by `rate`; `coolItem()` subtracts 5, much faster than
  heating, so progress does not freeze when fuel runs out.
- `NO_SPACE = -1` means "hot enough but the tank is full". It is **not** progress 0: `0` is cold, `-1`
  is hot and waiting. Conflating them either deletes the item or wedges it forever. `coolItem()`
  deliberately does **not** cool a `NO_SPACE` slot.
- Output goes through a constructor-injected `Predicate<FluidStack>` that returns whether the tank
  accepted it. That callback is the **single owner** of the "did it fit" decision, so `finish()` can
  never disagree with the controller's capacity check.
- `MeltingSlot implements ContainerData` (3 ints: current time, required time, required temp). The
  menu registers it with `addDataSlots`, which is how the heat bars get their data — no extra channel.

`MeltingInventory` adapts the slots to `ResourceHandler<ItemResource>` **and**
`IndexModifier<ItemResource>` (the menu needs both roles) and is **resizable**: its size equals the
interior volume, so a 3×2×3 furnace has 18 slots. Slots are created lazily. Shrinking hands the
evicted stacks to a consumer that drops them in front of the controller — resizing must never delete
a player's materials. Each slot holds 1 item because melting is timed per item.

## AlloyingRecipe.matches() returns false — use byType, not getRecipesFor

`AlloyingRecipe` / `FusionRecipe` take fluid ingredients, so `matches(RecipeInput, Level)` is
hard-coded `return false`. Therefore:

```java
serverLevel.recipeAccess().recipeMap().byType(type)          // correct
serverLevel.recipeAccess().recipeMap().getRecipesFor(...)    // returns nothing, ever
```

`AlloyingModule` computes a `Plan` (which fluids to drain + what to insert) **once**, and both "can I
alloy?" and "do the alloy" read that same plan. Checking and performing separately lets the two drift,
which drains the inputs and produces nothing. Within a plan every input ingredient must claim a
**different** tank slot (`boolean[] used`), the recipe temperature must be met, and
`mixture.getAmount() - drainTotal <= tank.getRemainingSpace()` — the net gain, because an output can
be the same fluid as an input.

The candidate list is shuffled before matching: two mutually exclusive recipes (iron+copper vs
iron+tin) would otherwise always resolve to whichever is registered first.

Recipe JSON fields: `inputs` (fluid ingredient list), `amounts` (mB per input, defaulting to an even
split of the output), `output` (`FluidStackTemplate`), `temperature`, `customName`, `color`.
Alloy composition is computed from **actual drained mB**, and an input that already carries
`ALLOY_DATA` is merged weighted by its amount, so multi-stage alloys propagate composition.

## Controller tick: four phases, one job each

`SmelteryControllerBlockEntity.heat()` switches on `tick % 4`, matching TiC3's `SmelteryBlockEntity`:

| Phase | Action |
|---|---|
| 0 | find fuel, but only **burn** a unit if something can actually melt or alloy |
| 1 | heat items, or cool them if there is no fuel |
| 2 | run one alloy |
| 3 | consume fuel and mirror "is burning" into the `ACTIVE` block state |

Doing all of it every tick is not faster (alloying rescans the recipe table) and makes the ratio of
fuel burned to progress gained depend on implementation details.

Capacity is `interiorVolume * CAPACITY_PER_BLOCK` (1728 mB per block) and the melting inventory is
resized to `interiorVolume` in the same place. When the structure breaks, both fall back to the
minimum rather than keeping the old size — otherwise the GUI shows a tank that no longer exists.

## Controller GUI gate: no interface until the structure is formed

`SmelteryControllerBlock.useWithoutItem` opens the menu only when
`state.getValue(IN_STRUCTURE)` is true; otherwise it calls
`player.sendSystemMessage(result.getMessage(), true)` with the last `MultiblockResult`. Opening the
GUI for an unformed furnace is meaningless — the tank and slots do not exist yet.

`MultiblockCuboid.lastResult` is initialised to a **failure** (`multiblock.soulforge.not_formed`),
not `success()`, so a controller that has never been scanned still reports something truthful.

## The controller GUI is two panels, and the layout lives in one class

**Melting slots are NOT inside the 176×220 panel.** They are in a *side panel* attached to its left
edge, exactly as TiC3 does with `MultiModuleScreen`. Getting this wrong is the easiest way to produce
a GUI that looks plausible but is not the original.

`menu/ControllerLayout` is the **sole owner** of every coordinate; both the menu (slot placement) and
the screen (texture sampling) read it. Two copies of these numbers is how items end up drawn outside
their recess.

Main panel, 176×220:

| Element | x, y, w, h |
|---|---|
| Melt tank | 8, 16, 106, 106 |
| Fuel tank | 152, 32, 16, 90 |
| Fire icon | 153, 15, 14, 14 |
| Transfer-direction button | 124, 69, 18, 18 |
| Bucket input / result | 125, 46 / 125, 104 |
| Player inventory rows | y = 138 + row*18, hotbar y = 196 |

Side panel: slot element 22×18 (4 px heat-bar recess + 16×16 item cell), border 7 px, max 10 visible
rows, at most 4 columns at 7 slots per column. It overlaps the main panel by `BORDER - 1` px so the
two read as one piece.

Texture sub-element coordinates, which the generator must match (all in
`textures/gui/smeltery_controller.png`, a 256×256 sheet):

| Element | u, v |
|---|---|
| Ruler overlay | 176, 0 (80×106) |
| Flame | 176, 136 (14×14) |
| Heat bars (progress / no heat / no space / unmeltable) | 176, 150; +3 each (3×16) |
| Transfer icons (auto / empty / fill / bucket placeholder) | 176, 186; +16 each (16×16) |
| Button hover | 176, 202 (18×18) |
| Side-panel slot / empty slot | 0, 238 / 22, 238 (22×18) |
| Side-panel border | `textures/gui/generic.png`, 9-slice at 7 px |

**The texture is the upstream TiC3 art, vendored verbatim** into `tools/gui_ref/` (MIT; provenance and
hashes in `tools/gui_ref/NOTICE.md`) and copied out by `tools/gen_assets.mjs`. Hand-redrawing it was
tried and produced an 8 % pixel mismatch — a GUI that is *nearly* the original is worse than one that
is exactly the original. If it is ever redrawn, `ControllerLayout` and the texture must change
together.

The multi-fluid tank stacks from the bottom up using `calcLiquidHeights`, a port of TiC3's algorithm:
`max(min=3, ceil(amount/capacity * height))`, then repeatedly shave a pixel off the tallest fluid
until the total fits (`height - min` when the tank is not full).

## Button ids and slot behaviour (the wire protocol)

The screen sends `menu.clickMenuButton(player, id)` **and** `gameMode.handleInventoryButtonClick`.
Ids match TiC3 exactly:

| id | Meaning |
|---|---|
| 0 | cycle transfer direction (AUTO → EMPTY_ITEM → FILL_ITEM) |
| 1 | fill the held item from the **fuel** tanks |
| 2 | drain the held item into the **fuel** tanks |
| 3 | drain the held item into the **melt** tank (clicking empty tank space) |
| 4+ | click fluid `id - 4` in the melt tank: with an item held, fill it; empty-handed, move that fluid to the bottom |

Bucket slots settle on insert: `BucketInputSlot.set` immediately exchanges fluid with the melt tank
and puts the result in the result slot. `BucketResultSlot.set` re-settles when emptied, so filling a
row of buckets works without re-inserting each one.

## Slot count must be sent in the menu-open packet

Slots are rebuilt with `slots.set(i, ...)` rather than moved, because **`Slot.x`/`Slot.y` are `final`
in 26.3** — there is no way to reposition an existing slot. Rebuilding preserves the index, so
client/server slot correspondence is unaffected.

`SmelteryControllerBlock.useWithoutItem` writes the slot count into the open packet
(`ControllerMenu.writeOpenData`). The client then resizes its inventory to match. Relying on the
block-entity sync instead is a race: the menu packet can arrive first, and the client would build the
wrong number of slots.

Slots beyond the 10 visible rows are moved to `OFF_PANEL` (-1000) rather than merely not drawn,
otherwise the player can put items into a cell they cannot see. Scrolling is driven by the mouse
wheel; the menu owns `scrollRow` and the screen only reports it.

## The client must be able to see the tanks and the tank contents

The GUI needs data `ContainerData` cannot carry (it is ints only): the fluid list for multi-segment
tank rendering, the item in each slot, and the fuel tanks' contents.

- Fluids and slot items ride the **block-entity update tag**. `saveAdditional` writes them and
  `getUpdateTag` reuses it; the controller calls `level.sendBlockUpdated(...)` when dirty, throttled
  to once per 4 ticks.
- **The fuel tank positions are part of that tag.** The client does not scan the structure, so
  without the `tanks` list it cannot find the tank blocks and the fuel gauge renders empty forever —
  while the server happily burns fuel.

## Constants that encode balance

| Constant | Where | Value | Meaning |
|---|---|---|---|
| `CAPACITY_PER_BLOCK` | controller | 1728 | mB per interior block |
| `MIN_CAPACITY` | controller | 1728 | floor so an unformed furnace still shows a sane tank |
| `BLOCKS_PER_FUEL` | controller | 15 | shell blocks per +1 fuel rate |
| `STRUCTURE_RECHECK_INTERVAL` | controller | 20 | ticks between full rescans |
| `TIME_SCALE` | MeltingSlot | 10 | recipe `time` → progress units |
| `NO_SPACE` | MeltingSlot | -1 | hot but the tank is full |
| cool rate | MeltingSlot | 5/tick | cooling is faster than heating |
| `MAX_VISIBLE_ROWS` | ControllerLayout | 10 | side-panel rows before scrolling |
| `DEFAULT_INNER_LIMIT` / `DEFAULT_MAX_HEIGHT` | MultiblockCuboid | 11 / 32 | max interior edge / total height |

## Verify with the probe, not with a compile

`smeltery/multiblock/MultiblockProbe` + `MultiblockProbeRunner` build a real furnace in the world at
`(0, 200, 0)` and call the **real** detection, tick, fuel, melting and layout code. Run it with
`SOULFORGE_SELFTEST=true` — the invocation and why a `-D` flag does not work are in
`soulforge-build-and-verify`.

A passing run is 16 lines (each is an assertion):

```
[selftest] 完整结构 -> OK 成形 inner=3x2x3 tanks=0 volume=18
[selftest] 换上一块燃料储罐 -> OK 成形 inner=3x2x3 tanks=1 volume=18
[selftest] 内部塞入方块 -> FAIL 未成形: Invalid smeltery wall block @ BlockPos{x=0, y=200, z=2}
[selftest] 顶层炉壁换成泥土 -> OK 成形 inner=3x1x3 tanks=1 volume=9
[selftest] 换回炉壁 -> OK 成形 inner=3x2x3 tanks=1 volume=18
[selftest] 成形后 tick 一次 -> IN_STRUCTURE = true (期望 true)
[selftest] 底层炉壁换成泥土后 -> IN_STRUCTURE = false (期望 false)
[selftest] 换回砖块后 -> IN_STRUCTURE = true (期望 true)
[selftest] 燃料储罐流体能力 -> OK 插入 1000mB 成功，抽出 1000mB 成功
[selftest] 流体移动 -> OK 20 tick 内水平位移=0.3815 inFluid=true fluidType=SoulForgeFluidType
[selftest] 熔炼槽数随体积 -> OK 槽位数=18 = 内部体积=18
[selftest] 熔炼槽状态机 -> OK 时间=1200 温度=1539 升温10 降温5 罐满NO_SPACE 出料=144mB
[selftest] 燃料模块 -> OK 温度=1500 速率=15 时长=150 每份=50mB
[selftest] 界面布局与滚动 -> OK 18槽=3列宽80 物品格x=12,34 热条x=8 100槽滚动15行
[selftest] 物品流体写回 -> OK 装入 1000mB 已回写到物品，抽出后为空
[selftest] 结束
```

`tanks=1` on the second line is the one to watch: it is the assertion that caught the lost `isTank`
override, and it is the difference between a furnace that burns fuel and one that never can.

The last three lines each guard a failure that produces **no error at all**: slot count not tracking
volume, the `NO_SPACE`/progress-0 confusion, and a fuel model that ignores `amount`/`rate`.
`probeItemFluidWriteBack` was added after reasoning from API names proved unreliable — it asserts that
inserting fluid through `ItemAccess.forStack(...)` actually writes back to the `ItemStack` (it does).

**Do not delete these two files.** They are the only way to test multiblock geometry, the fuel model
and fluid behaviour without a human in the client, they are untracked by git, and deleting them loses
them irrecoverably. `git add` them if a destructive step is coming.

## Known-correct behaviours that look like bugs

- **Replacing a *top* wall brick with dirt shrinks the furnace** (`inner=3x2x3` → `3x1x3`) instead of
  invalidating it. That is correct: the upward scan stops at the bad layer, and a shorter furnace is
  still a valid furnace. Only a failure on the bottom layer or in the interior collapses the structure.
- **An empty interior slot accepts a block** and then reports `Invalid smeltery wall block` at that
  position. The message names the wall because `detectLayer` checks the interior first and reports
  whatever failed; the position is what identifies the real problem.
