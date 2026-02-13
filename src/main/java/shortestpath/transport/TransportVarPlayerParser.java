package shortestpath.transport;

public class TransportVarPlayerParser extends VarRequirementParser<TransportVarPlayer> {
    @Override
    protected TransportVarPlayer create(int id, int value, TransportVarCheck check) {
        return new TransportVarPlayer(id, value, check);
    }
}

