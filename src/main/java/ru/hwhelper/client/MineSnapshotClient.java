package ru.hwhelper.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MineSnapshotClient {
    private static final Pattern ENTRY = Pattern.compile("((?:[\\p{L}0-9_-]+Лайт|Лайт)(?:\\s*\\(\\d+\\.\\d+(?:\\.\\d+)?\\))?)\\s*#(\\d+)\\s*\\(?\\s*(\\d+)\\s*(?:сек(?:унд[аы]?)?|с|s)\\.?\\s*\\)?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    static List<Mine> parseResponse(String body,long now) {
        List<Mine> parsed=parse(extractText(body));
        long age=0;
        try {
            var root=JsonParser.parseString(body).getAsJsonObject();
            if(root.has("updatedAt")) age=Math.max(0,(now-java.time.OffsetDateTime.parse(root.get("updatedAt").getAsString()).toInstant().toEpochMilli())/1000);
        } catch(RuntimeException ignored) { }
        final long seconds=Math.min(Integer.MAX_VALUE,age);
        return parsed.stream().map(m->new Mine(m.category(),m.anarchy(),m.number(),(int)Math.max(Integer.MIN_VALUE,(long)m.seconds()-seconds))).toList();
    }

    private static String extractText(String body) {
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("\"")) return body;
        JsonElement root;
        try {
            root = JsonParser.parseString(trimmed);
        } catch (RuntimeException notJson) {
            return body;
        }
        if (root.isJsonPrimitive()) return root.getAsString();
        if (!root.isJsonObject()) throw new IllegalArgumentException("Snapshot JSON must contain a text or message field");
        JsonObject object = root.getAsJsonObject();
        for (String key : List.of("text", "message", "snapshot")) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) return value.getAsString();
        }
        throw new IllegalArgumentException("Snapshot JSON must contain a text or message field");
    }

    static List<Mine> parse(String text) {
        List<Mine> result = new ArrayList<>();
        String category = "Шахты";
        for (String line : text.split("\\R")) {
            String cleanLine = line.replace("\u201c", "").replace("\u201d", "").trim();
            if (cleanLine.matches("(?iu)(?:(?:ад|энд)\\s*[-–—]\\s*)?(?:обычная|эпическая|легендарная|редкая)\\s*:?")) {
                category = cleanLine.replaceFirst(":$", "").trim();
                continue;
            }
            int colon = cleanLine.indexOf(':');
            if (colon >= 0) {
                category = cleanLine.substring(0, colon).trim();
                cleanLine = cleanLine.substring(colon + 1).trim();
            }
            if (cleanLine.endsWith(":")) {
                category = cleanLine.substring(0, cleanLine.length() - 1).trim();
                continue;
            }
            Matcher matcher = ENTRY.matcher(cleanLine);
            while (matcher.find()) {
                result.add(new Mine(category, matcher.group(1).trim(), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("В снимке не распознаны строки шахт: СолоЛайт #7 (131с.)");
        return List.copyOf(result);
    }
}
