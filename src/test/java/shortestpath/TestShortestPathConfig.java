package shortestpath;

public class TestShortestPathConfig implements ShortestPathConfig
{
	private int calculationCutoff = 5;
	private int unreachableTargetDistance = 2;
	private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;
	private boolean includeBankPath = false;

	@SuppressWarnings("unused")
	public void setCalculationCutoffValue(int calculationCutoff)
	{
		this.calculationCutoff = calculationCutoff;
	}

	public void setUnreachableTargetDistanceValue(int unreachableTargetDistance)
	{
		this.unreachableTargetDistance = unreachableTargetDistance;
	}

	@SuppressWarnings("unused")
	public void setUseTeleportationItemsValue(TeleportationItem useTeleportationItems)
	{
		this.useTeleportationItems = useTeleportationItems;
	}

	@SuppressWarnings("unused")
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
	public int unreachableTargetDistance()
	{
		return unreachableTargetDistance;
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
