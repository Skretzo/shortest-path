package shortestpath;

import java.util.EnumSet;
import java.util.Set;
import shortestpath.poh.PohNexusPortal;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.BalloonRoute;

public class TestShortestPathConfig implements ShortestPathConfig
{
	private int calculationCutoff = 5;
	private int unreachableTargetDistance = 2;
	private boolean collisionAwareBlockedTargets = true;
	private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;
	private boolean includeBankPath = false;
	private int costBankVisit = 0;
	private boolean unlockCanoeAxe = false;
	private boolean unlockXericsHonour = false;
	private boolean unlockDragontoothPassage = false;
	private boolean unlockSoulRift = false;
	private boolean unlockBloodRift = false;
	private String blockedTeleportItems = "";
	private boolean unlockBalloonLogBasket = false;
	// Null means the key was never written: like the interface default, the
	// route set then derives from the legacy unlockBalloonLogBasket flag.
	private Set<BalloonRoute> balloonLogBasketRoutes = null;
	private boolean usePoh = false;
	// Null means the key was never written: like the interface default, the
	// portal set then derives from the legacy useTeleportationPortalsPoh flag.
	private Set<PohNexusPortal> pohNexusPortals = null;
	private boolean showBankPickupInfo = false;
	private boolean showTransportInfo = true;

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
	public void setCollisionAwareBlockedTargetsValue(boolean collisionAwareBlockedTargets)
	{
		this.collisionAwareBlockedTargets = collisionAwareBlockedTargets;
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
	public void setUnlockSoulRiftValue(boolean unlockSoulRift)
	{
		this.unlockSoulRift = unlockSoulRift;
	}

	@SuppressWarnings("unused")
	public void setUnlockBloodRiftValue(boolean unlockBloodRift)
	{
		this.unlockBloodRift = unlockBloodRift;
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

	@SuppressWarnings("unused")
	public void setBalloonLogBasketRoutesValue(Set<BalloonRoute> balloonLogBasketRoutes)
	{
		this.balloonLogBasketRoutes = balloonLogBasketRoutes;
	}

	@SuppressWarnings("unused")
	public void setUsePohValue(boolean usePoh)
	{
		this.usePoh = usePoh;
	}

	@SuppressWarnings("unused")
	public void setPohNexusPortalsValue(Set<PohNexusPortal> pohNexusPortals)
	{
		this.pohNexusPortals = pohNexusPortals;
	}

	@SuppressWarnings("unused")
	public void setShowBankPickupInfoValue(boolean showBankPickupInfo)
	{
		this.showBankPickupInfo = showBankPickupInfo;
	}

	@SuppressWarnings("unused")
	public void setShowTransportInfoValue(boolean showTransportInfo)
	{
		this.showTransportInfo = showTransportInfo;
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
	public boolean unlockSoulRift()
	{
		return unlockSoulRift;
	}

	@Override
	public boolean unlockBloodRift()
	{
		return unlockBloodRift;
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
	public Set<BalloonRoute> balloonLogBasketRoutes()
	{
		if (balloonLogBasketRoutes != null)
		{
			return balloonLogBasketRoutes;
		}
		return unlockBalloonLogBasket
			? EnumSet.allOf(BalloonRoute.class)
			: EnumSet.noneOf(BalloonRoute.class);
	}

	@Override
	public boolean usePoh()
	{
		return usePoh;
	}

	@Override
	public Set<PohNexusPortal> pohNexusPortals()
	{
		return pohNexusPortals != null
			? pohNexusPortals
			: ShortestPathConfig.super.pohNexusPortals();
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
	public boolean collisionAwareBlockedTargets()
	{
		return collisionAwareBlockedTargets;
	}

	@Override
	public boolean showBankPickupInfo()
	{
		return showBankPickupInfo;
	}

	@Override
	public boolean showTransportInfo()
	{
		return showTransportInfo;
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
