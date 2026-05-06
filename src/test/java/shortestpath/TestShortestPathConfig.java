package shortestpath;

public class TestShortestPathConfig implements ShortestPathConfig
{
	private int calculationCutoff = 5;
	private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;

	public void setCalculationCutoffValue(int calculationCutoff)
	{
		this.calculationCutoff = calculationCutoff;
	}

	public void setUseTeleportationItemsValue(TeleportationItem useTeleportationItems)
	{
		this.useTeleportationItems = useTeleportationItems;
	}

	@Override
	public TeleportationItem useTeleportationItems()
	{
		return useTeleportationItems;
	}

	@Override
	public boolean useTeleportationMinigames()
	{
		return true;
	}

	@Override
	public boolean includeBankPath()
	{
		return false;
	}

	@Override
	public int calculationCutoff()
	{
		return calculationCutoff;
	}

}
