package shortestpath;

public class TestShortestPathConfig implements ShortestPathConfig {
    private int calculationCutoff = 5;
    private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;
    private String builtTeleportationBoxes = "";
    private String builtTeleportationPortalsPoh = "";

    public void setCalculationCutoffValue(int calculationCutoff) {
        this.calculationCutoff = calculationCutoff;
    }

    @Override
    public int calculationCutoff() {
        return calculationCutoff;
    }

    public void setUseTeleportationItemsValue(TeleportationItem useTeleportationItems) {
        this.useTeleportationItems = useTeleportationItems;
    }

    @Override
    public TeleportationItem useTeleportationItems() {
        return useTeleportationItems;
    }

    @Override
    public void setBuiltTeleportationBoxes(String content) {
        builtTeleportationBoxes = content;
    }

    @Override
    public String builtTeleportationBoxes() {
        return builtTeleportationBoxes;
    }

    @Override
    public void setBuiltTeleportationPortalsPoh(String content) {
        builtTeleportationPortalsPoh = content;
    }

    @Override
    public String builtTeleportationPortalsPoh() {
        return builtTeleportationPortalsPoh;
    }
}
