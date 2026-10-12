---
name: soulforge-neoforge-26.3-api
description: The exact NeoForge/Minecraft 26.3 API replacements this repo (锻魂 / SoulForge, mod_id=soulforge) needs, because its source was written for 1.21.1. Applies to this project only; not a general 26.3 migration guide.
whenToUse: Read before writing or repairing any Java file in this repo. Almost every pre-26.3 API the code used has been deleted or renamed, so code written from memory will not compile.
---

# SoulForge on NeoForge 26.3 — API mapping

The source under `src/main/java/com/soulforge/soulforge/` was authored against Minecraft 1.21.1.
`gradle.properties` pins `minecraft_version=26.3` / `neo_version=26.3.0.69-beta`. Every entry below
is a real compile error this repo hit, with the replacement that made `:compileJava` pass.

## Where to confirm a signature before writing code

Never guess a 26.3 signature. Three local sources, all already on disk:

| What | Path |
|---|---|
| Decompiled Minecraft sources | `build\moddev\artifacts\minecraft-patched-26.3.0.69-beta-sources.jar` |
| Patched Minecraft classes | `build\moddev\artifacts\minecraft-patched-26.3.0.69-beta.jar` |
| Raw client jar (has every class, no sources) | `C:\Users\q1505\.gradle\caches\neoformruntime\artifacts\minecraft_26.3_client.jar` |
| NeoForge sources | `C:\Users\q1505\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\26.3.0.69-beta\a9e95df815b425333431737b7eaf8d01e5983773\neoforge-26.3.0.69-beta-sources.jar` |
| NeoForge classes | `C:\Users\q1505\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\26.3.0.69-beta\434f72f2ad529bc2932dd8e19184af86ace3c6e6\neoforge-26.3.0.69-beta-universal.jar` |
| NeoForge's own patches to vanilla | `build\tmp\neoformruntime\<timestamp>_applyNeoforgePatches\patches\` |

Extract a single entry with `System.IO.Compression.ZipFile` in PowerShell, or list entries to find
a class. The NeoForge sources jar is the authority for `net.neoforged.*`; the Minecraft sources jar
for `net.minecraft.*`.

**`javap` is not on `PATH`.** It ships with the toolchain Gradle provisioned:

```
C:\Users\q1505\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2\bin\javap.exe
```

Dump an exact signature with
`& $javap -cp <jar> net.minecraft.world.level.storage.ValueInput`. Add `-p -c` to read bytecode when
you need to know which overload a caller actually invokes. The JDK at `C:\Program Files\Java\jdk-17`
is too old for these jars — use the Gradle one.

`javap` prints its diagnostics in GBK, so a missing class surfaces as mojibake in a UTF-8 terminal
rather than as readable text. Read the exit code and the class name, not the message.

**The `_applyNeoforgePatches` directory is the authority for how NeoForge rewires vanilla.** When a
vanilla method behaves unexpectedly, read the patch instead of guessing from the decompiled result:
`patches\net\minecraft\world\entity\LivingEntity.java.patch` is a diff against vanilla, so it shows
exactly which lines NeoForge inserted and what the original did. This is how the fluid-movement
behaviour below was pinned down.

## Identifier replaces ResourceLocation

`net.minecraft.resources.ResourceLocation` is **deleted**. Use `net.minecraft.resources.Identifier`.
Same static surface: `fromNamespaceAndPath`, `parse`, `withDefaultNamespace`, `tryParse`, `read`,
`CODEC`, `STREAM_CODEC`. This is a pure rename — 56 occurrences across 13 files here.

## Interaction results

`net.minecraft.world.ItemInteractionResult` is **deleted**. `BlockBehaviour.useItemOn` now returns
`net.minecraft.world.InteractionResult`, which is a **sealed interface**, not an enum:

- `ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION` → `InteractionResult.PASS`
- `InteractionResult.sidedSuccess(boolean)` (also gone) → `InteractionResult.SUCCESS`
- Constants: `SUCCESS`, `SUCCESS_SERVER`, `CONSUME`, `FAIL`, `PASS`, `TRY_WITH_EMPTY_HAND`
- `useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)`
- `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)`

`Level.isClientSide` is now a **private field**; call `level.isClientSide()` everywhere. Same for
`level.random` → `level.getRandom()`.

## GUI: GuiGraphicsExtractor

`net.minecraft.client.gui.GuiGraphics` is gone. Use `net.minecraft.client.gui.GuiGraphicsExtractor`.

- `renderBg(GuiGraphics, float, int, int)` no longer exists → override
  `extractBackground(GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick)`
- `render(...)` → `extractRenderState(GuiGraphicsExtractor, int, int, float)`
- `graphics.renderTooltip(font, component, x, y)` → `graphics.setTooltipForNextFrame(font, component, x, y)`
- `graphics.blit(texture, x, y, u, v, w, h)` needs a pipeline and the texture size:
  `graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, 256, 256)`
- `AbstractContainerScreen.imageWidth` / `imageHeight` are **final**. Set them through the
  4-arg constructor `super(menu, inventory, title, 176, 166)`, not by assignment.

## Fluid and item capability packages were deleted

`net.neoforged.neoforge.fluids.capability.*` and `net.neoforged.neoforge.items.*` **do not exist**.
Everything moved to `net.neoforged.neoforge.transfer.*`:

| 1.21.1 | 26.3 |
|---|---|
| `fluids.FluidUtil` | `transfer.fluid.FluidUtil` |
| `fluids.capability.IFluidHandler` | `transfer.ResourceHandler<FluidResource>` |
| `fluids.capability.IFluidHandler.FluidAction` | a plain `boolean simulate` parameter |
| `fluids.capability.templates.FluidTank` | `transfer.fluid.FluidStacksResourceHandler` |
| `items.ItemStackHandler` | `transfer.item.ItemStacksResourceHandler` |
| `items.SlotItemHandler` | `transfer.item.ResourceHandlerSlot(handler, handler::set, index, x, y)` |
| `fluids.capability.templates.FluidHandlerItemStack` | `transfer.fluid.ItemAccessFluidHandler(itemAccess, componentType, capacity)` |
| `items.wrapper.SidedInvWrapper` | `transfer.item.WorldlyContainerWrapper(container, side)` |
| `Capabilities.FluidHandler.BLOCK` | `Capabilities.Fluid.BLOCK` |
| `Capabilities.ItemHandler.BLOCK` | `Capabilities.Item.BLOCK` |
| `Capabilities.FluidHandler.ITEM` | `Capabilities.Fluid.ITEM` (context type is `ItemAccess`) |

Consequences that are easy to miss:

- `insert`/`extract` take a `TransactionContext` and must be wrapped:
  `try (Transaction tx = Transaction.openRoot()) { handler.insert(0, resource, amount, tx); tx.commit(); }`
- A custom handler implements `ResourceHandler<FluidResource>` and extends
  `transfer.transaction.SnapshotJournal<FluidStack>`, calling `updateSnapshots(transaction)` before
  every mutation. `CastingFluidHandler` in this repo is the working example.
- `FluidStack.isSameFluid(a, b)` and the `FluidStackTemplate` overloads replace ad-hoc
  `a.getFluid().equals(b.getFluid())` comparisons.
- Item fluid capabilities are queried as
  `stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack))`.
- `ItemAccessFluidHandler` stores fluid in the `SimpleFluidContent` component passed to it and
  captures `validItem` from the access at construction, so one handler instance is bound to one item
  type. `ItemAccess.forStack(stack)` plus `access.getResource().toStack(access.getAmount())` is the
  round trip for swapping a fluid container inside a menu slot.
- Block fluid capabilities are looked up as
  `level.getCapability(Capabilities.Fluid.BLOCK, pos, null)` — `null` for the side means unsided.

## A default method on an interface is silently lost through a method reference

Not a 26.3 API change, but this repo was bitten by it and the symptom is misleading. Given:

```java
public interface BlockValidator {
    boolean isValid(Level world, BlockPos pos, CuboidSide side);
    default boolean isTank(BlockState state) { return false; }
}
```

passing `this::isValidBlock` as the validator satisfies the single abstract method and **discards
`isTank` entirely** — the default body runs and forever returns `false`. The class must
`implements BlockValidator` and pass `this`.

The failure mode is worse than a plain missing feature because the object is still half-honoured:
`isValid` runs correctly (fuel tanks are accepted as legal wall blocks, the structure forms normally),
so everything looks right while `isTank` silently reports "no tanks" and a fuel-burning machine can
never find fuel. When an interface has a default method that carries meaning, check every call site
for a method reference before trusting it.

## Custom fluids MUST override FluidType.move, or entities freeze

This is the highest-severity 26.3 behaviour change in this repo, and nothing about the code looks
wrong when it happens: the fluid renders, buckets work, but any entity standing in it **cannot move
at all**.

NeoForge's patch to `LivingEntity.travel` (read it at
`build\tmp\neoformruntime\<ts>_applyNeoforgePatches\patches\net\minecraft\world\entity\LivingEntity.java.patch`):

```java
+ if (!this.getFluidInteraction().isInFluidMatching(this, (entity, type, _) -> !type.isVanilla() && entity.moveInFluid(type, input, baseGravity)))
      if (this.isInWater()) {
          this.travelInWater(input, baseGravity, isFalling, oldY);
-     } else {
+     } else if (this.isInLava()) { // Neo: only apply lava movement if the player is actually in lava and not another fluid
          this.travelInLava(input, baseGravity, isFalling, oldY);
      }
```

`isInFluidMatching` calls `FluidType.move` **only for non-vanilla fluids**. If it returns `false`
(and the fluid is not water and not lava), **none of the three movement branches run** — the entity
is frozen every tick. `FluidType.move`'s own javadoc states the contract:

> movement logic will default to water if `getIsWaterLike()` returns `true` or **no movement if it
> returns `false`**

`isWaterLike` defaults to `false` (`FluidType.java`, `private boolean isWaterLike = false;`), so the
default is the frozen case. `isWaterLike` itself only affects water *semantics* — eye-in-water,
fish, zombie drowning (`Entity.java.patch`, `Player.java.patch`) — it is **not** consulted for
movement. Setting it will not fix movement.

Fix: override `move` and return `true`. Mirror vanilla water structure so the feel is sane:

```java
@Override
public boolean move(LivingEntity entity, Vec3 movementVector, double gravity) {
    entity.moveRelative(MOLTEN_SPEED, movementVector);          // 0.02F, same as water
    Vec3 delta = entity.getDeltaMovement();
    entity.setDeltaMovement(delta.x * DRAG, delta.y - gravity * SCALE, delta.z * DRAG);
    entity.move(MoverType.SELF, entity.getDeltaMovement());
    entity.setDeltaMovement(entity.getDeltaMovement().scale(DRAG));
    return true;
}
```

`moveRelative` already reads and adds to `getDeltaMovement()`, so do not add the input vector a
second time. TiC3's `motionScale(0.0023333333333333335D)` + `canSwim(false)` recipe is 1.21.1
semantics and does **not** carry over.

Regression test: spawn a mob in the fluid and drive `mob.travel(new Vec3(0.5, 0, 0.5))` 20 times,
then assert the horizontal displacement is non-zero. `Mob` is concrete; `Player` is abstract and
cannot be instantiated for this.

## Entity and renderer classes moved

| 1.21.1 | 26.3 |
|---|---|
| `net.minecraft.world.entity.EntityType.CREEPER` | `net.minecraft.world.entity.EntityTypes.CREEPER` |
| `net.minecraft.world.entity.monster.Zombie` | `net.minecraft.world.entity.monster.zombie.Zombie` |

`EntityType` keeps only `CODEC` / `STREAM_CODEC` / `getKey`; the per-entity constants all live in the
new `EntityTypes` holder class.

`BlockEntityRenderer` is now generic over a render state and has no `render` method:

```java
public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> {
    S createRenderState();
    default void extractRenderState(T, S, float, Vec3, ModelFeatureRenderer.CrumblingOverlay);
    void submit(S, PoseStack, SubmitNodeCollector, CameraRenderState);
}
```

`net.minecraft.client.renderer.block.LiquidBlockRenderer` does **not** exist in 26.3. There is no
drop-in way to draw a fluid into a GUI or a block entity renderer — `GuiGraphicsExtractor` has
`fill` / `blit` / `submitCustomGeometry`, and the render pipeline moved to
`RenderSetup` / `RenderPipeline` / `com.mojang.renderpearl.api.*`. Budget real time for any custom
fluid rendering; `ControllerScreen` renders tank levels with plain `graphics.fill`.

`AbstractContainerScreen.mouseClicked` takes a record, not three primitives:

```java
public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick)
// event.x(), event.y(), event.button()
```

`setTooltipForNextFrame` has no `(Font, List<Component>, int, int)` overload. Pass an empty
`Optional<TooltipComponent>`:

```java
graphics.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
```

`player.displayClientMessage(Component, boolean)` is **deleted**. For an action-bar message use
`player.sendSystemMessage(component, true)`.



`loadAdditional(CompoundTag, HolderLookup.Provider)` and `saveAdditional(CompoundTag, ...)` are gone.

- `protected void loadAdditional(ValueInput input)`
- `protected void saveAdditional(ValueOutput output)`
- `output.child(name)` returns a nested `ValueOutput`; `input.childOrEmpty(name)` / `input.child(name)` for reading.
- `output.store(name, Codec, value)` / `input.read(name, Codec)`.
- `CompoundTag.contains(String, byte)` is **deleted**; `contains(String)` remains.
- Getters now return `Optional`: `getInt`, `getFloat`, `getString`, `getCompound`, `getList`, `getBoolean`.
  Use the `getXOr(name, default)` forms, and `list.getCompoundOrEmpty(i)` / `getListOrEmpty(i)` on `ListTag`.
- `FluidStack` has **no** `parse`/`save`; store it with `FluidStack.OPTIONAL_CODEC`.

## Recipes

`RecipeSerializer` is now a **record**, not an interface:
`public record RecipeSerializer<T extends Recipe<?>>(MapCodec<T> codec, StreamCodec<...> streamCodec)`.
There is no `codec()` / `streamCodec()` to override. Declare a field instead:

```java
public static final RecipeSerializer<MeltingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
```

and register with a lambda: `SoulForgeRegistries.RECIPE_SERIALIZERS.register("melting", () -> MeltingRecipe.SERIALIZER)`.
Registering `MeltingRecipe.Serializer::new` fails with `此处需要接口` / "interface expected".

`Recipe<T>` gained members and lost others:

- **Added, must implement:** `assemble(T input)` (no `HolderLookup.Provider` argument),
  `showNotification()`, `group()`, `placementInfo()`, `recipeBookCategory()`.
- **Removed:** `canCraftInDimensions(int, int)`, `getResultItem(HolderLookup.Provider)`,
  `getIngredients()` is no longer part of the interface.
- `getSerializer()` / `getType()` return `RecipeSerializer<? extends Recipe<T>>` /
  `RecipeType<? extends Recipe<T>>`.
- `Ingredient.EMPTY` is **deleted**. Use `Ingredient.of()` for an empty ingredient, or make the
  field `Optional<Ingredient>` and test with `Ingredient.testOptionalIngredient(optional, stack)`.
- Recipe lookup: `level.getRecipeManager()` is gone. Use
  `serverLevel.recipeAccess().recipeMap().getRecipesFor(type, input, level)` (returns a `Stream`).
  `Level.recipeAccess()` only returns a real manager on `ServerLevel`, so guard with
  `if (!(level instanceof ServerLevel serverLevel)) return null;`.

## Blocks, block entities, registration

- Block `codec()` and `BlockBehaviour.simpleCodec` are **deleted**. Delete the `CODEC` field and the
  `codec()` override; a `MapCodec<...> codec()` override fails with "method does not override".
- `BlockEntityType.Builder` is **deleted**. Use `new BlockEntityType<>(Factory::new, block1, block2, ...)`.
- `DeferredRegister` register methods take `Supplier<Properties>` or `UnaryOperator<Properties>`,
  never a bare `Properties`. Write `p -> p.strength(3.0F)`.
- `neighborChanged` takes `@Nullable Orientation` where it used to take `BlockPos`.
- `onRemove` → `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)`.
- `player.hurt(source, amount)` → `player.hurtServer(serverLevel, source, amount)`.
- `player.drop(stack, boolean)` → `player.drop(stack, boolean, Prediction.SERVER_ONLY)`.
- `@EventBusSubscriber(bus = ...)` — the `bus` member is **deleted**; only `Dist[] value()` and
  `String modid()` remain. Drop the argument.
- `ModConfig.Type.COMMON` does not exist. The enum is `STARTUP`, `CLIENT`, `SYNCED`, `LOCAL`.

## Registration ids must come from registerItem/registerBlock

`DeferredRegister.register(name, Supplier)` does **not** write the registry id into the object's
`Properties`, and 26.3 requires it. Constructing an item or block through the bare overload throws at
registry time:

```
java.lang.NullPointerException: Item id not set
    at net.minecraft.world.item.Item$Properties.itemIdOrThrow
java.lang.NullPointerException: Block id not set
    at net.minecraft.world.level.block.state.BlockBehaviour$Properties.effectiveDrops
```

Only these write the id: `registerItem`, `registerBlock`, `registerSimpleItem`, `registerSimpleBlock`,
`registerSimpleBlockItem`. `SoulForgeFluids.registerFluid` must use `registerItem` for the bucket and
`registerBlock` for the `LiquidBlock`.

`registerSimpleBlockItem(Holder<Block>)` derives the item name from **the block's registry name**.
This repo has a block `netherite_fuel_tank` and a separate fluid-container item of the same name, so
the block item must pass an explicit name:

```java
SoulForgeRegistries.ITEMS.registerSimpleBlockItem("netherite_fuel_tank_block",
        SoulForgeBlocks.NETHERITE_FUEL_TANK, p -> p.fireResistant())
```

Omitting the name throws `IllegalArgumentException: Duplicate registration netherite_fuel_tank`.
The same applies to `trinity_alloy_fuel_tank_block` and `magvac_fuel_tank_block`.
