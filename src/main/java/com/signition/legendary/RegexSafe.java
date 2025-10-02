// src_patch/com/signition/legendary/RegexSafe.java
package com.signition.legendary;

import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class RegexSafe {
    private final Logger log;
    public RegexSafe(Logger log) { this.log = log; }

    public Pattern compileOrDefault(String userRegex, String defaultRegex, String key) {
        try {
            return Pattern.compile(userRegex);
        } catch (Exception ex) {
            log.warning("[LegendarySpawnDiscord] Invalid regex for " + key + ": " + ex.getMessage()
                    + "  -- Falling back to default.");
            return Pattern.compile(defaultRegex);
        }
    }
}
