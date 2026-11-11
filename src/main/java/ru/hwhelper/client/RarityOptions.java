package ru.hwhelper.client;

/** Independent rarity choices for one dimension. */
public final class RarityOptions {
    public boolean common=true;
    public boolean rare=true;
    public boolean epic=true;
    public boolean legendary=true;
    public boolean unknown=true;

    public boolean allows(String rarity) {
        return switch(rarity) {
            case "Обычная" -> common;
            case "Редкая" -> rare;
            case "Эпическая" -> epic;
            case "Легендарная" -> legendary;
            default -> unknown;
        };
    }

    public static RarityOptions fromLegacy(boolean common,boolean rare,boolean epic,boolean legendary,boolean unknown) {
        RarityOptions result=new RarityOptions();
        result.common=common;result.rare=rare;result.epic=epic;
        result.legendary=legendary;result.unknown=unknown;
        return result;
    }
}
