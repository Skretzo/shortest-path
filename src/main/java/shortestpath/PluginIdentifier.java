package shortestpath;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

public class PluginIdentifier {
    @Getter
    private final String name;
    @Getter
    private final String fullyQualifiedName;
    private final int hashCode;

    public PluginIdentifier(Plugin plugin) {
        name = plugin.getName();
        fullyQualifiedName = plugin.getClass().getName();
        hashCode = fullyQualifiedName.hashCode();
    }

    PluginIdentifier(String name, String fullyQualifiedName) {
        this.name = name;
        this.fullyQualifiedName = fullyQualifiedName;
        hashCode = fullyQualifiedName.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || !(other instanceof PluginIdentifier)) {
            return false;
        }

        // Compare fully-qualified name only in-case plugin name changes
        return ((PluginIdentifier) other).fullyQualifiedName.equals(fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return name + " [" + fullyQualifiedName + "]";
    }
}
