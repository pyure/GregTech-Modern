package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * The recipes the loop audit found to be material loops: a recipe whose output can be turned back into its input by
 * other recipes. Read at data reload from {@code data/gtceu/loop_recipes.json}, a generated file (see the README,
 * "Loop list"), so a datapack can override it. A recipe that is not in the list is treated as not being a loop.
 */
public final class LoopRecipeList {

    public static final ResourceLocation FILE = GTCEu.id("loop_recipes.json");

    /** recipe type id (e.g. {@code gtceu:wiremill}) to recipe ids (e.g. {@code gtceu:wiremill/mill_steel_wire}) */
    private static volatile Map<String, Set<String>> recipes = Map.of();

    private LoopRecipeList() {}

    public static boolean isLoop(GTRecipeType type, ResourceLocation recipeId) {
        Set<String> ids = recipes.get(type.registryName.toString());
        return ids != null && ids.contains(recipeId.toString());
    }

    public static int entryCount() {
        return recipes.values().stream().mapToInt(Set::size).sum();
    }

    /** @return the number of listed recipe ids that no longer exist in {@code manager}. */
    public static int countStale(RecipeManager manager) {
        return countStale(recipes, manager);
    }

    @VisibleForTesting
    public static int countStale(Map<String, Set<String>> list, RecipeManager manager) {
        return staleIds(list, manager).size();
    }

    /** @return the listed recipe ids that no longer exist in {@code manager}, sorted. */
    @VisibleForTesting
    public static List<String> staleIds(Map<String, Set<String>> list, RecipeManager manager) {
        List<String> stale = new ArrayList<>();
        for (Set<String> ids : list.values()) {
            for (String id : ids) {
                ResourceLocation location = ResourceLocation.tryParse(id);
                if (location == null || manager.byKey(location).isEmpty()) stale.add(id);
            }
        }
        Collections.sort(stale);
        return stale;
    }

    private static final int STALE_IDS_LOGGED = 10;

    public static void logSummary(RecipeManager manager) {
        List<String> stale = staleIds(recipes, manager);
        GTCEu.LOGGER.info("Loop list: {} entries loaded, {} stale", entryCount(), stale.size());
        if (!stale.isEmpty()) {
            GTCEu.LOGGER.info("Loop list: first {} stale entries (recipes this pack or world does not have): {}",
                    Math.min(STALE_IDS_LOGGED, stale.size()),
                    stale.subList(0, Math.min(STALE_IDS_LOGGED, stale.size())));
        }
    }

    /** Reads the list from {@code manager} and makes it the current list. */
    public static void load(ResourceManager manager) {
        recipes = read(manager);
    }

    /** Reads the list; a missing or unreadable file logs a warning and gives an empty list. */
    @VisibleForTesting
    public static Map<String, Set<String>> read(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            GTCEu.LOGGER.warn("Loop list: {} not found, no recipe will be treated as a loop", FILE);
            return Map.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = GsonHelper.parse(reader);
            Map<String, Set<String>> parsed = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(root, "recipes").entrySet()) {
                Set<String> ids = new HashSet<>();
                JsonArray array = entry.getValue().getAsJsonArray();
                array.forEach(e -> ids.add(e.getAsString()));
                parsed.put(entry.getKey(), ids);
            }
            return parsed;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            GTCEu.LOGGER.warn("Loop list: could not read {}, no recipe will be treated as a loop", FILE, e);
            return Map.of();
        }
    }

    @VisibleForTesting
    public static Map<String, Set<String>> snapshot() {
        return recipes;
    }
}
