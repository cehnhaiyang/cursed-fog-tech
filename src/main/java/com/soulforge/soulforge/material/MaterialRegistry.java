package com.soulforge.soulforge.material;

import com.soulforge.soulforge.SoulForge;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MaterialRegistry {
    private static final Map<Identifier, SoulForgeMaterial> MATERIALS = new HashMap<>();

    public static final SoulForgeMaterial CALCITE = register("calcite", new SoulForgeMaterial.Builder("CaCO\u2083")
            .hardness(3.0f).density(2.71f).meltingPoint(1339)
            .durability(60).miningSpeed(2.0f).attackDamage(1.0f)
            .armor(3).toughness(0).miningLevel(1)
            .traitCalcite());

    public static final SoulForgeMaterial GOLD = register("gold", new SoulForgeMaterial.Builder("Au")
            .hardness(2.75f).density(19.32f).meltingPoint(1067)
            .durability(32).miningSpeed(12.0f).attackDamage(2.0f)
            .armor(5).toughness(0).miningLevel(0)
            .traitGold());

    public static final SoulForgeMaterial COPPER = register("copper", new SoulForgeMaterial.Builder("Cu")
            .hardness(3.0f).density(8.96f).meltingPoint(1083)
            .durability(180).miningSpeed(5.0f).attackDamage(2.5f)
            .armor(6).toughness(0).miningLevel(1)
            .traitCopper());

    public static final SoulForgeMaterial IRON = register("iron", new SoulForgeMaterial.Builder("Fe")
            .hardness(4.5f).density(7.87f).meltingPoint(1539)
            .durability(250).miningSpeed(6.0f).attackDamage(3.0f)
            .armor(7).toughness(0).miningLevel(2)
            .noTrait());

    public static final SoulForgeMaterial QUARTZ = register("quartz", new SoulForgeMaterial.Builder("SiO\u2082")
            .hardness(7.0f).density(2.65f).meltingPoint(1339)
            .durability(120).miningSpeed(7.0f).attackDamage(2.0f)
            .armor(4).toughness(0).miningLevel(2)
            .traitQuartz());

    public static final SoulForgeMaterial DIAMOND = register("diamond", new SoulForgeMaterial.Builder("C")
            .hardness(10.0f).density(3.52f).meltingPoint(3500)
            .durability(1561).miningSpeed(8.0f).attackDamage(4.0f)
            .armor(8).toughness(2).miningLevel(3)
            .traitDiamond());

    public static final SoulForgeMaterial COBALT = register("cobalt", new SoulForgeMaterial.Builder("Co")
            .hardness(5.5f).density(8.90f).meltingPoint(1500)
            .durability(800).miningSpeed(10.0f).attackDamage(3.5f)
            .armor(7).toughness(1).miningLevel(3)
            .traitCobalt());

    public static final SoulForgeMaterial OBSIDIAN = register("obsidian", new SoulForgeMaterial.Builder("SiO\u2082+")
            .hardness(5.75f).density(2.55f).meltingPoint(1700)
            .durability(200).miningSpeed(7.0f).attackDamage(3.0f)
            .armor(8).toughness(1).miningLevel(3)
            .traitObsidian());

    public static final SoulForgeMaterial NETHERITE = register("netherite", new SoulForgeMaterial.Builder("Netherite")
            .hardness(12.0f).density(12.0f).meltingPoint(3500)
            .durability(2031).miningSpeed(9.0f).attackDamage(4.0f)
            .armor(10).toughness(3).miningLevel(4)
            .traitNetherite());

    public static final SoulForgeMaterial TRINITY_ALLOY = register("trinity_alloy", new SoulForgeMaterial.Builder("Trinity")
            .hardness(11.0f).density(15.0f).meltingPoint(5000)
            .durability(3000).miningSpeed(10.0f).attackDamage(5.0f)
            .armor(12).toughness(4).miningLevel(4)
            .traitTrinityAlloy());

    public static final SoulForgeMaterial SINGULARITY = register("singularity", new SoulForgeMaterial.Builder("Singularity")
            .hardness(20.0f).density(25.0f).meltingPoint(10000)
            .durability(5000).miningSpeed(12.0f).attackDamage(6.0f)
            .armor(15).toughness(5).miningLevel(5)
            .noTrait());

    public static SoulForgeMaterial register(String name, SoulForgeMaterial.Builder builder) {
        Identifier id = Identifier.fromNamespaceAndPath(SoulForge.MOD_ID, name);
        SoulForgeMaterial material = builder.build(id);
        MATERIALS.put(id, material);
        return material;
    }

    public static SoulForgeMaterial byId(Identifier id) {
        return MATERIALS.get(id);
    }

    public static Map<Identifier, SoulForgeMaterial> getMaterials() {
        return Collections.unmodifiableMap(MATERIALS);
    }
}
