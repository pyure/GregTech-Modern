package com.gregtechceu.gtceu.gametest.looplist;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.LoopRecipeList;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Checks for the generated loop list and its loader (see {@link LoopRecipeList}). */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LoopListTest {

    private static final String BATCH = "LoopList";

    @GameTest(template = "empty", batch = BATCH)
    public static void knownLoopRecipesAreListed(GameTestHelper helper) {
        var wire = GTCEu.id("wiremill/mill_steel_wire");
        var alloy = GTCEu.id("mixer/blue_alloy");
        helper.assertTrue(LoopRecipeList.entryCount() > 0, "the loop list is empty (file missing or unreadable)");
        helper.assertTrue(LoopRecipeList.isLoop(GTRecipeTypes.WIREMILL_RECIPES, wire), "wiremill steel wire");
        helper.assertTrue(LoopRecipeList.isLoop(GTRecipeTypes.MIXER_RECIPES, alloy), "mixer blue alloy");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void lossyRecipeIsNotListed(GameTestHelper helper) {
        var id = GTCEu.id("electrolyzer/decomposition_electrolyzing_pollucite");
        var manager = helper.getLevel().getServer().getRecipeManager();
        helper.assertTrue(manager.byKey(id).isPresent(), "test premise: the pollucite recipe exists");
        helper.assertFalse(LoopRecipeList.isLoop(GTRecipeTypes.ELECTROLYZER_RECIPES, id),
                "a lossy recipe is listed as a loop");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void staleEntriesAreCounted(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        Map<String, Set<String>> list = new HashMap<>();
        LoopRecipeList.snapshot().forEach((k, v) -> list.put(k, new HashSet<>(v)));
        int before = LoopRecipeList.countStale(list, manager);
        list.computeIfAbsent("gtceu:wiremill", k -> new HashSet<>()).add("gtceu:wiremill/no_such_recipe");
        int after = LoopRecipeList.countStale(list, manager);
        helper.assertTrue(after == before + 1,
                "one bogus entry should add exactly one stale entry: before " + before + ", after " + after);
        helper.assertTrue(LoopRecipeList.staleIds(list, manager).contains("gtceu:wiremill/no_such_recipe"),
                "the stale id list should name the bogus entry");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void missingFileGivesEmptyListWithoutCrashing(GameTestHelper helper) {
        Map<String, Set<String>> list = LoopRecipeList.read(ResourceManager.Empty.INSTANCE);
        helper.assertTrue(list.isEmpty(), "a missing file should give an empty list");
        helper.succeed();
    }
}
