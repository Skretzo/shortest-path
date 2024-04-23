package shortestpath;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PluginPathManager {
    @Getter
    private List<PluginIdentifier> pluginPriorities;
    private Map<PluginIdentifier, PathParameters> pluginMap;

    @Getter
    private PathParameters currentParameters;

    PluginPathManager() {
        pluginPriorities = new ArrayList<>(32);
        pluginMap = new HashMap<>(32);
    }

    public PathParameters getParameters(PluginIdentifier pluginIdentifier) {
        return pluginMap.get(pluginIdentifier);
    }

    public void addPlugin(PluginIdentifier pluginIdentifier) {
        if (!pluginPriorities.contains(pluginIdentifier)) {
            pluginPriorities.add(pluginIdentifier);
        }
    }

    public boolean removePlugin(PluginIdentifier pluginIdentifier) {
        pluginPriorities.remove(pluginIdentifier);
        pluginMap.remove(pluginIdentifier);
        return refreshCurrentParameters();
    }

    // Returns true if the current parameters changed
    public boolean requestPath(PathParameters parameters) {
        if (Objects.equals(currentParameters, parameters)) {
            return false;
        }

        int priority = pluginPriorities.indexOf(parameters.getRequester());
        pluginMap.put(parameters.getRequester(), parameters);
        if (priority < 0) {
            pluginPriorities.add(parameters.getRequester());
        }

        return refreshCurrentParameters();
    }

    // Returns true if the current parameters changed
    boolean setNewPriorities(@NonNull List<PluginIdentifier> priorities) {
        if (priorities.size() != pluginPriorities.size()) {
            throw new IllegalArgumentException("new priorities size must match");
        }

        pluginPriorities = priorities;
        return refreshCurrentParameters();
    }

    // Returns true if the current parameters changed
    boolean clearPath(PluginIdentifier requester) {
        pluginMap.remove(requester);
        return refreshCurrentParameters();
    }

    // Returns true if the current parameters changed
    private boolean refreshCurrentParameters() {
        PathParameters oldParameters = currentParameters;
        currentParameters = getHighestPriorityParameters();
        return !Objects.equals(oldParameters, currentParameters);
    }

    private PathParameters getHighestPriorityParameters() {
        for (int i = 0; i < pluginPriorities.size(); ++i) {
            PathParameters parameters = pluginMap.get(pluginPriorities.get(i));
            if (parameters != null && parameters.isVisible()) {
                return parameters;
            }
        }

        return null;
    }

    public void shutDown() {
        // TODO: save order
    }
}
