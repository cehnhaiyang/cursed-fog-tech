package com.soulforge.soulforge.alloy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an alloy as a weighted collection of {@link AlloyComponent}s plus
 * optional display metadata (custom name and color).
 */
public class AlloyMixture {
    public static final Codec<AlloyMixture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AlloyComponent.CODEC.listOf().fieldOf("components").forGetter(AlloyMixture::getComponents),
            Codec.FLOAT.fieldOf("total").forGetter(AlloyMixture::getTotalRatio),
            Codec.STRING.optionalFieldOf("customName", "").forGetter(AlloyMixture::getCustomName),
            Codec.INT.optionalFieldOf("color", 0).forGetter(AlloyMixture::getColor)
    ).apply(instance, AlloyMixture::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyMixture> STREAM_CODEC = StreamCodec.composite(
            AlloyComponent.STREAM_CODEC.apply(ByteBufCodecs.list()), AlloyMixture::getComponents,
            ByteBufCodecs.FLOAT, AlloyMixture::getTotalRatio,
            ByteBufCodecs.STRING_UTF8, AlloyMixture::getCustomName,
            ByteBufCodecs.INT, AlloyMixture::getColor,
            AlloyMixture::new
    );

    private final List<AlloyComponent> components;
    private float totalRatio;
    private String customName;
    private int color;

    public AlloyMixture() {
        this(new ArrayList<>(), 0.0f, "", 0);
    }

    public AlloyMixture(List<AlloyComponent> components, float totalRatio, String customName, int color) {
        this.components = new ArrayList<>(components);
        this.totalRatio = totalRatio;
        this.customName = customName != null ? customName : "";
        this.color = color;
    }

    public List<AlloyComponent> getComponents() {
        return components;
    }

    public float getTotalRatio() {
        return totalRatio;
    }

    public String getCustomName() {
        return customName;
    }

    public int getColor() {
        return color;
    }

    public void setCustomName(String customName) {
        this.customName = customName != null ? customName : "";
    }

    public void setColor(int color) {
        this.color = color;
    }

    /**
     * Adds a component, merging with an existing entry for the same material.
     */
    public void addComponent(AlloyComponent component) {
        for (int i = 0; i < components.size(); i++) {
            AlloyComponent existing = components.get(i);
            if (existing.materialId().equals(component.materialId())) {
                components.set(i, existing.merge(component));
                totalRatio += component.ratio();
                return;
            }
        }
        components.add(component);
        totalRatio += component.ratio();
    }

    /**
     * Normalizes all ratios so they sum to 1.0. No-op if the total ratio is 0.
     */
    public void normalize() {
        if (totalRatio <= 0.0f) {
            return;
        }
        List<AlloyComponent> normalized = new ArrayList<>(components.size());
        for (AlloyComponent component : components) {
            normalized.add(new AlloyComponent(component.materialId(), component.ratio() / totalRatio));
        }
        components.clear();
        components.addAll(normalized);
        totalRatio = 1.0f;
    }

    /**
     * Merges another mixture into this one, weighting each of its components by
     * the supplied factor. Display metadata of this mixture is preserved.
     */
    public void merge(AlloyMixture other, float weight) {
        for (AlloyComponent component : other.components) {
            addComponent(new AlloyComponent(component.materialId(), component.ratio() * weight));
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (AlloyComponent component : components) {
            CompoundTag entry = new CompoundTag();
            entry.putString("material", component.materialId().toString());
            entry.putFloat("ratio", component.ratio());
            list.add(entry);
        }
        tag.put("components", list);
        tag.putFloat("total", totalRatio);
        if (!customName.isEmpty()) {
            tag.putString("customName", customName);
        }
        tag.putInt("color", color);
        return tag;
    }

    public static AlloyMixture deserializeNBT(CompoundTag tag) {
        List<AlloyComponent> components = new ArrayList<>();
        tag.getList("components").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompoundOrEmpty(i);
                Identifier id = Identifier.parse(entry.getStringOr("material", ""));
                components.add(new AlloyComponent(id, entry.getFloatOr("ratio", 0.0f)));
            }
        });
        return new AlloyMixture(components, tag.getFloatOr("total", 0.0f),
                tag.getStringOr("customName", ""), tag.getIntOr("color", 0));
    }
}
