package com.gregtechceu.gtceu.data.loader;

import com.gregtechceu.gtceu.api.recipe.LoopRecipeList;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Reloads {@link LoopRecipeList} whenever server data is reloaded. */
public class LoopRecipeListLoader implements ResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        LoopRecipeList.load(resourceManager);
    }
}
