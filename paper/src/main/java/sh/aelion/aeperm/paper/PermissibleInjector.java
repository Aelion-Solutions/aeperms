package sh.aelion.aeperm.paper;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;

import java.lang.reflect.Field;

final class PermissibleInjector {

    private PermissibleInjector() {
    }

    static boolean inject(Player player, AepermPermissible replacement) {
        try {
            Field field = findPermField(player.getClass());
            PermissibleBase old = (PermissibleBase) field.get(player);
            if (old instanceof AepermPermissible) {
                return true;
            }
            replacement.oldPermissible(old);
            field.set(player, replacement);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    static void uninject(Player player) {
        try {
            Field field = findPermField(player.getClass());
            Object current = field.get(player);
            if (current instanceof AepermPermissible ours) {
                PermissibleBase old = ours.oldPermissible();
                field.set(player, old != null ? old : new PermissibleBase(player));
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Field findPermField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField("perm");
                if (PermissibleBase.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException("perm");
    }
}
