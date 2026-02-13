package shortestpath.transport;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class VarRequirementParser<T> implements FieldParser<Set<T>> {
    private static final String DELIM_MULTI = ";";

    protected abstract T create(int id, int value, TransportVarCheck check);

    @Override
    public Set<T> parse(String value) {
        Set<T> result = new HashSet<>();
        if (value == null || value.isEmpty()) {
            return result;
        }

        try {
            for (String requirement : value.split(DELIM_MULTI)) {
                if (requirement.isEmpty()) {
                    continue;
                }
                String[] parts = new String[0];
                for (TransportVarCheck check : TransportVarCheck.values()) {
                    parts = requirement.split(Pattern.quote(check.getCode()));
                    if (parts.length == 2) {
                        int id = Integer.parseInt(parts[0]);
                        int val = Integer.parseInt(parts[1]);
                        result.add(create(id, val, check));
                        break;
                    }
                }
                if (parts.length != 2) {
                    log.error("Invalid var requirement: '{}'", requirement);
                }
            }
        } catch (NumberFormatException e) {
            log.error("Invalid var requirement: {}", value);
        }
        return result;
    }
}


