package dev.jeefwynn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

final class WynntilsState {
    record Snapshot(int mana, String className, String[] spellNames, int[] costs) {}

    private static final int[][] DEFAULT_COSTS = {
            {6, 3, 8, 8},  // Archer
            {6, 2, 8, 8},  // Assassin
            {6, 4, 9, 7},  // Warrior
            {8, 4, 8, 6},  // Mage
            {4, 3, 8, 6},  // Shaman
    };
    private static final String[][] SPELL_NAMES = {
            {"Arrow Storm", "Escape", "Arrow Bomb", "Arrow Shield"},
            {"Spin Attack", "Dash", "Multihit", "Smoke Bomb"},
            {"Bash", "Charge", "Uppercut", "War Scream"},
            {"Heal", "Teleport", "Meteor", "Ice Snake"},
            {"Totem", "Haul", "Aura", "Uproot"},
    };

    private final int[][] learnedCosts = new int[5][4];
    private Object characterStats;
    private Object character;
    private Object spell;
    private Method getMana;
    private Method getClassType;
    private Method getLastSpellName;
    private Method getLastSpellManaCost;
    private Method cappedCurrent;
    private String lastLearnedSpell = "";
    private int lastLearnedCost = -1;
    private boolean initialized;

    Snapshot snapshot() {
        if (!initialized && !initialize()) return null;
        try {
            Optional<?> manaValue = (Optional<?>) getMana.invoke(characterStats);
            if (manaValue.isEmpty()) return null;
            int mana = ((Number) cappedCurrent.invoke(manaValue.get())).intValue();
            String className = ((Enum<?>) getClassType.invoke(character)).name();
            int classIndex = classIndex(className);
            if (classIndex < 0) return null;
            learnLatestCost(classIndex);

            int[] costs = DEFAULT_COSTS[classIndex].clone();
            for (int i = 0; i < costs.length; i++) {
                if (learnedCosts[classIndex][i] > 0) costs[i] = learnedCosts[classIndex][i];
            }
            return new Snapshot(mana, titleCase(className), SPELL_NAMES[classIndex].clone(), costs);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            initialized = false;
            return null;
        }
    }

    private boolean initialize() {
        try {
            Class<?> models = Class.forName("com.wynntils.core.components.Models");
            characterStats = publicField(models, "CharacterStats").get(null);
            character = publicField(models, "Character").get(null);
            spell = publicField(models, "Spell").get(null);
            getMana = characterStats.getClass().getMethod("getMana");
            getClassType = character.getClass().getMethod("getClassType");
            getLastSpellName = spell.getClass().getMethod("getLastSpellName");
            getLastSpellManaCost = spell.getClass().getMethod("getLastSpellManaCost");

            Optional<?> currentMana = (Optional<?>) getMana.invoke(characterStats);
            if (currentMana.isPresent()) {
                cappedCurrent = currentMana.get().getClass().getMethod("current");
            } else {
                Class<?> capped = Class.forName("com.wynntils.utils.type.CappedValue");
                cappedCurrent = capped.getMethod("current");
            }
            initialized = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private void learnLatestCost(int classIndex) throws ReflectiveOperationException {
        String name = (String) getLastSpellName.invoke(spell);
        int cost = ((Number) getLastSpellManaCost.invoke(spell)).intValue();
        if (name.isBlank() || cost <= 0 || (name.equals(lastLearnedSpell) && cost == lastLearnedCost)) return;
        for (int i = 0; i < SPELL_NAMES[classIndex].length; i++) {
            if (name.startsWith(SPELL_NAMES[classIndex][i])) {
                learnedCosts[classIndex][i] = cost;
                lastLearnedSpell = name;
                lastLearnedCost = cost;
                return;
            }
        }
    }

    private static Field publicField(Class<?> type, String name) throws NoSuchFieldException {
        return type.getField(name);
    }

    private static int classIndex(String className) {
        return switch (className) {
            case "ARCHER" -> 0;
            case "ASSASSIN" -> 1;
            case "WARRIOR" -> 2;
            case "MAGE" -> 3;
            case "SHAMAN" -> 4;
            default -> -1;
        };
    }

    private static String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
