package com.personalenglishai.backend.service.subscription;

import java.util.function.Supplier;

public final class AiUsageContextHolder {
    private static final ThreadLocal<AiUsageContext> HOLDER = new ThreadLocal<>();

    private AiUsageContextHolder() {
    }

    public static AiUsageContext current() {
        return HOLDER.get();
    }

    public static void set(AiUsageContext context) {
        HOLDER.set(context);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static <T> T call(AiUsageContext context, Supplier<T> supplier) {
        AiUsageContext previous = HOLDER.get();
        HOLDER.set(context);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
