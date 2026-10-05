package shortestpath;

import shortestpath.requirement.TeleportationItem;

public class TestShortestPathConfig implements ShortestPathConfig
{
	private int calculationCutoff = 5;
	private int unreachableTargetDistance = 2;
	private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;
	private boolean includeBankPath = false;
	private int costBankVisit = 0;
	private boolean unlockCanoeAxe = false;
	private boolean unlockXericsHonour = false;
	private boolean unlockDragontoothPassage = false;
	private String blockedTeleportItems = "";
	private boolean unlockBalloonLogBasket = false;

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

	@SuppressWarnings("unused")
	public void setCostBankVisitValue(int costBankVisit)
	{
		this.costBankVisit = costBankVisit;
	}

	@SuppressWarnings("unused")
	public void setUnlockCanoeAxeValue(boolean unlockCanoeAxe)
	{
		this.unlockCanoeAxe = unlockCanoeAxe;
	}

	@SuppressWarnings("unused")
	public void setUnlockXericsHonourValue(boolean unlockXericsHonour)
	{
		this.unlockXericsHonour = unlockXericsHonour;
	}

	@SuppressWarnings("unused")
	public void setUnlockDragontoothPassageValue(boolean unlockDragontoothPassage)
	{
		this.unlockDragontoothPassage = unlockDragontoothPassage;
	}

	@SuppressWarnings("unused")
	public void setBlockedTeleportItemsValue(String blockedTeleportItems)
	{
		this.blockedTeleportItems = blockedTeleportItems;
	}

	@SuppressWarnings("unused")
	public void setUnlockBalloonLogBasketValue(boolean unlockBalloonLogBasket)
	{
		this.unlockBalloonLogBasket = unlockBalloonLogBasket;
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
	public int costBankVisit()
	{
		return costBankVisit;
	}

	@Override
	public boolean unlockCanoeAxe()
	{
		return unlockCanoeAxe;
	}

	@Override
	public boolean unlockXericsHonour()
	{
		return unlockXericsHonour;
	}

	@Override
	public boolean unlockDragontoothPassage()
	{
		return unlockDragontoothPassage;
	}

	@Override
	public String blockedTeleportItems()
	{
		return blockedTeleportItems;
	}

	@Override
	public boolean unlockBalloonLogBasket()
	{
		return unlockBalloonLogBasket;
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
