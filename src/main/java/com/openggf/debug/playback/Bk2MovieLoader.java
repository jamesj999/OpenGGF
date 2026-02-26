package com.openggf.debug.playback;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads BizHawk BK2 movies (zip container + Input Log) for deterministic input playback.
 */
public final class Bk2MovieLoader {
    private static final String BK2_EXTENSION = ".bk2";
    private static final String INPUT_LOG_ENTRY = "Input Log.txt";
    private static final String HEADER_ENTRY = "Header.txt";

    public Bk2Movie load(Path bk2Path) throws IOException {
        Objects.requireNonNull(bk2Path, "bk2Path");
        String pathString = bk2Path.toString().toLowerCase(Locale.ROOT);
        if (!pathString.endsWith(BK2_EXTENSION)) {
            throw new IOException("Unsupported movie file extension (expected .bk2): " + bk2Path);
        }
        if (!Files.exists(bk2Path)) {
            throw new IOException("Movie file not found: " + bk2Path);
        }

        try (ZipFile zip = new ZipFile(bk2Path.toFile())) {
            ZipEntry inputLogEntry = findEntryIgnoreCase(zip, INPUT_LOG_ENTRY);
            if (inputLogEntry == null) {
                throw new IOException("BK2 missing required entry: " + INPUT_LOG_ENTRY);
            }

            ZipEntry headerEntry = findEntryIgnoreCase(zip, HEADER_ENTRY);
            Map<String, String> headerMetadata = headerEntry == null
                    ? Map.of()
                    : parseHeader(readEntryLines(zip, headerEntry));

            List<String> inputLines = readEntryLines(zip, inputLogEntry);
            ParsedInputLog parsed = parseInputLog(inputLines);
            return new Bk2Movie(bk2Path, parsed.logKey(), headerMetadata, parsed.frames());
        }
    }

    private static ZipEntry findEntryIgnoreCase(ZipFile zip, String expectedName) {
        String expected = expectedName.toLowerCase(Locale.ROOT);
        return zip.stream()
                .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).equals(expected))
                .findFirst()
                .orElse(null);
    }

    private static List<String> readEntryLines(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    private static Map<String, String> parseHeader(List<String> lines) {
        Map<String, String> metadata = new HashMap<>();
        for (String line : lines) {
            String trimmed = stripBom(line).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("[")) {
                continue;
            }
            int sep = trimmed.indexOf(':');
            if (sep <= 0 || sep >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, sep).trim();
            String value = trimmed.substring(sep + 1).trim();
            if (!key.isEmpty()) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }

    private static ParsedInputLog parseInputLog(List<String> lines) throws IOException {
        String logKey = null;
        List<String> frameLines = new ArrayList<>();
        boolean inInputBlock = false;

        for (String line : lines) {
            String stripped = stripBom(line);
            String trimmed = stripped.trim();
            if (trimmed.startsWith("LogKey:")) {
                logKey = trimmed.substring("LogKey:".length()).trim();
            }
            if ("[Input]".equalsIgnoreCase(trimmed)) {
                inInputBlock = true;
                continue;
            }
            if ("[/Input]".equalsIgnoreCase(trimmed)) {
                inInputBlock = false;
                continue;
            }
            if (!inInputBlock) {
                continue;
            }
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("LogKey:")) {
                continue;
            }
            frameLines.add(trimmed);
        }

        if (logKey == null || logKey.isBlank()) {
            throw new IOException("BK2 Input Log missing LogKey");
        }
        if (frameLines.isEmpty()) {
            throw new IOException("BK2 Input Log contains no frame lines");
        }

        List<String> logKeyFields = splitFields(logKey);
        List<String> firstFrameFields = splitFields(frameLines.get(0));
        FrameBindings bindings = resolveBindings(logKeyFields, firstFrameFields);
        if (bindings.bindings().isEmpty()) {
            throw new IOException("Unable to resolve P1 Genesis bindings from LogKey: " + logKey);
        }

        List<Bk2FrameInput> frames = new ArrayList<>(frameLines.size());
        for (int i = 0; i < frameLines.size(); i++) {
            String raw = frameLines.get(i);
            List<String> fields = splitFields(raw);
            int mask = 0;
            boolean startPressed = false;
            boolean a = isPressed(bindings.bindings().get(Button.A), fields);
            boolean b = isPressed(bindings.bindings().get(Button.B), fields);
            boolean c = isPressed(bindings.bindings().get(Button.C), fields);

            if (isPressed(bindings.bindings().get(Button.UP), fields)) {
                mask |= AbstractPlayableSprite.INPUT_UP;
            }
            if (isPressed(bindings.bindings().get(Button.DOWN), fields)) {
                mask |= AbstractPlayableSprite.INPUT_DOWN;
            }
            if (isPressed(bindings.bindings().get(Button.LEFT), fields)) {
                mask |= AbstractPlayableSprite.INPUT_LEFT;
            }
            if (isPressed(bindings.bindings().get(Button.RIGHT), fields)) {
                mask |= AbstractPlayableSprite.INPUT_RIGHT;
            }
            if (a || b || c) {
                mask |= AbstractPlayableSprite.INPUT_JUMP;
            }
            if (isPressed(bindings.bindings().get(Button.START), fields)) {
                startPressed = true;
            }

            frames.add(new Bk2FrameInput(i, mask, startPressed, raw));
        }

        return new ParsedInputLog(logKey, frames);
    }

    private static FrameBindings resolveBindings(List<String> logKeyFields, List<String> firstFrameFields) {
        EnumMap<Button, FieldBinding> map = new EnumMap<>(Button.class);

        resolveHashGroupedBindings(logKeyFields, firstFrameFields, map);
        if (hasDirectionMapping(map)) {
            return new FrameBindings(map);
        }

        // Direct field-by-field mapping is only valid when line/token arity matches.
        if (Math.abs(logKeyFields.size() - firstFrameFields.size()) <= 2) {
            for (int i = 0; i < logKeyFields.size(); i++) {
                putButtonBinding(map, logKeyFields.get(i), FieldBinding.field(i));
            }
        }

        if (hasDirectionMapping(map)) {
            return new FrameBindings(map);
        }

        int groupedFieldIndex = -1;
        String groupedToken = "";
        for (int i = 0; i < logKeyFields.size(); i++) {
            String raw = logKeyFields.get(i).trim().toUpperCase(Locale.ROOT);
            if (raw.length() >= 8 && raw.indexOf('U') >= 0 && raw.indexOf('D') >= 0
                    && raw.indexOf('L') >= 0 && raw.indexOf('R') >= 0) {
                groupedFieldIndex = i;
                groupedToken = raw;
                break;
            }
        }

        if (groupedFieldIndex >= 0) {
            bindGroupedToken(map, groupedFieldIndex, groupedToken);
            return new FrameBindings(map);
        }

        int fallbackIndex = -1;
        for (int i = 0; i < firstFrameFields.size(); i++) {
            String token = firstFrameFields.get(i);
            if (token.length() >= 8) {
                fallbackIndex = i;
                break;
            }
        }
        if (fallbackIndex >= 0) {
            map.put(Button.UP, FieldBinding.charAt(fallbackIndex, 0));
            map.put(Button.DOWN, FieldBinding.charAt(fallbackIndex, 1));
            map.put(Button.LEFT, FieldBinding.charAt(fallbackIndex, 2));
            map.put(Button.RIGHT, FieldBinding.charAt(fallbackIndex, 3));
            map.put(Button.START, FieldBinding.charAt(fallbackIndex, 4));
            map.put(Button.A, FieldBinding.charAt(fallbackIndex, 5));
            map.put(Button.B, FieldBinding.charAt(fallbackIndex, 6));
            map.put(Button.C, FieldBinding.charAt(fallbackIndex, 7));
        }

        return new FrameBindings(map);
    }

    private static void resolveHashGroupedBindings(List<String> logKeyFields, List<String> firstFrameFields,
            EnumMap<Button, FieldBinding> map) {
        List<GroupDescriptor> groups = buildHashGroups(logKeyFields);
        if (groups.isEmpty()) {
            return;
        }
        int[] dataIndices = middleFieldIndices(firstFrameFields);
        if (dataIndices.length != groups.size()) {
            return;
        }
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            GroupDescriptor group = groups.get(groupIndex);
            int fieldIndex = dataIndices[groupIndex];
            for (int charIndex = 0; charIndex < group.tokens().size(); charIndex++) {
                putButtonBinding(map, group.tokens().get(charIndex), FieldBinding.charAt(fieldIndex, charIndex));
            }
        }
    }

    private static List<GroupDescriptor> buildHashGroups(List<String> logKeyFields) {
        List<GroupDescriptor> groups = new ArrayList<>();
        List<String> current = null;
        for (String raw : logKeyFields) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean startsGroup = trimmed.startsWith("#");
            String token = startsGroup ? trimmed.substring(1).trim() : trimmed;
            if (token.isEmpty()) {
                continue;
            }
            if (startsGroup || current == null) {
                current = new ArrayList<>();
                groups.add(new GroupDescriptor(current));
            }
            current.add(token);
        }
        return groups;
    }

    private static int[] middleFieldIndices(List<String> fields) {
        if (fields.size() <= 2) {
            return new int[0];
        }
        int[] indices = new int[fields.size() - 2];
        int out = 0;
        for (int i = 1; i < fields.size() - 1; i++) {
            indices[out++] = i;
        }
        return indices;
    }

    private static void putButtonBinding(EnumMap<Button, FieldBinding> map, String rawToken, FieldBinding binding) {
        String token = normalize(rawToken);
        if (token.isEmpty() || token.contains("p2")) {
            return;
        }
        boolean p1Scoped = token.contains("p1");
        String leaf = token;
        int lastSpace = token.lastIndexOf(' ');
        if (lastSpace >= 0 && lastSpace < token.length() - 1) {
            leaf = token.substring(lastSpace + 1);
        }
        if ("up".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.UP, binding);
        } else if ("down".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.DOWN, binding);
        } else if ("left".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.LEFT, binding);
        } else if ("right".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.RIGHT, binding);
        } else if ("a".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.A, binding);
        } else if ("b".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.B, binding);
        } else if ("c".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.C, binding);
        } else if ("start".equals(leaf) && (p1Scoped || !containsLanePrefix(token))) {
            map.putIfAbsent(Button.START, binding);
        }
    }

    private static void bindGroupedToken(EnumMap<Button, FieldBinding> map, int fieldIndex, String groupedToken) {
        putCharBindingIfFound(map, Button.UP, fieldIndex, groupedToken.indexOf('U'));
        putCharBindingIfFound(map, Button.DOWN, fieldIndex, groupedToken.indexOf('D'));
        putCharBindingIfFound(map, Button.LEFT, fieldIndex, groupedToken.indexOf('L'));
        putCharBindingIfFound(map, Button.RIGHT, fieldIndex, groupedToken.indexOf('R'));
        putCharBindingIfFound(map, Button.START, fieldIndex, groupedToken.indexOf('S'));
        putCharBindingIfFound(map, Button.A, fieldIndex, groupedToken.indexOf('A'));
        putCharBindingIfFound(map, Button.B, fieldIndex, groupedToken.indexOf('B'));
        putCharBindingIfFound(map, Button.C, fieldIndex, groupedToken.indexOf('C'));
    }

    private static void putCharBindingIfFound(EnumMap<Button, FieldBinding> map, Button button, int fieldIndex,
            int charIndex) {
        if (charIndex >= 0) {
            map.putIfAbsent(button, FieldBinding.charAt(fieldIndex, charIndex));
        }
    }

    private static boolean hasDirectionMapping(EnumMap<Button, FieldBinding> map) {
        return map.containsKey(Button.UP)
                || map.containsKey(Button.DOWN)
                || map.containsKey(Button.LEFT)
                || map.containsKey(Button.RIGHT);
    }

    private static boolean containsLanePrefix(String token) {
        return token.contains("p1") || token.contains("p2");
    }

    private static boolean isPressed(FieldBinding binding, List<String> fields) {
        if (binding == null || binding.fieldIndex() < 0 || binding.fieldIndex() >= fields.size()) {
            return false;
        }
        String token = fields.get(binding.fieldIndex()).trim();
        if (token.isEmpty()) {
            return false;
        }
        if (binding.charIndex() >= 0) {
            return binding.charIndex() < token.length() && token.charAt(binding.charIndex()) != '.';
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) != '.') {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitFields(String line) {
        String[] raw = stripBom(line).split("\\|", -1);
        List<String> fields = new ArrayList<>(raw.length);
        for (String token : raw) {
            fields.add(token);
        }
        return fields;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    private static String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private enum Button {
        UP, DOWN, LEFT, RIGHT, START, A, B, C
    }

    private record FieldBinding(int fieldIndex, int charIndex) {
        private static FieldBinding field(int fieldIndex) {
            return new FieldBinding(fieldIndex, -1);
        }

        private static FieldBinding charAt(int fieldIndex, int charIndex) {
            return new FieldBinding(fieldIndex, charIndex);
        }
    }

    private record FrameBindings(EnumMap<Button, FieldBinding> bindings) {
    }

    private record ParsedInputLog(String logKey, List<Bk2FrameInput> frames) {
    }

    private record GroupDescriptor(List<String> tokens) {
    }
}
