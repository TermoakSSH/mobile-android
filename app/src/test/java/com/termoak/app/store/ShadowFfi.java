package com.termoak.app.store;

import com.termoak.ffi.AccountNames;
import com.termoak.ffi.ContextChip;
import com.termoak.ffi.ContextChipKind;
import com.termoak.ffi.LastCommandInfo;
import com.termoak.ffi.TerminalThemeInfo;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The engine's plain functions (com.termoak.ffi's top-level ones) that the
 * screens call to show things, with the engine's rules (names.rs,
 * term_ai.rs, themes.rs), for the JVM where its library can't load.
 */
@Implements(className = "com.termoak.ffi.Termoak_ffiKt", isInAndroidSdk = false)
public class ShadowFfi {
    @Implementation
    protected static String accountDisplayName(AccountNames names, String accountId, String email) {
        String alias = alias(names, accountId);
        return alias != null ? alias : accountDisplayEmail(names, email);
    }

    @Implementation
    protected static String accountDisplayEmail(AccountNames names, String email) {
        return names.getHideEmails() ? maskEmail(email) : email.trim();
    }

    @Implementation
    protected static String accountInitial(AccountNames names, String accountId, String name, String email) {
        String alias = alias(names, accountId);
        String from = alias != null ? alias : (name.trim().isEmpty() ? email : name).trim();
        return from.isEmpty() ? "?" : from.substring(0, from.offsetByCodePoints(0, 1)).toUpperCase();
    }

    @Implementation
    protected static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String tld = dot >= 0 ? domain.substring(dot) : "";
        return email.charAt(0) + "•••@" + (domain.isEmpty() ? "" : domain.charAt(0) + "•••") + tld;
    }

    @Implementation
    protected static List<TerminalThemeInfo> terminalThemes() {
        return Themes.INSTANCE.getAll();
    }

    @Implementation
    protected static String terminalThemeForHost(String value, String app) {
        for (TerminalThemeInfo t : Themes.INSTANCE.getAll()) {
            if (t.getId().equals(value)) return value;
        }
        return app;
    }

    @Implementation
    protected static ContextChip contextChipHost(String name, String os) {
        String system = os == null || os.isEmpty() ? null : Character.toUpperCase(os.charAt(0)) + os.substring(1);
        return new ContextChip(ContextChipKind.HOST, system == null ? name : name + " \u00b7 " + system, "Host: " + name);
    }

    @Implementation
    protected static ContextChip contextChipLastCommand(LastCommandInfo last, String label) {
        return new ContextChip(ContextChipKind.LAST_COMMAND, label, last.getOutput());
    }

    @Implementation
    protected static ContextChip contextChipSelection(String text, String label) {
        return new ContextChip(ContextChipKind.SELECTION, label, text);
    }

    @Implementation(methodName = "shortenText-OsBMiQA")
    protected static String shortenText(String text, int max, boolean fromEnd) {
        if (text.length() <= max) return text;
        return fromEnd ? "…" + text.substring(text.length() - max + 1) : text.substring(0, max - 1) + "…";
    }

    @Implementation
    protected static boolean containsSecrets(String text) {
        return false;
    }

    @Implementation
    protected static String redactSecrets(String text) {
        return text;
    }

    @Implementation
    protected static String libraryVersion() {
        return "0.6.1";
    }

    @Implementation
    protected static String officialServerUrl() {
        return "https://termoak.com";
    }

    @Implementation
    protected static boolean commandEchoed(String command, String line, boolean prefix) {
        return line.contains(command);
    }

    @Implementation
    protected static String typeableCommand(String command) {
        return command.trim();
    }

    private static String alias(AccountNames names, String accountId) {
        String a = names.getAliases().get(accountId);
        if (a == null || a.trim().isEmpty()) return null;
        return a.trim();
    }
}
