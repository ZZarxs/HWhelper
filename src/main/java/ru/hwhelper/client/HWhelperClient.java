package ru.hwhelper.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.DeltaTracker;

import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import ru.hwhelper.client.standalone.TelegramService;

public final class HWhelperClient implements ClientModInitializer {
    static final Logger LOGGER = LoggerFactory.getLogger("hwhelper");
    private static final int PANEL_WIDTH = 252;
    private static final int HEADER_HEIGHT = 34;
    private int rowHeight() { return config.compact ? 36 : 40; }
    private long appearedAt = System.nanoTime();
    private TelegramService telegram;
    private LocalMediaService media;
    private AnarchySwitcher switcher;
    private volatile List<Mine> mines = List.of();
    private volatile String error = "";
    private volatile long snapshotReceivedAt;
    private HwConfig config;
    private final DynamicIslandHud island = new DynamicIslandHud();
    private final WorldLyrics worldLyrics=new WorldLyrics(island);
    private final Cosmetics cosmetics=new Cosmetics();
    private final java.util.Map<Mine, Long> recentlyUpdated = new java.util.concurrent.ConcurrentHashMap<>();
    private final KeyAlert keyAlert = new KeyAlert();
    private boolean dragging;
    private boolean resizing;
    private double dragOffsetX;
    private double dragOffsetY;
    private double resizeStartX;
    private float resizeStartScale;

    @Override
    public void onInitializeClient() {
        config = HwConfig.load();
        HubSwapLegacyImport.importOnce(config);
        switcher = new AnarchySwitcher(config);
        telegram = new TelegramService(FabricLoader.getInstance().getConfigDir().resolve("hwhelper"), this::acceptSnapshot);
        media = new LocalMediaService(config, island::accept);
        if (config.telegramAutoConnect && telegram.credentialsSaved()) telegram.connectSaved();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { telegram.close(); media.close(); });
        UiStyle.configure(config);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("hwhelper", "key_alert"), (graphics, delta) -> {
                    if (Minecraft.getInstance().screen == null || Minecraft.getInstance().screen instanceof ChatScreen) keyAlert.render(graphics,config);
                });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("hwhelper", "nearby_mines"), this::render);
        KeyMapping settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hwhelper.settings", GLFW.GLFW_KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hwhelper", "settings"))));
        KeyMapping toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.hwhelper.toggle", GLFW.GLFW_KEY_H, settingsKey.getCategory()));
        switcher.register(settingsKey.getCategory());
        GoldenSpawnerInfo.register(config);
        worldLyrics.register(config);
        cosmetics.register(config,settingsKey.getCategory());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            switcher.tick(client);
            keyAlert.tick(client,config);
            cosmetics.tick(client,config);worldLyrics.tick(client,config);
            while (toggleKey.consumeClick()) {
                config.enabled = !config.enabled;
                appearedAt = System.nanoTime();
                config.save();
            }
            while (settingsKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new HWhelperScreen(config, this));
            }
        });
        registerChatDragging();
        island.register(config);
    }
    TelegramService telegram(){return telegram;}
    AnarchySwitcher switcher(){return switcher;}
    String mediaStatus(){return media.status();}
    void showClones(){cosmetics.triggerClones(Minecraft.getInstance(),config);}
    void showRavens(){cosmetics.triggerRavens(Minecraft.getInstance(),config);}

    private void registerChatDragging() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen) && !(screen instanceof HudEditorScreen)) return;

            ScreenMouseEvents.afterMouseClick(screen).register((currentScreen, event, consumed) -> {
                if (!island.contains(config,event.x(),event.y()) && (config.enabled || screen instanceof HudEditorScreen) && !consumed && event.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
                    if (isInsideResizeHandle(event.x(), event.y())) {
                        resizing = true;
                        resizeStartX = event.x();
                        resizeStartScale = config.hudScale;
                    } else if (isInsideHeader(event.x(), event.y())) {
                        dragging = true;
                        dragOffsetX = event.x() - config.hudX;
                        dragOffsetY = event.y() - config.hudY;
                    }
                }
                return false;
            });

            ScreenMouseEvents.afterMouseDrag(screen).register((currentScreen, event, dx, dy, consumed) -> {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1 && dragging) {
                    int panelHeight = measureHeight(getUpcomingMines());
                    int scaledPanelWidth = (int) Math.round(PANEL_WIDTH * config.hudScale);
                    int scaledPanelHeight = (int) Math.round(panelHeight * config.hudScale);
                    config.hudX = clamp((int) Math.round(event.x() - dragOffsetX), 0, scaledWidth - scaledPanelWidth);
                    if (config.snapToGrid) config.hudX = config.hudX / 8 * 8;
                    config.hudY = clamp((int) Math.round(event.y() - dragOffsetY), 0, scaledHeight - scaledPanelHeight);
                    if (config.snapToGrid) config.hudY = config.hudY / 8 * 8;
                } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1 && resizing) {
                    config.hudScale = Math.max(0.65f, Math.min(1.6f,
                            resizeStartScale + (float) ((event.x() - resizeStartX) / PANEL_WIDTH)));
                }
                return false;
            });

            ScreenEvents.remove(screen).register(removed -> {
                if (dragging || resizing) config.save();
                dragging = false;
                resizing = false;
            });
            ScreenMouseEvents.afterMouseRelease(screen).register((currentScreen, event, consumed) -> {
                if ((dragging || resizing) && event.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
                    dragging = false;
                    resizing = false;
                    config.save();
                }
                return false;
            });
        });
    }

    private boolean isInsideHeader(double mouseX, double mouseY) {
        double localX = (mouseX - config.hudX) / config.hudScale;
        double localY = (mouseY - config.hudY) / config.hudScale;
        return localX >= 0 && localX <= PANEL_WIDTH && localY >= 0 && localY <= HEADER_HEIGHT;
    }

    private boolean isInsideResizeHandle(double mouseX, double mouseY) {
        double localX = (mouseX - config.hudX) / config.hudScale;
        double localY = (mouseY - config.hudY) / config.hudScale;
        int panelHeight = measureHeight(getUpcomingMines());
        return localX >= PANEL_WIDTH - 16 && localX <= PANEL_WIDTH
                && localY >= panelHeight - 16 && localY <= panelHeight;
    }

    private boolean acceptSnapshot(String raw, long timestamp) {
        try {
            List<Mine> fresh = MineSnapshotClient.parse(raw);
            long now = System.currentTimeMillis();
            for (Mine old : mines) {
                long deadline = snapshotReceivedAt + old.seconds()*1000L;
                if (deadline <= now && now-deadline <= config.updatedSeconds*1000L) rememberUpdated(old, deadline);
            }
            mines = fresh;
            snapshotReceivedAt = timestamp;
            error = "";
            return true;
        } catch (Exception exception) {
            return false; // Bot greetings and unrelated messages are not snapshots.
        }
    }

    private void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        // Do not enqueue custom graphics inside Lunar's container/blur rendering pass.
        // The HUD editor renders its own preview directly; chat has no world blur pass.
        if (client.screen != null && !(client.screen instanceof ChatScreen)) return;
        if (client.player == null || client.options.hideGui) return;
        island.render(graphics, config, false);
        switcher.render(graphics);
        if (!config.enabled) return;

        renderHud(graphics, false);
    }

    void renderHud(GuiGraphics graphics, boolean preview) {
        Minecraft client = Minecraft.getInstance();
        boolean chatOpen = preview || client.screen instanceof ChatScreen;
        if (preview) island.render(graphics, config, true);
        List<UpcomingMine> upcoming = getUpcomingMines();
        if (preview && upcoming.isEmpty()) upcoming = List.of(
                new UpcomingMine(new Mine("Легендарная Шахта Верхнего мира", "СолоЛайт", 7, 24), 24),
                new UpcomingMine(new Mine("Ад - Эпическая", "ДуоЛайт", 12, 48), 48)).stream().filter(e->matchesMineFilters(e.mine(),this::isCategoryEnabled,config::allowsMineRarity)).toList();
        int availableRows = rowLimit();
        if (upcoming.size() > availableRows) upcoming = upcoming.subList(0, availableRows);
        int panelHeight = measureHeight(upcoming);
        float scale = config.hudScale;
        int scaledWidth = (int) Math.round(PANEL_WIDTH * scale);
        int scaledHeight = (int) Math.round(panelHeight * scale);
        int maxX = Math.max(0, graphics.guiWidth() - scaledWidth);
        int maxY = Math.max(0, graphics.guiHeight() - Math.min(scaledHeight, graphics.guiHeight()));
        config.hudX = clamp(config.hudX, 0, maxX);
        config.hudY = preview ? clamp(config.hudY, 38, maxY - 20) : clamp(config.hudY, 0, maxY);
        graphics.pose().pushMatrix();
        float entrance = config.animations ? Math.min(1f, (System.nanoTime() - appearedAt) / 350_000_000f) : 1f;
        graphics.pose().translate(config.hudX, config.hudY + (1 - entrance) * 10);
        graphics.pose().scale(scale, scale);
        drawPanel(graphics, client, 0, 0, panelHeight, upcoming, chatOpen);
        graphics.pose().popMatrix();
    }

    private void rememberUpdated(Mine mine,long deadline) {
        recentlyUpdated.keySet().removeIf(m->m.category().equals(mine.category()) && m.anarchy().equals(mine.anarchy()) && m.number()==mine.number() && !m.equals(mine));
        recentlyUpdated.put(mine,deadline);
    }

    private List<UpcomingMine> getUpcomingMines() {
        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - snapshotReceivedAt) / 1000);
        long now=System.currentTimeMillis();
        for(Mine mine:mines) {
            long deadline=snapshotReceivedAt+mine.seconds()*1000L;
            if(now>=deadline && now-deadline<=config.updatedSeconds*1000L) rememberUpdated(mine,deadline);
        }
        recentlyUpdated.entrySet().removeIf(e->now-e.getValue()>config.updatedSeconds*1000L);
        List<UpcomingMine> result=new ArrayList<>(selectUpcomingByMine(mines, elapsedSeconds, config.maxEntries, this::isCategoryEnabled, config::allowsMineRarity));
        if(config.keepUpdated) for(var e:recentlyUpdated.entrySet()) {
            Mine m=e.getKey();
            if(matchesMineFilters(m,this::isCategoryEnabled,config::allowsMineRarity))
                result.add(new UpcomingMine(m,-1-(int)((now-e.getValue())/1000)));
        }
        result.sort(Comparator.comparingInt(UpcomingMine::secondsLeft));
        return List.copyOf(result.subList(0,Math.min(config.maxEntries,result.size())));
    }

    static List<UpcomingMine> selectUpcoming(List<Mine> mines, long elapsedSeconds, int maxEntries,
                                             java.util.function.Predicate<String> categoryEnabled) {
        return selectUpcoming(mines,elapsedSeconds,maxEntries,categoryEnabled,rarity -> true);
    }

    static boolean matchesFilters(Mine mine,java.util.function.Predicate<String> categoryEnabled,
                                   java.util.function.Predicate<String> rarityEnabled) {
        return matchesMineFilters(mine,categoryEnabled,m->rarityEnabled.test(m.rarity()));
    }

    static boolean matchesMineFilters(Mine mine,java.util.function.Predicate<String> categoryEnabled,
                                      java.util.function.Predicate<Mine> rarityEnabled) {
        return !(mine.anarchy().equalsIgnoreCase("КланЛайт") && mine.number()>=88 && mine.number()<=90)
                && categoryEnabled.test(mine.type()) && rarityEnabled.test(mine);
    }

    static List<UpcomingMine> selectUpcoming(List<Mine> mines,long elapsedSeconds,int maxEntries,
                    java.util.function.Predicate<String> categoryEnabled,java.util.function.Predicate<String> rarityEnabled) {
        return selectUpcomingByMine(mines,elapsedSeconds,maxEntries,categoryEnabled,m->rarityEnabled.test(m.rarity()));
    }

    static List<UpcomingMine> selectUpcomingByMine(List<Mine> mines,long elapsedSeconds,int maxEntries,
                    java.util.function.Predicate<String> categoryEnabled,java.util.function.Predicate<Mine> rarityEnabled) {
        List<UpcomingMine> upcoming = new ArrayList<>();
        for (Mine mine : mines) {
            int remaining = mine.seconds() - (int) elapsedSeconds;
            if (remaining >= 0 && matchesMineFilters(mine,categoryEnabled,rarityEnabled)) {
                upcoming.add(new UpcomingMine(mine, remaining));
            }
        }
        upcoming.sort(Comparator.comparingInt(UpcomingMine::secondsLeft));
        if (upcoming.size() > maxEntries) return List.copyOf(upcoming.subList(0, maxEntries));
        return List.copyOf(upcoming);
    }

    private boolean isCategoryEnabled(String category) {
        String name = category.toLowerCase(java.util.Locale.ROOT);
        if (name.equals("энд")) return config.showEnd;
        if (name.equals("ад")) return config.showNether;
        return config.showNormal;
    }

    private int rowLimit() {
        Minecraft client = Minecraft.getInstance();
        int space = client.getWindow().getGuiScaledHeight() - (client.screen instanceof HudEditorScreen ? 58 : 0);
        return Math.max(1, ((int)(space / config.hudScale) - 74) / rowHeight());
    }

    private int measureHeight(List<UpcomingMine> upcoming) {
        int count = upcoming.isEmpty() && Minecraft.getInstance().screen instanceof HudEditorScreen ? 2 : upcoming.size();
        int rows = Math.min(count, rowLimit());
        return 70 + Math.max(1, rows) * rowHeight();
    }

    private void drawPanel(GuiGraphics g, Minecraft client, int x, int y, int height,
                           List<UpcomingMine> upcoming, boolean editing) {
        int accent = config.accent();
        // Glass surfaces use ordinary GUI geometry; never request a second full-screen blur from the HUD.
        UiStyle.glass(g, 0, 0, PANEL_WIDTH, height, accent, 0, true, config.opacity * 255 / 100);
        int reflectionX = config.animations ? (int)(PANEL_WIDTH * (.5 + .3 * Math.sin(System.nanoTime() / 2_000_000_000.0))) : PANEL_WIDTH / 2;
        UiStyle.glint(g, 0, 1, PANEL_WIDTH, reflectionX, 0xFFE6F8FF, .85f);
        UiStyle.shape(g,10,11,24,24,6,0xFF33232C);
        UiStyle.icon(g,"mines",15,16,14,accent);
        g.drawString(client.font,UiStyle.hudText("Автошахты"),42,12,UiStyle.WHITE,true);
        String status = snapshotReceivedAt > 0 && System.currentTimeMillis()-snapshotReceivedAt < 20000 && telegram != null && telegram.ready() ? "LIVE" : "ОЖИДАНИЕ";
        var statusText=UiStyle.text(status,8);int statusWidth=client.font.width(statusText);
        UiStyle.shape(g,PANEL_WIDTH-20-statusWidth,11,statusWidth+10,16,4,0xFF292229);
        g.drawString(client.font,statusText,PANEL_WIDTH-15-statusWidth,15,accent,true);
        g.drawString(client.font,UiStyle.text(editing ? "Тяни заголовок или нижний угол" : "Ближайшие события",8),42,29,UiStyle.MUTED,true);
        g.fill(12,42,PANEL_WIDTH-12,43,UiStyle.EDGE);
        int cy = 46;
        if (upcoming.isEmpty()) g.drawString(client.font,UiStyle.hudText(telegram != null && !telegram.ready() ? "Telegram: M → Связь" : "Нет доступных шахт"), 12, cy + 8, UiStyle.MUTED, true);
        for (int i = 0; i < upcoming.size(); i++) {
            UpcomingMine entry = upcoming.get(i);
            Mine mine = entry.mine();
            int h = rowHeight();
            UiStyle.glass(g, 8, cy, PANEL_WIDTH - 16, h - 4, mine.rarityColor(), i == 0 ? 1 : 0,
                    false, (i == 0 ? 240 : 210) * config.opacity / 100);
            if (i == 0) UiStyle.glint(g, 8, cy + 1, PANEL_WIDTH - 16, reflectionX, accent, .75f);
            UiStyle.shape(g,11,cy+8,2,h-20,1,mine.rarityColor());
            String name = mine.anarchy() + " #" + mine.number();
            g.drawString(client.font,UiStyle.hudText(name), 17, cy + 5, UiStyle.WHITE, true);
            String timer = entry.secondsLeft() < 0 ? "Обновилась" : entry.secondsLeft() >= 100 ? entry.secondsLeft()/60 + ":" + String.format("%02d", entry.secondsLeft()%60) : entry.secondsLeft() + " сек";
            // Measure the exact styled component being drawn, including the denser HUD font.
            var timerText=UiStyle.hudText(timer);
            int timerRight=PANEL_WIDTH-17;
            int timerX=timerRight-client.font.width(timerText);
            g.drawString(client.font,timerText,timerX,cy+5,
                    entry.secondsLeft() <= 20 ? 0xFFFF8FB9 : UiStyle.TEXT,true);
            String typeLabel = mine.type().equals("Обычные") ? "Обычная" : mine.type();
            g.drawString(client.font,UiStyle.hudText(typeLabel + (config.showRarity ? " / " + mine.rarity() : "")),
                    17, cy + (config.compact ? 16 : 18), mine.rarityColor(), true);
            if (config.showProgress) {
                double seconds = entry.secondsLeft();
                if (config.animations && snapshotReceivedAt > 0) seconds -= (System.currentTimeMillis() - snapshotReceivedAt) % 1000 / 1000.0;
                int bar = (int)(Math.max(0, Math.min(90, seconds)) / 90 * (PANEL_WIDTH - 34));
                UiStyle.shape(g,17,cy+h-7,PANEL_WIDTH-34,2,1,0xFF393137);
                if(bar>0)UiStyle.shape(g,17,cy+h-7,bar,2,1,accent);
            }
            cy += h;
        }
        g.drawString(client.font,UiStyle.text(editing ? "Размер: тяни за нижний угол" : formatUpdatedAt(),9),12,height-16,UiStyle.MUTED,true);
        if (editing) { g.fill(PANEL_WIDTH-13, height-5, PANEL_WIDTH-4, height-3, accent); g.fill(PANEL_WIDTH-6, height-12, PANEL_WIDTH-4, height-3, accent); }
    }

    private String formatUpdatedAt() {
        if (snapshotReceivedAt == 0) return "ожидаю снимок";
        long secondsAgo = Math.max(0, (System.currentTimeMillis() - snapshotReceivedAt) / 1000);
        return "снимок " + secondsAgo + " сек назад";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    record UpcomingMine(Mine mine, int secondsLeft) {}
}
