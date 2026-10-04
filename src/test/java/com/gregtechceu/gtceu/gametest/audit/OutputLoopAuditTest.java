package com.gregtechceu.gtceu.gametest.audit;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Dev-only audit, not a real test. Dumps every machine recipe that is "element-conserving" (output holds at least
 * as much of every element as the input) and checks whether the output can be turned back into the input again using
 * only other conserving recipes (depth-limited). Those recipes are the ones where a free bonus output would create a
 * resource loop.
 *
 * Runs only when env var GT_OUTPUT_AUDIT=1; otherwise it passes immediately. Output goes to
 * run/gt_audit/recipes.csv and run/gt_audit/summary.txt.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class OutputLoopAuditTest {

    /** An element count must be at least this fraction of the input to count as "not lossy". */
    /**
     * A recipe counts as a step of a loop when it keeps at least this fraction of the material it consumes. Low on
     * purpose: a bonus of up to 2.85x makes even a fairly lossy loop profitable, and a loop list that is too big
     * only means more bonuses are delivered as defective items.
     */
    private static final double RETAIN = 0.5;
    private static final int MAX_DEPTH = 4;

    private static final Map<Material, Map<String, Double>> DECOMP_CACHE = new HashMap<>();

    /** Items/fluids that are consumed or produced, with element content (null = unknown/opaque). */
    private record Held(String key, Map<String, Double> content) {}

    private static final class R {

        String type, id;
        final List<Held> consumed = new ArrayList<>();
        final Set<String> freeKeys = new HashSet<>();
        final List<Held> guaranteed = new ArrayList<>();
        final List<Held> chancedExpected = new ArrayList<>();
        boolean unknown;
        final Set<String> unknownKeys = new TreeSet<>();
        /** Every item any ingredient accepts (all tag members, not just the first). Used for dead-end analysis. */
        final Set<String> altKeys = new TreeSet<>();
        /** One entry per consumed ingredient: every key that ingredient accepts. */
        final List<Set<String>> consumedAlts = new ArrayList<>();
        /** Reagent fluids (water, lubricant, gases): ignored as inputs unless the recipe has no other input. */
        final List<Held> reagents = new ArrayList<>();
        final List<Set<String>> reagentAlts = new ArrayList<>();
        String typeFull, idFull;
        /** null for GT machine recipes, else the vanilla recipe kind (crafting, smelting, blasting). */
        String vanillaKind;
        Map<String, Double> in = new HashMap<>(), out = new HashMap<>(), outExp = new HashMap<>();
        Set<String> consumedKeys = new HashSet<>(), outKeys = new HashSet<>();
        double ratio;
        boolean conserving;
        boolean solidFluidOut;
        String path = "";
    }

    @GameTest(template = "empty", batch = "OutputAudit")
    public static void outputLoopAudit(GameTestHelper helper) throws IOException {
        if (!"1".equals(System.getenv("GT_OUTPUT_AUDIT"))) {
            helper.succeed();
            return;
        }
        var rm = helper.getLevel().getServer().getRecipeManager();
        List<R> all = new ArrayList<>();
        for (GTRecipeType type : GTRegistries.RECIPE_TYPES) {
            for (GTRecipe recipe : rm.getAllRecipesFor(type)) {
                all.add(parse(type, recipe));
            }
        }
        var vanillaTypes = Set.of(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                net.minecraft.world.item.crafting.RecipeType.SMELTING,
                net.minecraft.world.item.crafting.RecipeType.BLASTING);
        for (var recipe : rm.getRecipes()) {
            if (vanillaTypes.contains(recipe.getType())) {
                all.add(parseVanilla(recipe, helper.getLevel().registryAccess()));
            }
        }
        List<R> conserving = all.stream().filter(r -> r.conserving).toList();
        Map<String, List<R>> byConsumedKey = new HashMap<>();
        for (R r : conserving) {
            for (Set<String> alts : r.consumedAlts) {
                for (String k : alts) byConsumedKey.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
            }
        }
        for (R r : conserving) r.path = findReturnPath(r, byConsumedKey);

        Path dir = FMLPaths.GAMEDIR.get().resolve("gt_audit");
        Files.createDirectories(dir);
        Path csvPath = dir.resolve("recipes.csv");
        try {
            Files.deleteIfExists(csvPath);
        } catch (IOException locked) {
            csvPath = dir.resolve("recipes_" + System.currentTimeMillis() + ".csv");
        }
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            csv.println(
                    "type,id,status,ratio,expChancedRatio,consumed,produced,returnPath,ingredientAlts,solidFluidOut");
            for (R r : all) {
                String status = r.unknown ? "UNKNOWN" :
                        !r.conserving ? "lossy" : r.path.isEmpty() ? "conserving" : "LOOP";
                double expRatio = total(r.in) == 0 ? 0 : (total(r.out) + total(r.outExp)) / total(r.in);
                csv.printf("%s,%s,%s,%.4f,%.4f,\"%s\",\"%s\",\"%s\",\"%s\",%s%n", r.type, r.id, status, r.ratio,
                        expRatio,
                        String.join(" + ", r.consumedKeys), String.join(" + ", r.outKeys), r.path,
                        String.join(" ", r.altKeys), r.solidFluidOut);
            }
        }
        writeLoopList(all, dir);
        Map<String, int[]> perType = new TreeMap<>();
        for (R r : all) {
            int[] c = perType.computeIfAbsent(r.type, x -> new int[4]);
            c[0]++;
            if (r.unknown) c[1]++;
            else if (r.conserving) c[2]++;
            if (!r.path.isEmpty()) c[3]++;
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(dir.resolve("summary.txt"),
                StandardCharsets.UTF_8))) {
            out.printf("%-28s %8s %8s %10s %8s%n", "recipe type", "total", "unknown", "conserving", "LOOP");
            perType.forEach((t, c) -> out.printf("%-28s %8d %8d %10d %8d%n", t, c[0], c[1], c[2], c[3]));
            long loops = all.stream().filter(r -> !r.path.isEmpty()).count();
            out.printf("%nTOTAL loop recipes: %d (first-tag-member-only baseline on 2026-10-02: 12671)%n", loops);
        }
        Map<String, Integer> unk = new HashMap<>();
        for (R r : all) if (r.unknown) for (String k : r.unknownKeys) unk.merge(k, 1, Integer::sum);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(dir.resolve("unknown_causes.txt"),
                StandardCharsets.UTF_8))) {
            unk.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> out.printf("%6d  %s%n", e.getValue(), e.getKey()));
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(dir.resolve("fluid_classes.txt"),
                StandardCharsets.UTF_8))) {
            FLUID_CLASSES.forEach((k, solid) -> out.printf("%-48s %s%n", k, solid ? "SOLID-IN-LIQUID-FORM" : "other"));
        }
        GTCEu.LOGGER.info("Output loop audit written to {}", dir);
        helper.succeed();
    }

    // ---------- loop list output ----------

    /**
     * Writes the list the game reads at runtime: GT machine recipes (and the vanilla smelting recipes the electric
     * furnace proxies) that the audit classified as loops, grouped by recipe type, sorted so unchanged input gives
     * byte-identical output. Destination: env GT_LOOP_LIST_OUT, else gt_audit/loop_recipes.json.
     */
    private static void writeLoopList(List<R> all, Path auditDir) throws IOException {
        TreeMap<String, TreeSet<String>> byType = new TreeMap<>();
        for (R r : all) {
            if (r.unknown || !r.conserving || r.path.isEmpty()) continue;
            String type;
            if (r.vanillaKind == null) type = r.typeFull;
            else if (r.vanillaKind.equals("smelting")) type = "gtceu:electric_furnace";
            else continue; // crafting and vanilla blasting have no bonus mechanism
            byType.computeIfAbsent(type, k -> new TreeSet<>()).add(r.idFull);
        }
        JsonObject meta = new JsonObject();
        meta.addProperty("about", "Recipes the loop audit found to be material loops. Generated, do not edit by hand.");
        meta.addProperty("regenerate", "./gradlew regenerateLoopList");
        meta.addProperty("minRetention", RETAIN);
        meta.addProperty("ignoredInputFluids", "water, distilled water, lubricant, gases (acids are counted)");
        meta.addProperty("maxReturnHops", MAX_DEPTH);
        meta.addProperty("maceratorRecyclingYield", ConfigHolder.INSTANCE.recipes.maceratorRecyclingYield);
        meta.addProperty("arcRecyclingYield", ConfigHolder.INSTANCE.recipes.arcRecyclingYield);
        meta.addProperty("extractorRecyclingYield", ConfigHolder.INSTANCE.recipes.extractorRecyclingYield);
        // dev mode forces this on (CommonProxy), so the list includes gem-quality recipes a default install lacks
        meta.addProperty("generateLowQualityGems", ConfigHolder.INSTANCE.recipes.generateLowQualityGems);
        meta.addProperty("ignored", "crafting-table recipes, vanilla blasting; ore chain assumed safe");
        JsonObject recipes = new JsonObject();
        byType.forEach((type, ids) -> {
            JsonArray array = new JsonArray();
            ids.forEach(array::add);
            recipes.add(type, array);
        });
        JsonObject root = new JsonObject();
        root.add("_meta", meta);
        root.add("recipes", recipes);
        String env = System.getenv("GT_LOOP_LIST_OUT");
        Path out = env != null && !env.isBlank() ? Path.of(env) : auditDir.resolve("loop_recipes.json");
        Files.createDirectories(out.getParent());
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
        Files.writeString(out, json, StandardCharsets.UTF_8);
        GTCEu.LOGGER.info("Loop list written to {} ({} recipe types)", out, byType.size());
    }

    // ---------- loop search ----------

    /** True when every ingredient has at least one acceptable alternative in {@code reach}. */
    private static boolean satisfied(List<Set<String>> ingredientAlts, Set<String> reach) {
        for (Set<String> alts : ingredientAlts) {
            if (Collections.disjoint(alts, reach)) return false;
        }
        return true;
    }

    private static String findReturnPath(R start, Map<String, List<R>> byConsumedKey) {
        if (start.consumedAlts.isEmpty()) return "";
        Map<String, R> producedBy = new HashMap<>();
        Set<String> reach = new HashSet<>(start.outKeys);
        for (String free : start.freeKeys) reach.add(free);
        Set<R> fired = new HashSet<>();
        fired.add(start);
        Set<String> frontier = new HashSet<>(reach);
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Set<String> next = new HashSet<>();
            for (String key : frontier) {
                for (R r : byConsumedKey.getOrDefault(key, List.of())) {
                    if (fired.contains(r) || !satisfied(r.consumedAlts, reach)) continue;
                    fired.add(r);
                    for (String freeKey : r.freeKeys) reach.add(freeKey);
                    for (String o : r.outKeys) {
                        if (reach.add(o)) {
                            producedBy.put(o, r);
                            next.add(o);
                        }
                    }
                }
            }
            if (satisfied(start.consumedAlts, reach)) return describePath(start, producedBy);
            if (next.isEmpty()) break;
            frontier = next;
        }
        return "";
    }

    private static void queueProducers(List<Set<String>> ingredientAlts, Map<String, R> producedBy,
                                       Deque<String> todo) {
        for (Set<String> alts : ingredientAlts) {
            for (String alt : alts) {
                if (producedBy.containsKey(alt)) {
                    todo.add(alt);
                    break;
                }
            }
        }
    }

    private static String describePath(R start, Map<String, R> producedBy) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        queueProducers(start.consumedAlts, producedBy, todo);
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String key = todo.pop();
            if (!seen.add(key)) continue;
            R r = producedBy.get(key);
            if (r == null) continue; // came from the starting recipe's own outputs or a free item
            ids.add(r.type + ":" + r.id);
            queueProducers(r.consumedAlts, producedBy, todo);
        }
        return String.join(" > ", ids);
    }

    private static Set<String> altsOf(Ingredient ing) {
        Set<String> keys = new TreeSet<>();
        for (ItemStack s : ing.getItems()) keys.add("i:" + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
        return keys;
    }

    private static Set<String> altsOfFluid(FluidIngredient fi) {
        Set<String> keys = new TreeSet<>();
        for (FluidStack s : fi.getStacks()) keys.add("f:" + BuiltInRegistries.FLUID.getKey(s.getFluid()).getPath());
        return keys;
    }

    // ---------- recipe parsing ----------

    private static R parse(GTRecipeType type, GTRecipe recipe) {
        R r = new R();
        r.type = type.registryName.getPath();
        r.id = recipe.id.getPath();
        r.typeFull = type.registryName.toString();
        r.idFull = recipe.id.toString();

        for (Content c : recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            Ingredient ing = ItemRecipeCapability.CAP.of(c.content);
            addAlts(r, ing);
            Held h = heldItem(ing);
            if (h != null && c.chance == 0) {
                r.freeKeys.add(h.key);
                continue;
            }
            if (h == null || h.content == null) {
                r.unknown = true;
                r.unknownKeys.add(h == null ? "?" : h.key);
                continue;
            }
            r.consumed.add(h);
            r.consumedAlts.add(altsOf(ing));
        }
        for (Content c : recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, List.of())) {
            FluidIngredient fi = FluidRecipeCapability.CAP.of(c.content);
            Held h = heldFluid(fi);
            if (h != null && c.chance == 0) {
                r.freeKeys.add(h.key);
                continue;
            }
            // water, distilled water, lubricant and gases are reagents or coolants, not material to conserve
            if (h != null && h.content != null && fi.getStacks().length > 0 &&
                    isReagentFluid(fi.getStacks()[0].getFluid())) {
                r.freeKeys.add(h.key);
                r.reagents.add(h);
                r.reagentAlts.add(altsOfFluid(fi));
                continue;
            }
            if (h == null || h.content == null) {
                r.unknown = true;
                r.unknownKeys.add(h == null ? "?" : h.key);
                continue;
            }
            r.consumed.add(h);
            r.consumedAlts.add(altsOfFluid(fi));
        }
        for (Content c : recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            Held h = heldItem(ItemRecipeCapability.CAP.of(c.content));
            addOutput(r, c, h);
        }
        for (Content c : recipe.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of())) {
            Held h = heldFluid(FluidRecipeCapability.CAP.of(c.content));
            addOutput(r, c, h);
        }

        r.solidFluidOut = DefectiveFlag.hasSolidInLiquidFormOutput(recipe);
        recordFluidClasses(recipe);
        finish(r);
        return r;
    }

    private static final Map<String, Boolean> FLUID_CLASSES = new TreeMap<>();

    private static void recordFluidClasses(GTRecipe recipe) {
        var contents = recipe.outputs.get(FluidRecipeCapability.CAP);
        if (contents == null) return;
        for (Content c : contents) {
            for (FluidStack s : FluidRecipeCapability.CAP.of(c.content).getStacks()) {
                FLUID_CLASSES.put("f:" + BuiltInRegistries.FLUID.getKey(s.getFluid()).getPath(),
                        DefectiveFlag.isSolidInLiquidForm(s.getFluid()));
            }
        }
    }

    private static void finish(R r) {
        // a recipe whose only inputs are reagents (for example electrolysing a gas) counts them after all
        if (r.consumed.isEmpty() && !r.reagents.isEmpty()) {
            r.consumed.addAll(r.reagents);
            r.consumedAlts.addAll(r.reagentAlts);
            // no longer free: it has to be given back for a loop to close
            for (Held h : r.reagents) r.freeKeys.remove(h.key);
        }
        for (Held h : r.consumed) {
            r.consumedKeys.add(h.key);
            add(r.in, h.content, 1);
        }
        for (Held h : r.guaranteed) {
            r.outKeys.add(h.key);
            add(r.out, h.content, 1);
        }
        // chanced outputs are stored pre-scaled by chance, see addOutput
        for (Held h : r.chancedExpected) add(r.outExp, h.content, 1);

        double tin = total(r.in);
        r.ratio = tin == 0 ? 0 : total(r.out) / tin;
        r.conserving = !r.unknown && tin > 0 && !r.guaranteed.isEmpty() && total(r.out) >= RETAIN * tin;
    }

    /** Crafting-table / furnace recipes. Tools and anything with a crafting remainder are treated as not consumed. */
    private static R parseVanilla(net.minecraft.world.item.crafting.Recipe<?> recipe,
                                  net.minecraft.core.RegistryAccess access) {
        R r = new R();
        r.type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).getPath();
        r.id = recipe.getId().getPath();
        r.typeFull = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();
        r.idFull = recipe.getId().toString();
        r.vanillaKind = r.type;
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty() || ing.getItems().length == 0) continue;
            addAlts(r, ing);
            ItemStack s0 = ing.getItems()[0];
            Held h = heldItem(ing);
            if (h != null && (s0.hasCraftingRemainingItem() ||
                    s0.getItem() instanceof com.gregtechceu.gtceu.api.item.IGTTool)) {
                r.freeKeys.add(h.key);
                continue;
            }
            if (h == null || h.content == null) {
                r.unknown = true;
                r.unknownKeys.add(h == null ? "?" : h.key);
                continue;
            }
            r.consumed.add(h);
            r.consumedAlts.add(altsOf(ing));
        }
        ItemStack res = recipe.getResultItem(access);
        if (res.isEmpty()) {
            r.unknown = true;
        } else {
            addOutput(r, new Content(res, 1, 1, 0), heldStack(res.getItem(), res.getCount()));
        }
        finish(r);
        return r;
    }

    private static void addAlts(R r, Ingredient ing) {
        for (ItemStack s : ing.getItems()) r.altKeys.add("i:" + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
    }

    private static void addOutput(R r, Content c, Held h) {
        if (h == null || h.content == null) {
            // an unknown guaranteed output makes the whole recipe uncheckable; unknown chanced ones don't matter
            if (c.chance >= c.maxChance) {
                r.unknown = true;
                r.unknownKeys.add((h == null ? "?" : h.key) + " (out)");
            }
            return;
        }
        if (c.chance >= c.maxChance) {
            r.guaranteed.add(h);
        } else {
            double p = (double) c.chance / c.maxChance;
            Map<String, Double> scaled = new HashMap<>();
            add(scaled, h.content, p);
            r.chancedExpected.add(new Held(h.key, scaled));
        }
    }

    /**
     * Water, distilled water, lubricant and gases are consumed as reagents or coolants, so they are not counted as
     * material a loop has to give back. Acids and solutions are counted: they behave like ingredients.
     */
    private static boolean isReagentFluid(net.minecraft.world.level.material.Fluid fluid) {
        Material material = ChemicalHelper.getMaterial(fluid);
        if (material == null || material.isNull()) return false;
        if (material == com.gregtechceu.gtceu.common.data.GTMaterials.Water ||
                material == com.gregtechceu.gtceu.common.data.GTMaterials.DistilledWater ||
                material == com.gregtechceu.gtceu.common.data.GTMaterials.Lubricant) {
            return true;
        }
        return material.hasProperty(PropertyKey.FLUID) &&
                material.getProperty(PropertyKey.FLUID).get(FluidStorageKeys.GAS) == fluid;
    }

    private static double total(Map<String, Double> m) {
        double t = 0;
        for (double v : m.values()) t += v;
        return t;
    }

    private static void add(Map<String, Double> into, Map<String, Double> from, double scale) {
        from.forEach((k, v) -> into.merge(k, v * scale, Double::sum));
    }

    // ---------- content of items / fluids ----------

    private static Held heldItem(Ingredient ing) {
        ItemStack[] stacks = ing.getItems();
        if (stacks.length == 0) return null;
        int count = ing instanceof SizedIngredient sized ? sized.getAmount() : 1;
        return heldStack(stacks[0].getItem(), count);
    }

    private static Held heldStack(net.minecraft.world.item.Item item, int count) {
        String key = "i:" + BuiltInRegistries.ITEM.getKey(item).getPath();
        Map<String, Double> content = new HashMap<>();
        MaterialStack ms = ChemicalHelper.getMaterialStack(item);
        if (!ms.isEmpty()) {
            add(content, decompose(ms.material()), (double) ms.amount() / com.gregtechceu.gtceu.api.GTValues.M * count);
        } else {
            ItemMaterialInfo info = ChemicalHelper.getMaterialInfo(item);
            if (info == null || info.getMaterials().isEmpty()) return new Held(key, null);
            for (MaterialStack m : info.getMaterials()) {
                add(content, decompose(m.material()),
                        (double) m.amount() / com.gregtechceu.gtceu.api.GTValues.M * count);
            }
        }
        return new Held(key, content);
    }

    private static Held heldFluid(FluidIngredient fi) {
        FluidStack[] stacks = fi.getStacks();
        if (stacks.length == 0) return null;
        var fluid = stacks[0].getFluid();
        String key = "f:" + BuiltInRegistries.FLUID.getKey(fluid).getPath();
        Material m = ChemicalHelper.getMaterial(fluid);
        if (m == null || m.isNull()) return new Held(key, null);
        boolean molten = m.hasProperty(PropertyKey.FLUID) && m.hasProperty(PropertyKey.DUST) &&
                (m.getProperty(PropertyKey.FLUID).get(FluidStorageKeys.MOLTEN) == fluid ||
                        m.getProperty(PropertyKey.FLUID).get(FluidStorageKeys.LIQUID) == fluid);
        double units = fi.getAmount() / (molten ? 144.0 : 1000.0);
        Map<String, Double> content = new HashMap<>();
        add(content, decompose(m), units);
        return new Held(key, content);
    }

    /** Element content of one "unit" (one dust / 1000 mB) of a material. */
    private static Map<String, Double> decompose(Material m) {
        var cached = DECOMP_CACHE.get(m);
        if (cached != null) return cached;
        {
            Material mat = m;
            Map<String, Double> out = new HashMap<>();
            var comps = mat.getMaterialComponents();
            if (comps == null || comps.isEmpty()) {
                out.put(mat.getName(), 1.0);
            } else {
                for (MaterialStack c : comps) add(out, decompose(c.material()), c.amount());
            }
            DECOMP_CACHE.put(m, out);
            return out;
        }
    }
}
