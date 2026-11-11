package ru.hwhelper.client;
import java.util.Locale;
public record Mine(String category, String anarchy, int number, int seconds) {
    public String type() {
        String s = category.toLowerCase(Locale.ROOT).strip();
        if (s.startsWith("ад")) return "Ад";
        if (s.startsWith("энд") || s.startsWith("эндер")) return "Энд";
        return "Обычные";
    }
    public String rarity() {
        String s = category.toLowerCase(Locale.ROOT);
        if (s.contains("легендар")) return "Легендарная";
        if (s.contains("эпичес")) return "Эпическая";
        if (s.contains("редк")) return "Редкая";
        if (s.contains("обычн")) return "Обычная";
        return "Не указана";
    }
    public int rarityColor() {
        return switch (rarity()) {
            case "Легендарная" -> 0xFFFFC66D;
            case "Эпическая" -> 0xFFB69AFF;
            case "Редкая" -> 0xFF70BFFF;
            default -> 0xFF9DAFC4;
        };
    }
}
