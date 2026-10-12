package com.soulforge.soulforge.alloy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A single material component within an alloy, identified by its material ID
 * and weighted by a ratio.
 */
public record AlloyComponent(Identifier materialId, float ratio) {
    public static final Codec<AlloyComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("material").forGetter(AlloyComponent::materialId),
            Codec.FLOAT.fieldOf("ratio").forGetter(AlloyComponent::ratio)
    ).apply(instance, AlloyComponent::new));

    public static final StreamCodec<ByteBuf, AlloyComponent> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, AlloyComponent::materialId,
            ByteBufCodecs.FLOAT, AlloyComponent::ratio,
            AlloyComponent::new
    );

    /**
     * Alias for {@link #materialId()} used by map-based mixture helpers.
     */
    public Identifier material() {
        return materialId;
    }

    /**
     * Merges another component of the same material by adding ratios.
     *
     * @throws IllegalArgumentException if the material IDs differ
     */
    public AlloyComponent merge(AlloyComponent other) {
        if (!this.materialId.equals(other.materialId())) {
            throw new IllegalArgumentException("Cannot merge alloy components with different material IDs");
        }
        return new AlloyComponent(this.materialId, this.ratio + other.ratio());
    }
}
