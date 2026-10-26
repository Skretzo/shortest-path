package shortestpath.transport;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import org.junit.Test;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.VarCheckType;
import shortestpath.requirement.model.VarRequirement;

/**
 * Contract pin for {@link LoadInterner}: every {@code intern*} entry point
 * returns the first-inserted canonical instance for equal inputs, and the two
 * documented null arms pass {@code null} through. The interner is
 * package-private, so this suite lives in its package to reach it.
 */
public class LoadInternerTest
{
	private static VarRequirement varbit(int id)
	{
		return new VarRequirement(VarRequirement.VarType.VARBIT, id, 1, VarCheckType.EQUAL);
	}

	private static TransportItems oneItemGroup()
	{
		return new TransportItems(List.of(
			new ItemRequirement(new int[]{1, 2}, new int[0], new int[0], 1)));
	}

	@Test
	public void internVarRequirementReturnsTheCanonicalInstance()
	{
		LoadInterner interner = new LoadInterner();
		VarRequirement first = varbit(123);

		assertSame(first, interner.intern(first));
		assertSame(first, interner.intern(varbit(123)));
	}

	@Test
	public void internTransportItemsReturnsTheCanonicalInstance()
	{
		LoadInterner interner = new LoadInterner();
		TransportItems first = oneItemGroup();

		assertSame(first, interner.intern(first));
		assertSame(first, interner.intern(oneItemGroup()));
	}

	@Test
	public void internTransportItemsPassesNullThrough()
	{
		assertNull(new LoadInterner().intern((TransportItems) null));
	}

	@Test
	public void internVarSetReturnsTheFirstInsertedSet()
	{
		LoadInterner interner = new LoadInterner();
		Set<VarRequirement> first = new HashSet<>(Set.of(varbit(123), varbit(456)));
		// Set equality is order-insensitive; the interned set is the first-seen instance.
		Set<VarRequirement> equal = new HashSet<>(Set.of(varbit(456), varbit(123)));

		assertSame(first, interner.internVarSet(first));
		assertSame(first, interner.internVarSet(equal));
	}

	@Test
	public void internQuestSetReturnsTheFirstInsertedSet()
	{
		LoadInterner interner = new LoadInterner();
		Set<Quest> first = new HashSet<>(Set.of(Quest.COOKS_ASSISTANT, Quest.DEMON_SLAYER));
		Set<Quest> equal = new HashSet<>(Set.of(Quest.DEMON_SLAYER, Quest.COOKS_ASSISTANT));

		assertSame(first, interner.internQuestSet(first));
		assertSame(first, interner.internQuestSet(equal));
	}

	@Test
	public void internStringReturnsTheCanonicalInstanceAndPassesNullThrough()
	{
		LoadInterner interner = new LoadInterner();
		String first = new String("label");

		assertSame(first, interner.internString(first));
		assertSame(first, interner.internString(new String("label")));
		assertNull(interner.internString(null));
	}

	/**
	 * Production-path dedup pin: {@link ResourceLoader#transports()} runs the
	 * interner over every bundled transport TSV, so distinct transports whose
	 * var-requirement sets compare equal must hold the identical Set instance.
	 * Committed transports.tsv permutes rows with identical requirement text;
	 * a scan finding zero sharing means the data shape changed, not the code.
	 */
	@Test
	public void resourceLoaderInternsSharedVarRequirementSets()
	{
		HashMap<Integer, Set<Transport>> transports = ResourceLoader.transports();
		Map<Set<VarRequirement>, Transport> seen = new HashMap<>();
		for (Set<Transport> bucket : transports.values())
		{
			for (Transport transport : bucket)
			{
				Set<VarRequirement> requirements = transport.getVarRequirements();
				if (requirements.isEmpty())
				{
					continue;
				}
				Transport previous = seen.putIfAbsent(requirements, transport);
				if (previous != null && previous != transport)
				{
					assertSame(
						"transports sharing a requirement set must share the interned Set instance",
						previous.getVarRequirements(), requirements);
					return;
				}
			}
		}
		fail("expected committed transports.tsv to share at least one var-requirement set "
			+ "across distinct transports — the interner exists to dedup them");
	}
}
