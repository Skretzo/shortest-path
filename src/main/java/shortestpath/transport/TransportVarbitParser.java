package shortestpath.transport;

public class TransportVarbitParser extends VarRequirementParser<TransportVarbit> {
    @Override
    protected TransportVarbit create(int id, int value, TransportVarCheck check) {
        return new TransportVarbit(id, value, check);
    }
}

