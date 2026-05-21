package shortestpath.teleports;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.Assert;
import org.junit.Test;

public class EasyTeleportLabelFormatterTest
{
	private final Map<String, String> config = new HashMap<>();
	private final BiFunction<String, String, String> lookup = (group, key) -> config.get(group + "." + key);

	@Test
	public void formatsBookOfTheDeadWithEnabledEasyTeleportsReplacement()
	{
		config.put("easypharaohsceptre.enableKharedstsMemoirs", "true");
		config.put("easypharaohsceptre.replacementLancalliums", "<col=2aae4f>Hosidius</col>");

		String label = EasyTeleportLabelFormatter.format("Book of the dead: Lunch by the Lancalliums", lookup);

		Assert.assertEquals("Book of the dead: Hosidius", label);
	}

	@Test
	public void formatsPharaohsSceptreWithEnabledEasyTeleportsReplacement()
	{
		config.put("easypharaohsceptre.enablePharaohSceptre", "true");
		config.put("easypharaohsceptre.replacementJalsavrah", "Pyramid Plunder");

		String label = EasyTeleportLabelFormatter.format("Pharaoh's sceptre: Jalsavrah", lookup);

		Assert.assertEquals("Pharaoh's sceptre: Pyramid Plunder", label);
	}

	@Test
	public void formatsDiaryCapeNumberedDestinationWithEnabledEasyTeleportsReplacement()
	{
		config.put("easypharaohsceptre.enableDiaryCape", "true");
		config.put("easypharaohsceptre.replacementArdougne", "Ardougne: Bar");

		String label = EasyTeleportLabelFormatter.format("Achievement diary cape: 1. Two-pints", lookup);

		Assert.assertEquals("Achievement diary cape: Ardougne: Bar", label);
	}

	@Test
	public void fallsBackWhenEasyTeleportsToggleIsDisabled()
	{
		config.put("easypharaohsceptre.enableKharedstsMemoirs", "false");
		config.put("easypharaohsceptre.replacementLancalliums", "Hosidius");

		String label = EasyTeleportLabelFormatter.format("Book of the dead: Lunch by the Lancalliums", lookup);

		Assert.assertEquals("Book of the dead: Lunch by the Lancalliums", label);
	}

	@Test
	public void fallsBackWhenReplacementIsMissing()
	{
		config.put("easypharaohsceptre.enablePharaohSceptre", "true");

		String label = EasyTeleportLabelFormatter.format("Pharaoh's sceptre: Jalsavrah", lookup);

		Assert.assertEquals("Pharaoh's sceptre: Jalsavrah", label);
	}

	@Test
	public void fallsBackForUnknownDisplayInfo()
	{
		String label = EasyTeleportLabelFormatter.format("Varrock Teleport: GE", lookup);

		Assert.assertEquals("Varrock Teleport: GE", label);
	}

	@Test
	public void formatsXericsTalismanNumberedDestination()
	{
		config.put("easypharaohsceptre.enableXericsTalisman", "true");
		config.put("easypharaohsceptre.replacementHonour", "Raids");

		String label = EasyTeleportLabelFormatter.format("Xeric's talisman: 5. Xeric's Honour", lookup);

		Assert.assertEquals("Xeric's talisman: Raids", label);
	}

	@Test
	public void formatsRingOfDuelingDestination()
	{
		config.put("easypharaohsceptre.enableRingOfDueling", "true");
		config.put("easypharaohsceptre.replacementFeroxEnclave", "Ferox");

		String label = EasyTeleportLabelFormatter.format("Ring of dueling: Ferox Enclave", lookup);

		Assert.assertEquals("Ring of dueling: Ferox", label);
	}

	@Test
	public void formatsPendantOfAtesNumberedDestination()
	{
		config.put("easypharaohsceptre.enablePendantOfAtes", "true");
		config.put("easypharaohsceptre.replacementDarkfrost", "Darkfrost");

		String label = EasyTeleportLabelFormatter.format("Pendant of ates: 1. The Darkfrost", lookup);

		Assert.assertEquals("Pendant of ates: Darkfrost", label);
	}

	@Test
	public void formatsMaxCapeNestedDestination()
	{
		config.put("easypharaohsceptre.enableMaxCape", "true");
		config.put("easypharaohsceptre.replacementMaxCapeFishingGuild", "Fish Guild");

		String label = EasyTeleportLabelFormatter.format("Max cape: Fishing Teleports: Fishing Guild", lookup);

		Assert.assertEquals("Max cape: Fish Guild", label);
	}
}
