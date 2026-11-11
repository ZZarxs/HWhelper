package ru.hwhelper.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HwConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hwhelper.json");
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("mines-hud.json");

    public boolean telegramAutoConnect = true;
    public int maxEntries = 20;
    public boolean enabled = true;
    public boolean keyAlertEnabled = true;
    public boolean spawnerEnabled=true;
    public String keySoundFile="";
    public float keySoundVolume=1;
    public CosmeticOptions cosmetics=new CosmeticOptions();
    public WorldLyricOptions worldLyrics=new WorldLyricOptions();
    public boolean liquidGlass = false;
    public boolean keepUpdated = true;
    public boolean musicEnabled = true;
    public boolean islandInitialized = false;
    public boolean islandCentered = true;
    public boolean islandEqualizer = true;
    public boolean islandStatus = true;
    public boolean islandTrackClock = false;
    public boolean islandAlwaysShow = false;
    public int islandHeight = 30;
    public boolean musicSnapToGrid = false;
    public int musicX = 280, musicY = 40;
    public int musicWidth = 260;
    public int musicHeight = 108;
    public int musicOpacity = 90;
    public float musicScale = 1f;
    public boolean musicCover = true, musicLyrics = true, musicProgress = true, musicArtist = true;
    public boolean musicGlow = true, musicHideIdle = false;
    public int updatedSeconds = 30;
    public boolean animations = true;
    public java.util.List<String> favoriteModules = new java.util.ArrayList<>();
    public boolean compact = false;
    public boolean showRarity = true;
    public boolean showCommonRarity = true;
    public boolean showRareRarity = true;
    public boolean showEpicRarity = true;
    public boolean showLegendaryRarity = true;
    public boolean showUnknownRarity = true;
    // Nullable fields allow existing global preferences to migrate once per dimension.
    public RarityOptions overworldRarities;
    public RarityOptions netherRarities;
    public RarityOptions endRarities;

    private RarityOptions legacyRarities() {
        return RarityOptions.fromLegacy(showCommonRarity,showRareRarity,showEpicRarity,showLegendaryRarity,showUnknownRarity);
    }

    public RarityOptions rarityOptions(String type) {
        if(type.equals("Ад")) {
            if(netherRarities==null)netherRarities=legacyRarities();
            return netherRarities;
        }
        if(type.equals("Энд")) {
            if(endRarities==null)endRarities=legacyRarities();
            return endRarities;
        }
        if(overworldRarities==null)overworldRarities=legacyRarities();
        return overworldRarities;
    }

    public boolean allowsMineRarity(Mine mine) {
        return rarityOptions(mine.type()).allows(mine.rarity());
    }
    public boolean showProgress = true;
    public boolean snapToGrid = true;
    public int opacity = 92;
    public int theme = 0;
    public int accent() { return new int[]{0xFFFF8FB9, 0xFFB9A5FF, 0xFFE9BD83, 0xFFFFBBD4, 0xFF97CEE6, 0xFFB9DCA2}[Math.floorMod(theme, 6)]; }
    public boolean showNormal = true;
    public boolean showNether = true;
    public boolean showEnd = true;
    public int hudX = 12;
    public int hudY = 36;
    public float hudScale = 1.0f;

    public static HwConfig load() {
        Path source=Files.exists(FILE)?FILE:LEGACY_FILE;
        if (!Files.exists(source)) {
            HwConfig config = new HwConfig().sanitize();
            config.save();
            return config;
        }
        try {
            HwConfig loaded = GSON.fromJson(Files.readString(source), HwConfig.class);
            HwConfig config=loaded == null ? new HwConfig().sanitize() : loaded.sanitize();
            if(source.equals(LEGACY_FILE))config.save();
            return config;
        } catch (Exception exception) {
            HWhelperClient.LOGGER.error("Cannot read {}. Using defaults.", FILE, exception);
            return new HwConfig().sanitize();
        }
    }

    private HwConfig sanitize() {
        if(cosmetics==null)cosmetics=new CosmeticOptions();cosmetics.sanitize();
        if(worldLyrics==null)worldLyrics=new WorldLyricOptions();worldLyrics.sanitize();
        if(keySoundFile==null)keySoundFile="";
        keySoundVolume=Float.isFinite(keySoundVolume)?Math.max(0,Math.min(1,keySoundVolume)):1;
        if(favoriteModules==null)favoriteModules=new java.util.ArrayList<>();
        if(!islandInitialized) {
            islandInitialized=true;islandCentered=true;musicY=6;islandHeight=30;liquidGlass=true;musicHideIdle=false;
        }
        islandHeight=Math.max(22,Math.min(44,islandHeight));
        rarityOptions("Обычные");rarityOptions("Ад");rarityOptions("Энд");
        musicHeight=Math.max(100,Math.min(180,musicHeight));
        musicOpacity=Math.max(30,Math.min(100,musicOpacity));
        musicScale=Float.isFinite(musicScale)?Math.max(.65f,Math.min(1.6f,musicScale)):1;
        updatedSeconds=Math.max(10,Math.min(60,updatedSeconds));
        musicWidth=Math.max(140,Math.min(420,musicWidth));
        opacity = Math.max(30, Math.min(100, opacity));
        maxEntries = Math.max(1, Math.min(maxEntries, 40));
        hudX = Math.max(0, hudX);
        hudY = Math.max(0, hudY);
        hudScale = Float.isFinite(hudScale) ? Math.max(0.65f, Math.min(hudScale, 1.6f)) : 1.0f;
        return this;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException exception) {
            HWhelperClient.LOGGER.error("Cannot write {}.", FILE, exception);
        }
    }
}
