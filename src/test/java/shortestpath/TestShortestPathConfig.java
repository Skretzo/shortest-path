package shortestpath;

public class TestShortestPathConfig implements ShortestPathConfig
{
	private int calculationCutoff = 5;
	private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;
	private boolean includeBankPath = false;

	public void setCalculationCutoffValue(int calculationCutoff)
	{
		this.calculationCutoff = calculationCutoff;
	}

	public void setUseTeleportationItemsValue(TeleportationItem useTeleportationItems)
	{
		this.useTeleportationItems = useTeleportationItems;
	}

	public void setIncludeBankPathValue(boolean includeBankPath)
	{
		this.includeBankPath = includeBankPath;
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
		return includeBankPath;
	}

	@Override
	public int calculationCutoff()
	{
		return calculationCutoff;
	}

	@Override
	public void setBuiltTeleportationBoxes(String content)
	{
	}

	@Override
	public void setBuiltTeleportationPortalsPoh(String content)
	{
	}

}
