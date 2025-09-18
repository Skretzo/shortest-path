package shortestpath.transport;

import lombok.Getter;

public enum TransportVarCheck {
    BIT_SET("&"),
    COOLDOWN_MINUTES("@"),
    EQUAL("="),
    GREATER(">"),
    SMALLER("<"),
    ;

    @Getter
    private final String code;

    TransportVarCheck(String code) {
        this.code = code;
    }
}
