<p align="center"><img src="https://raw.githubusercontent.com/GregTechCEu/Branding/refs/heads/master/gregtech_ceu_modern_logo_large_modern.png" alt="Logo"></p>
<h1 align="center">GregTech CEu: Modern</h1>
<p align="center">GregTech:CEu for modern Minecraft versions MinecraftForge (1.20.1) & NeoForge (1.21.1+).</p>
<h1 align="center">
    <a href="https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern"><img src="https://img.shields.io/badge/Available%20for-MC%201.20.1+%20-informational?style=for-the-badge" alt="Supported Versions"></a>
    <a href="https://github.com/GregTechCEu/GregTech-Modern/blob/1.20.1/LICENSE"><img src="https://img.shields.io/github/license/GregTechCEu/GregTech?style=for-the-badge" alt="License"></a>
    <a href="https://discord.gg/bWSWuYvURP"><img src="https://img.shields.io/discord/701354865217110096?color=5464ec&label=Discord&style=for-the-badge" alt="Discord"></a>
    <br>
    <a href="https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern"><img src="https://cf.way2muchnoise.eu/890405.svg?badge_style=for_the_badge" alt="CurseForge"></a>
    <a href="https://modrinth.com/mod/gregtechceu-modern"><img src="https://img.shields.io/modrinth/dt/gregtechceu-modern?logo=modrinth&label=&suffix=%20&style=for-the-badge&color=2d2d2d&labelColor=5ca424&logoColor=1c1c1c" alt="Modrinth"></a>
    <a href="https://github.com/GregTechCEu/GregTech-Modern/releases"><img src="https://img.shields.io/github/downloads/GregTechCEu/GregTech-Modern/total?sort=semver&logo=github&label=&style=for-the-badge&color=2d2d2d&labelColor=545454&logoColor=FFFFFF" alt="GitHub"></a>
</h1>

### [Wiki](https://gregtechceu.github.io/GregTech-Modern/)

## Developers

To add GTCEu: Modern (GTM) to your project as a dependency, add the following to your `build.gradle`:
```groovy
repositories {
    maven {
        name = 'GTCEu Maven'
        url = 'https://maven.gtceu.com'
        content {
            includeGroup 'com.gregtechceu.gtceu'
        }
    }
}
```
Then, you can add it as a dependency, with `${mc_version}` being your Minecraft version target and `${gtm_version}` being the version of GTM you want to use.
```groovy
dependencies {
	// Forge (see below block as well if you use Forge Gradle)
	implementation fg.deobf("com.gregtechceu.gtceu:gtceu-${mc_version}:${gtm_version}")

	// NeoForge
	implementation "com.gregtechceu.gtceu:gtceu-${mc_version}:${gtm_version}"

	// Architectury
	modImplementation "com.gregtechceu.gtceu:gtceu-${mc_version}:${gtm_version}"
}
```

### IDE Requirements (when using IntelliJ IDEA)

For contributing to this mod, the [Lombok plugin](https://plugins.jetbrains.com/plugin/6317-lombok) for IntelliJ IDEA is strictly required.  
Additionally, the [Minecraft Development plugin](https://plugins.jetbrains.com/plugin/8327-minecraft-development) is recommended.


### Loop list (`data/gtceu/loop_recipes.json`)

Some recipes form a material loop: their output can be turned back into their input by other recipes (for example ingot, wiremill, wire, macerator, dust, furnace, ingot). A free bonus output on such a recipe would be free material every lap, so Power Distribution delivers the bonus on these recipes as a *defective* item (it works as an ingredient but cannot be recycled).

`src/main/resources/data/gtceu/loop_recipes.json` is the list of those recipes, grouped by recipe type. **It is generated, do not edit it by hand.** It is produced by the loop audit, a game test (`OutputLoopAuditTest`) that reads every recipe, converts items and fluids to element content, and looks for recipes whose outputs can be turned back into their inputs through other recipes (up to 4 steps, where a step counts if it keeps at least half the material it consumes; water, distilled water, lubricant and gases are not counted as consumed material).

To regenerate it after changing recipes, material data or the default recycling yields:
```
./gradlew regenerateLoopList
```
This runs the game test server once with the audit switched on and rewrites the file. The output is sorted and carries no timestamps, so regenerating with nothing changed leaves no diff. The `_meta` block at the top of the file records the thresholds and recycling yields it was generated with. Other files from the same run are written to `run/gametest/gt_audit/` (per-recipe results, the fluid classification, and the reasons for unknown recipes).

Things to know:
- The game logs `Loop list: N entries loaded, M stale` at server start. A non-zero stale count means the file names recipes that no longer exist, so regenerate it.
- A recipe that is not in the file is treated as not being a loop and gets an ordinary bonus. The audit does not look at crafting-table recipes, vanilla blasting recipes or the ore-processing chain (assumed safe).
- The list is generated in dev mode, which forces `generateLowQualityGems` on, so it includes the chipped and flawed gem recipes (`engrave_*_gem_to_flawed_gem` and similar) that a default install does not have. They show up as stale entries (about 70) and are harmless. The `_meta` block records the value used.
- The file is an ordinary data resource, so a datapack can replace it.

## Credited Works
- Most textures are originally from [Gregtech: Refreshed](https://modrinth.com/resourcepack/gregtech-refreshed) by @ULSTICK. With some consistency edits and additions by @Ghostipedia.
- Some textures are originally from the **[ZedTech GTCEu Resourcepack](https://github.com/brachy84/zedtech-ceu)**, with some changes made by the community.
- New material item textures by @TTFTCUTS and @Rosethorns.
- Wooden Forms, World Accelerators, and the Extreme Combustion Engine are from the **[GregTech: New Horizons Modpack](https://www.curseforge.com/minecraft/modpacks/gt-new-horizons)**.
- Primitive Water Pump is from the **[IMPACT: GREGTECH EDITION Modpack](https://gt-impact.github.io/#/)**.
- Ender Fluid Link Cover, Auto-Maintenance Hatch, Optical Fiber, and Data Bank Textures are from **[TecTech](https://github.com/Technus/TecTech)**.
- Steam Grinder is from **[GregTech++](https://www.curseforge.com/minecraft/mc-mods/gregtech-gt-gtplusplus)**.
- Certificate of Not Being a Noob Anymore is from **[Crops++](https://www.curseforge.com/minecraft/mc-mods/berries)**.

See something we forgot to credit? Reach out to us on Discord, or open an issue and ask for appropriate credit, we will happily mark it here
