package ru.hwhelper.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GoldenSpawnerInfo {
    static void register(HwConfig config) {
        ItemTooltipCallback.EVENT.register((stack,context,type,lines)->{
            if(config.spawnerEnabled)appendSpawnerData(stack,context,type,lines);
        });
    }

    private static void appendSpawnerData(ItemStack stack, Item.TooltipContext context,
                                          net.minecraft.world.item.TooltipFlag type, List<Component> lines) {
        if (!stack.is(Items.SPAWNER)) return;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("gs_marker") && !tag.contains("gs_mob_type")) return;

        lines.add(Component.empty());
        lines.add(styled("HWhelper / Золотой спавнер",UiStyle.accent(),true));
        lines.add(styled("────────────────────────",UiStyle.EDGE,false));
        lines.add(infoLine("Моб", mobName(tag.getStringOr("gs_mob_type", "UNKNOWN")), ChatFormatting.AQUA));
        lines.add(infoLine("Яиц вставлено", number(tag.getIntOr("gs_total_eggs", -1)), ChatFormatting.YELLOW));
        lines.add(infoLine("Нафармлено", number(tag.getIntOr("gs_kills_total", -1)), ChatFormatting.GREEN));
        lines.add(infoLine("Осталось мобов", number(tag.getIntOr("gs_kills_remaining", -1)), ChatFormatting.LIGHT_PURPLE));
        lines.addAll(swordLines(tag.getStringOr("gs_sword_item", "")));
    }

    private static Component styled(String text,int color,boolean bold) {
        return UiStyle.text(text,10).copy().withStyle(style->style.withColor(color&0xFFFFFF).withBold(bold));
    }
    private static Component infoLine(String label,String value,ChatFormatting ignored) {
        return styled("  "+label+"  ",UiStyle.MUTED,false).copy().append(styled(value,UiStyle.WHITE,true));
    }

    static List<Component> swordLines(String raw) {
        List<Component> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            lines.add(styled("СНАРЯЖЕНИЕ",UiStyle.accent(),true));
            lines.add(styled("  Не установлен",UiStyle.MUTED,false));
            return lines;
        }
        try {
            JsonObject sword = JsonParser.parseString(raw).getAsJsonObject();
            String type = sword.has("type") ? sword.get("type").getAsString() : "меч";
            String path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
            String itemName = Component.translatable("item.minecraft." + path).getString();
            String customName = sword.has("name") ? plainText(sword.get("name").getAsString()) : "";
            lines.add(styled("СНАРЯЖЕНИЕ",UiStyle.accent(),true));
            lines.add(styled("  "+(customName.isBlank()?itemName:customName),UiStyle.WHITE,true));

            JsonElement enchantments = sword.get("enchants");
            if (enchantments != null && enchantments.isJsonObject()) {
                lines.add(styled("  Зачарования",UiStyle.MUTED,false));
                for (Map.Entry<String, JsonElement> enchantment : enchantments.getAsJsonObject().entrySet()) {
                    int level = enchantment.getValue().getAsInt();
                    lines.add(styled("   • "+enchantmentName(enchantment.getKey()),UiStyle.TEXT,false)
                            .copy().append(styled(" "+roman(level),UiStyle.accent(),true)));
                }
            }
        } catch (RuntimeException ignored) {
            lines.add(styled("СНАРЯЖЕНИЕ",UiStyle.accent(),true));
            lines.add(styled("  Данные меча есть",UiStyle.MUTED,false));
        }
        return lines;
    }

    private static String plainText(String text) {
        return text.replaceAll("§.", "").trim();
    }

    private static String enchantmentName(String id) {
        return switch (id) {
            case "enchantments:rich-enchant-custom" -> "Богач";
            case "enchantments:critical-enchant-custom" -> "Критический";
            case "minecraft:bane_of_arthropods" -> "Бич членистоногих";
            case "minecraft:fire_aspect" -> "Заговор огня";
            case "minecraft:looting" -> "Добыча";
            case "minecraft:mending" -> "Починка";
            case "minecraft:sharpness" -> "Острота";
            case "minecraft:smite" -> "Небесная кара";
            case "minecraft:sweeping" -> "Разящий клинок";
            case "minecraft:unbreaking" -> "Прочность";
            default -> id.substring(id.indexOf(':') + 1).replace('_', ' ');
        };
    }

    static String roman(int value) {
        if (value <= 0 || value > 3999) return Integer.toString(value);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (value >= values[i]) {
                result.append(numerals[i]);
                value -= values[i];
            }
        }
        return result.toString();
    }

    private static String mobName(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("UNKNOWN")) return "неизвестно";
        String id = raw.toLowerCase(Locale.ROOT).contains(":") ? raw.toLowerCase(Locale.ROOT)
                : "minecraft:" + raw.toLowerCase(Locale.ROOT);
        return BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(id))
                .map(type -> type.value().getDescription().getString())
                .orElse(raw);
    }

    static String number(int value) {
        return value < 0 ? "нет данных" : String.format(Locale.forLanguageTag("ru-RU"), "%,d", value);
    }
}
