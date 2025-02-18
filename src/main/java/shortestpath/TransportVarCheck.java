package shortestpath;

import lombok.Getter;

public enum TransportVarCheck {
    EQUAL("="),
    GREATER(">"),
    SMALLER("<"),
    TIME_EXCEEDS("@time_exceeds@"),
    ;

    @Getter
    private final String code;

    TransportVarCheck(String code) {
        this.code = code;
    }
}
