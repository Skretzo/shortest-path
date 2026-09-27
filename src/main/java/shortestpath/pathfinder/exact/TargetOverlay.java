package shortestpath.pathfinder.exact;

import java.util.Arrays;
import shortestpath.pathfinder.CollisionMap;

/** Query-local target site and its point-attachment walking edges. */
public final class TargetOverlay
{
	private final SiteGraph graph;
	private final RoutingStatic stat;
	private final PreparedRoutingAccount account;
	private final long accountFingerprint;
	private final CollisionMap collision;
	private final int packed;
	private final int node;
	private final boolean synthetic;
	private final int[] components;
	private final int[] attachmentSites;
	private final int[] attachmentCosts;

	public TargetOverlay(SiteGraph graph, CollisionMap collision, int packed)
	{
		if (graph == null || collision == null)
			throw new NullPointerException();
		this.graph = graph;
		this.stat = graph.routingStatic();
		this.account = graph.preparedAccount();
		this.accountFingerprint = account.fingerprint();
		this.collision = collision;
		this.packed = packed;
		int existing = graph.nodeForTile(packed);
		this.synthetic = existing < 0;
		this.node = synthetic ? graph.nodeCount() : existing;
		this.components = synthetic ? stat.attachments(packed, collision) : stat.siteComponents(existing);
		int[] sites = new int[componentSiteCapacity(components)];
		int count = 0;
		for (int component : components)
		{
			for (int site : stat.componentSites(component))
			{
				if (firstSharedComponent(site) == component)
					sites[count++] = site;
			}
		}
		attachmentSites = Arrays.copyOf(sites, count);
		attachmentCosts = new int[count];
		for (int i = 0; i < count; i++)
		{
			attachmentCosts[i] = distance(packed, stat.siteTile(attachmentSites[i]));
		}
	}

	public SiteGraph graph()
	{
		return graph;
	}
	public RoutingStatic routingStatic()
	{
		return stat;
	}
	public PreparedRoutingAccount account()
	{
		return account;
	}
	CollisionMap collision()
	{
		return collision;
	}
	public long accountFingerprint()
	{
		return accountFingerprint;
	}
	public int packedTarget()
	{
		return packed;
	}
	public int targetNode()
	{
		return node;
	}
	public boolean synthetic()
	{
		return synthetic;
	}
	public int queryNodeCount()
	{
		return graph.nodeCount() + (synthetic ? 1 : 0);
	}
	public int[] components()
	{
		return components.clone();
	}
	public int attachmentCount()
	{
		return attachmentSites.length;
	}
	public int attachmentSite(int index)
	{
		return attachmentSites[index];
	}
	public int attachmentCost(int index)
	{
		return attachmentCosts[index];
	}
	public int targetState(boolean banked)
	{
		return SiteGraph.stateId(node, banked);
	}

	void requireCompatible(SiteGraph candidate)
	{
		if (candidate.routingStatic() != stat || candidate.preparedAccount() != account
			|| candidate.preparedAccount().fingerprint() != accountFingerprint)
		{
			throw new IllegalArgumentException("target overlay belongs to another static/account snapshot");
		}
	}

	private int componentSiteCapacity(int[] values)
	{
		int capacity = 0;
		for (int component : values)
			capacity += stat.componentSites(component).length;
		return capacity;
	}

	private int firstSharedComponent(int site)
	{
		for (int component : components)
		{
			for (int siteComponent : stat.siteComponents(site))
				if (siteComponent == component)
					return component;
		}
		throw new IllegalStateException("target attachment missing shared routing component");
	}

	private static int distance(int left, int right)
	{
		return shortestpath.WorldPointUtil.distanceBetween(left, right);
	}
}
