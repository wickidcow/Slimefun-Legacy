package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Server-independent text helpers used by the Slimefun item doctor. */
public final class ItemDoctorText {

    private static final Pattern DYNAMIC_TOKEN = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|(?<!§)[+-]?\\d+(?:[.,]\\d+)?");
    private static final Pattern LEGACY_COLOR_CODE = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern LEGACY_CHARGE =
            Pattern.compile("(?i)(?<!§)([+-]?\\d+(?:[.,]\\d+)?)\\s*/\\s*([+-]?\\d+(?:[.,]\\d+)?)\\s*J");
    private static final Pattern LEGACY_USES_LEFT =
            Pattern.compile("(?i)(?:uses?\\s+left|remaining\\s+uses?|\u5269\u4F59(?:\u4F7F\u7528)?\u6B21\u6570"
                    + "|\u5269\u9918(?:\u4F7F\u7528)?\u6B21\u6578)"
                    + "\\s*[:：]?\\s*(?<!§)([+-]?\\d+)");

    private ItemDoctorText() {}

    public static boolean containsCjk(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (isCjkCodePoint(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    public static boolean containsCjk(@Nullable List<String> lines) {
        if (lines == null) {
            return false;
        }

        for (String line : lines) {
            if (containsCjk(line)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces CJK presentation lines with the registered English template while retaining
     * every non-CJK line in its original slot. This is deliberately position-preserving
     * because legacy items and third-party addons may keep hidden state in lore.
     */
    public static @Nonnull List<String> mergeEnglishLore(
            @Nullable List<String> currentLore, @Nullable List<String> canonicalLore) {
        List<String> canonical = canonicalLore == null ? List.of() : canonicalLore;
        if (currentLore == null || currentLore.isEmpty()) {
            return new ArrayList<>(canonical);
        }

        List<String> result = new ArrayList<>(currentLore.size() + canonical.size());
        List<Boolean> removablePlaceholders = new ArrayList<>(currentLore.size() + canonical.size());
        for (int i = 0; i < currentLore.size(); i++) {
            String currentLine = currentLore.get(i);
            if (!containsCjk(currentLine)) {
                result.add(currentLine);
                removablePlaceholders.add(false);
            } else if (i < canonical.size()) {
                result.add(carryDynamicTokens(currentLine, canonical.get(i)));
                removablePlaceholders.add(false);
            } else {
                // Keep later hidden/state lines at their original indexes. A blank placeholder
                // is safer than moving legacy lore-backed data to a different slot.
                result.add("");
                removablePlaceholders.add(true);
            }
        }

        // A translated template may contain more lines than the stored legacy item.
        // Append only missing lines so hidden or dynamic lines never change position.
        for (String canonicalLine : canonical) {
            if (!normalize(canonicalLine).isEmpty() && !containsEquivalent(result, canonicalLine)) {
                result.add(canonicalLine);
                removablePlaceholders.add(false);
            }
        }

        while (!result.isEmpty() && removablePlaceholders.get(removablePlaceholders.size() - 1)) {
            int lastIndex = result.size() - 1;
            result.remove(lastIndex);
            removablePlaceholders.remove(lastIndex);
        }
        return result;
    }

    /**
     * Replaces translated presentation lines with the canonical template without carrying
     * numeric tokens from the translated text. This is intended only when the caller has
     * authoritative knowledge that any dynamic presentation state can be restored afterwards.
     * Non-CJK lines are retained so hidden or addon-owned state outside translated lines is not
     * discarded.
     */
    static @Nonnull List<String> mergeStaticEnglishLore(
            @Nullable List<String> currentLore, @Nullable List<String> canonicalLore) {
        List<String> canonical = canonicalLore == null ? List.of() : canonicalLore;
        if (currentLore == null || currentLore.isEmpty()) {
            return new ArrayList<>(canonical);
        }

        List<String> result = new ArrayList<>(currentLore.size() + canonical.size());
        List<Boolean> removablePlaceholders = new ArrayList<>(currentLore.size() + canonical.size());
        for (int i = 0; i < currentLore.size(); i++) {
            String currentLine = currentLore.get(i);
            if (!containsCjk(currentLine)) {
                result.add(currentLine);
                removablePlaceholders.add(false);
            } else if (i < canonical.size()) {
                result.add(canonical.get(i));
                removablePlaceholders.add(false);
            } else {
                result.add("");
                removablePlaceholders.add(true);
            }
        }

        for (String canonicalLine : canonical) {
            if (!normalize(canonicalLine).isEmpty() && !containsEquivalent(result, canonicalLine)) {
                result.add(canonicalLine);
                removablePlaceholders.add(false);
            }
        }

        while (!result.isEmpty() && removablePlaceholders.get(removablePlaceholders.size() - 1)) {
            int lastIndex = result.size() - 1;
            result.remove(lastIndex);
            removablePlaceholders.remove(lastIndex);
        }
        return result;
    }

    /**
     * Performs a best-effort lore repair for third-party items whose complete presentation state
     * is not known to Slimefun. Text-only translated lines are safe to replace, and numeric/UUID
     * lines are replaced only when their token shape matches the canonical line or the caller can
     * explicitly restore that line. Ambiguous state lines are left byte-for-byte unchanged.
     */
    static @Nonnull List<String> mergeConservativeEnglishLore(
            @Nullable List<String> currentLore,
            @Nullable List<String> canonicalLore,
            @Nonnull Predicate<String> safelyRestoredLine) {
        List<String> canonical = canonicalLore == null ? List.of() : canonicalLore;
        if (currentLore == null || currentLore.isEmpty()) {
            return new ArrayList<>(canonical);
        }

        List<String> result = new ArrayList<>(currentLore);
        for (int i = 0; i < currentLore.size(); i++) {
            String currentLine = currentLore.get(i);
            if (!containsCjk(currentLine) || i >= canonical.size()) {
                continue;
            }

            String canonicalLine = canonical.get(i);
            List<Token> currentTokens = extractTokens(currentLine);
            if (currentTokens.isEmpty()) {
                result.set(i, canonicalLine);
                continue;
            }

            if (safelyRestoredLine.test(currentLine) || hasCompatibleTokenShape(currentLine, canonicalLine)) {
                result.set(i, carryDynamicTokens(currentLine, canonicalLine));
            }
        }
        return result;
    }

    private static boolean hasCompatibleTokenShape(String currentLine, String canonicalLine) {
        List<Token> currentTokens = extractTokens(currentLine);
        List<Token> canonicalTokens = extractTokens(canonicalLine);
        if (currentTokens.size() != canonicalTokens.size() || currentTokens.isEmpty()) {
            return false;
        }

        for (int i = 0; i < currentTokens.size(); i++) {
            if (currentTokens.get(i).uuid() != canonicalTokens.get(i).uuid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether every dynamic number or UUID in translated lore can be mapped one-to-one
     * onto the registered English template. Refusing ambiguous mappings prevents display repair
     * from silently resetting lore-backed addon state.
     */
    static boolean canSafelyMergeDynamicTokens(
            @Nullable List<String> currentLore, @Nullable List<String> canonicalLore) {
        return canSafelyMergeDynamicTokens(currentLore, canonicalLore, ignored -> false);
    }

    static boolean canSafelyMergeDynamicTokens(
            @Nullable List<String> currentLore,
            @Nullable List<String> canonicalLore,
            @Nonnull Predicate<String> safelyRestoredLine) {
        if (currentLore == null) {
            return true;
        }

        List<String> canonical = canonicalLore == null ? List.of() : canonicalLore;
        for (int i = 0; i < currentLore.size(); i++) {
            String currentLine = currentLore.get(i);
            if (!containsCjk(currentLine)) {
                continue;
            }

            List<Token> currentTokens = extractTokens(currentLine);
            if (currentTokens.isEmpty()) {
                continue;
            }
            if (i >= canonical.size()) {
                if (safelyRestoredLine.test(currentLine)) {
                    continue;
                }
                return false;
            }

            List<Token> canonicalTokens = extractTokens(canonical.get(i));
            if (currentTokens.size() != canonicalTokens.size()) {
                if (safelyRestoredLine.test(currentLine)) {
                    continue;
                }
                return false;
            }
            for (int tokenIndex = 0; tokenIndex < currentTokens.size(); tokenIndex++) {
                if (currentTokens.get(tokenIndex).uuid()
                        != canonicalTokens.get(tokenIndex).uuid()) {
                    if (safelyRestoredLine.test(currentLine)) {
                        break;
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Builds a conservative English display name for an orphaned Slimefun item when the
     * addon which registered its canonical template is no longer installed. Only the
     * stored Slimefun ID is used, so no functional item data is interpreted or changed.
     */
    static @Nonnull String humanizeItemId(@Nonnull String itemId) {
        String localId = itemId.trim();
        int namespace = localId.lastIndexOf(':');
        if (namespace >= 0 && namespace + 1 < localId.length()) {
            localId = localId.substring(namespace + 1);
        }

        String[] words = localId.split("[^A-Za-z0-9]+");
        StringBuilder result = new StringBuilder(localId.length());
        int emitted = 0;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (emitted++ > 0) {
                result.append(' ');
            }

            if (word.chars().allMatch(Character::isDigit) || isShortIdentifier(word)) {
                result.append(word.toUpperCase(java.util.Locale.ROOT));
                continue;
            }

            String lower = word.toLowerCase(java.util.Locale.ROOT);
            if (emitted > 1 && isConnectorWord(lower)) {
                result.append(lower);
            } else {
                result.append(Character.toUpperCase(lower.charAt(0)));
                result.append(lower, 1, lower.length());
            }
        }
        return result.length() == 0 ? "Legacy Slimefun Item" : result.toString();
    }

    /** Preserves only leading legacy formatting codes while replacing the visible text. */
    static @Nonnull String preserveLeadingFormatting(@Nullable String currentName, @Nonnull String englishName) {
        if (currentName == null || currentName.isEmpty()) {
            return englishName;
        }

        StringBuilder prefix = new StringBuilder();
        int index = 0;
        while (index + 1 < currentName.length() && currentName.charAt(index) == '\u00A7') {
            prefix.append(currentName, index, index + 2);
            index += 2;
        }
        return prefix.append(englishName).toString();
    }

    private static boolean isConnectorWord(String word) {
        return word.equals("of") || word.equals("the") || word.equals("and") || word.equals("to") || word.equals("for");
    }

    private static boolean isShortIdentifier(String word) {
        if (word.length() < 2 || word.length() > 4) {
            return false;
        }
        if (!word.equals(word.toUpperCase(java.util.Locale.ROOT))) {
            return false;
        }
        return switch (word) {
            case "XP", "GPS", "DNA", "RGB", "GUI", "PDC", "NBT", "RF", "EU", "I", "II", "III", "IV" -> true;
            default -> false;
        };
    }

    static @Nullable String carryDynamicTokens(@Nullable String currentLine, @Nullable String canonicalLine) {
        if (currentLine == null || canonicalLine == null) {
            return canonicalLine;
        }

        List<Token> currentTokens = extractTokens(currentLine);
        List<Token> canonicalTokens = extractTokens(canonicalLine);
        if (currentTokens.size() != canonicalTokens.size() || currentTokens.isEmpty()) {
            return canonicalLine;
        }

        for (int i = 0; i < currentTokens.size(); i++) {
            if (currentTokens.get(i).uuid() != canonicalTokens.get(i).uuid()) {
                return canonicalLine;
            }
        }

        StringBuilder rebuilt = new StringBuilder(canonicalLine.length() + 16);
        int previousEnd = 0;
        for (int i = 0; i < canonicalTokens.size(); i++) {
            Token canonicalToken = canonicalTokens.get(i);
            rebuilt.append(canonicalLine, previousEnd, canonicalToken.start());
            rebuilt.append(currentTokens.get(i).value());
            previousEnd = canonicalToken.end();
        }
        rebuilt.append(canonicalLine, previousEnd, canonicalLine.length());
        return rebuilt.toString();
    }

    static @Nullable Float findLegacyCharge(@Nullable List<String> lore) {
        if (lore == null) {
            return null;
        }

        Float result = null;
        for (String line : lore) {
            if (line == null) {
                continue;
            }

            Matcher matcher = LEGACY_CHARGE.matcher(stripLegacyColorCodes(line));
            if (!matcher.find()) {
                continue;
            }

            try {
                float value = Float.parseFloat(matcher.group(1).replace(',', '.'));
                if (!Float.isFinite(value) || result != null) {
                    return null;
                }
                result = value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return result;
    }

    static @Nullable Integer findLegacyUsesLeft(@Nullable List<String> lore) {
        if (lore == null) {
            return null;
        }

        Integer result = null;
        for (String line : lore) {
            if (line == null) {
                continue;
            }

            Matcher matcher = LEGACY_USES_LEFT.matcher(stripLegacyColorCodes(line));
            if (!matcher.find()) {
                continue;
            }

            try {
                int value = Integer.parseInt(matcher.group(1));
                if (result != null) {
                    return null;
                }
                result = value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return result;
    }

    private static List<Token> extractTokens(String line) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = DYNAMIC_TOKEN.matcher(line);
        while (matcher.find()) {
            String value = matcher.group();
            tokens.add(new Token(matcher.start(), matcher.end(), value, isUuidToken(value)));
        }
        return tokens;
    }

    private static boolean isUuidToken(String value) {
        return value.length() == 36
                && value.charAt(8) == '-'
                && value.charAt(13) == '-'
                && value.charAt(18) == '-'
                && value.charAt(23) == '-';
    }

    private static boolean containsEquivalent(List<String> lines, String candidate) {
        String normalizedCandidate = normalize(candidate);
        for (String line : lines) {
            if (normalize(line).equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }

        String withoutColors = stripLegacyColorCodes(text);
        return DYNAMIC_TOKEN.matcher(withoutColors).replaceAll("#").trim();
    }

    private static String stripLegacyColorCodes(String text) {
        return LEGACY_COLOR_CODE.matcher(text).replaceAll("");
    }

    private record Token(int start, int end, String value, boolean uuid) {}

    private static boolean isCjkCodePoint(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
