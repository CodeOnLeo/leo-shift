package io.github.codeonleo.leoshift.util;

import io.github.codeonleo.leoshift.entity.User;
import org.springframework.util.StringUtils;

public final class ColorTagUtil {

    private static final String[] PALETTE = {
            "#0A84FF", "#5E5CE6", "#32D74B", "#FF9F0A", "#FF375F", "#64D2FF", "#FFD60A", "#A2845E"
    };

    private ColorTagUtil() {
    }

    public static String resolve(User user) {
        if (user == null) {
            return PALETTE[0];
        }
        if (StringUtils.hasText(user.getColorTag())) {
            return user.getColorTag();
        }
        int seed = 0;
        if (user.getId() != null) {
            seed = user.getId().intValue();
        } else if (StringUtils.hasText(user.getEmail())) {
            seed = user.getEmail().hashCode();
        } else if (StringUtils.hasText(user.getName())) {
            seed = user.getName().hashCode();
        }
        int idx = Math.abs(seed) % PALETTE.length;
        return PALETTE[idx];
    }
}
