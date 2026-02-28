package com.rivoo.common.util;

import java.util.UUID;

/**
 * Generates prefixed external IDs for domain entities.
 * Format: {prefix}_{uuid} → e.g., sal_98765432-abcd-ef01-2345-678901234567
 */
public final class ExternalIdGenerator {

    private ExternalIdGenerator() {
    }

    public static String generate(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }
}
