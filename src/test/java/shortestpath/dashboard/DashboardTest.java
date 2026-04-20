package shortestpath.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.Pathfinder;

/**
 * Generic dashboard test harness.
 *
 * <p>Run with {@code -DrunDashboardVerification=true} to enable assertions.
 * Without the flag all tests are skipped; the task still produces the report.</p>
 *
 * <h3>Gradle invocation</h3>
 * <pre>
 * ./gradlew dashboard
 *   -PdashboardDataset=/dashboard/unit-tests.csv
 *   -PdashboardBundle=unit-tests
 * </pre>
 *
 * <h3>System properties</h3>
 * <table>
 *   <tr><th>Property</th><th>Default</th></tr>
 *   <tr><td>{@code dashboard.dataset}</td><td>{@code /dashboard/routes.csv}</td></tr>
 *   <tr><td>{@code dashboard.bundleName}</td><td>{@code routes}</td></tr>
 *   <tr><td>{@code dashboard.title}</td><td>{@code Dashboard}</td></tr>
 *   <tr><td>{@code dashboard.subtitle}</td><td>dataset label</td></tr>
 *   <tr><td>{@code dashboard.profile}</td><td>{@code true}</td></tr>
 *   <tr><td>{@code reachability.maxTargets}</td><td>{@code 10000}</td></tr>
 * </table>
 */
public class DashboardTest
{

	// Universal bank: every item id 0..24999 in qty 1000 – used for BANK preset runs.
	private static final Item[] UNIVERSAL_BANK_ITEMS;

	static
	{
		UNIVERSAL_BANK_ITEMS = new Item[25000];
		for (int i = 0; i < 25000; i++)
		{
			UNIVERSAL_BANK_ITEMS[i] = new Item(i, 1000);
		}
		((Logger) LoggerFactory.getLogger("shortestpath.transport.Transport")).setLevel(Level.OFF);
	}

	private static final String DATASET_PROPERTY = "dashboard.dataset";
	private static final String BUNDLE_NAME_PROPERTY = DashboardBundlePublisher.BUNDLE_NAME_PROPERTY;
	private static final String DEFAULT_DATASET = "/dashboard/routes.csv";
	private static final int MAX_SCENARIOS = Integer.getInteger("reachability.maxTargets", 10000);

	private final DashboardScenarioLoader loader = new DashboardScenarioLoader();
	private final ProfilerReportWriter profilerReportWriter = new ProfilerReportWriter();
	private final PathfinderDashboardReportWriter reportWriter = new PathfinderDashboardReportWriter();
	private final DashboardBundlePublisher bundlePublisher = new DashboardBundlePublisher();

	private Client client;
	private ItemContainer universalBankContainer;
	private Runnable clientBaseline;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getTotalLevel()).thenReturn(2277);
		when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(null);

		universalBankContainer = mock(ItemContainer.class);
		when(universalBankContainer.getItems()).thenReturn(UNIVERSAL_BANK_ITEMS);

		// Capture current stub state as the per-scenario baseline Runnable
		clientBaseline = () ->
		{
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getClientThread()).thenReturn(Thread.currentThread());
			when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
			when(client.getTotalLevel()).thenReturn(2277);
			when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
			when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
			when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
			when(client.getItemContainer(InventoryID.WORN)).thenReturn(null);
		};
	}

	@Test
	public void run() throws IOException
	{
		String dataset = System.getProperty(DATASET_PROPERTY, DEFAULT_DATASET);
		boolean profile = Boolean.parseBoolean(System.getProperty("dashboard.profile", "true"));
		String bundleName = System.getProperty(BUNDLE_NAME_PROPERTY, "routes");
		String reportTitle = System.getProperty("dashboard.title", "Dashboard");
		String reportSubtitle = System.getProperty("dashboard.subtitle", datasetLabel(dataset));
		Path siteRoot = bundlePublisher.getOutputRoot();

		List<DashboardScenario> allScenarios = loadScenarios(dataset);
		List<DashboardScenario> scenarios = allScenarios.subList(0, Math.min(MAX_SCENARIOS, allScenarios.size()));

		List<PathfinderDashboardModels.RunRecord> runs = new ArrayList<>();
		Map<String, Integer> capturedLengths = new LinkedHashMap<>();
		long started = System.currentTimeMillis();

		int scenarioIndex = 0;
		for (DashboardScenario scenario : scenarios)
		{
			scenarioIndex++;

			DashboardScenarioRunner.ApplyResult applied = DashboardScenarioRunner.apply(
				scenario, client, clientBaseline, universalBankContainer);

			// Default to the Grand Exchange bank when no explicit start is set (e.g. clue-step CSV rows)
			int start = scenario.getStartPoint() != WorldPointUtil.UNDEFINED
				? scenario.getStartPoint()
				: WorldPointUtil.packWorldPoint(3185, 3436, 0);
			int end = scenario.getEndPoint();
			String category = scenario.getCategory() != null && !scenario.getCategory().isEmpty()
				? scenario.getCategory()
				: "dashboard";

			PathfinderResult result;
			PathfinderProfile profileData = null;
			if (profile)
			{
				ProfilingPathfinder profiler = new ProfilingPathfinder(
					applied.pathfinderConfig, start, Set.of(end));
				profiler.run();
				result = profiler.getResult();
				profileData = profiler.getProfile();
			}
			else
			{
				Pathfinder pathfinder = new Pathfinder(applied.pathfinderConfig, start, Set.of(end));
				pathfinder.run();
				result = pathfinder.getResult();
			}

			if (result == null)
			{
				System.out.printf("[%2d/%-2d] \u2716 %s  NO_RESULT%n",
					scenarioIndex, scenarios.size(), scenario.getName());
				continue;
			}

			List<PathStep> path = result.getPathSteps();
			int pathLength = path.size();
			boolean reached = isReachedOrAdjacent(result, end);
			if (reached)
			{
				capturedLengths.put(scenario.getName(), pathLength);
			}

			// Evaluate assertions
			Boolean assertionPassed = null;
			String assertionMessage = null;
			OptionalInt expectedLength = scenario.getExpectedLength();
			OptionalInt minimumLength = scenario.getMinimumLength();
			if (expectedLength.isPresent())
			{
				int expected = expectedLength.getAsInt();
				if (pathLength == expected)
				{
					assertionPassed = true;
				}
				else
				{
					assertionPassed = false;
					assertionMessage = "Expected path length " + expected + " but got " + pathLength;
				}
			}
			else if (minimumLength.isPresent())
			{
				int minimum = minimumLength.getAsInt();
				if (pathLength >= minimum)
				{
					assertionPassed = true;
				}
				else
				{
					assertionPassed = false;
					assertionMessage = "Expected minimum path length " + minimum + " but got " + pathLength;
				}
			}

			List<String> details = List.of(
				"Dataset: " + datasetLabel(dataset),
				"Scenario: " + scenario.getName(),
				"Preset: " + scenario.getPreset(),
				"Expected reachable: true");

			PathfinderDashboardModels.RunRecord run = reportWriter.createRunRecord(
				scenario.getName(),
				category,
				details,
				result,
				applied.pathfinderConfig,
				reached,
				assertionPassed,
				assertionMessage);

			DashboardRunMetadata.apply(run, scenario.getPreset(), applied.dashboardConfig,
				applied.lumbridgeDiaryEliteStub);

			if (profileData != null)
			{
				profilerReportWriter.populateProfilerData(run, profileData);
			}
			bundlePublisher.externalizeRunHeatmap(bundleName, runs.size(), run);
			runs.add(run);

			System.out.printf("[%2d/%-2d] %s %s  %.0fms  %d steps%n",
				scenarioIndex, scenarios.size(),
				reached ? "\u2714" : "\u2716",
				scenario.getName(),
				result.getElapsedNanos() / 1_000_000.0,
				pathLength);

		}

		PathfinderDashboardModels.Report report = reportWriter.createReport(
			reportTitle,
			reportSubtitle,
			System.currentTimeMillis() - started,
			runs,
			reportWriter.createTransportLayerPointsAlwaysAvailable());
		report.bankNamesFromData = BankDestinationLabels.uniqueSortedBankNames();

		bundlePublisher.publishBundle(bundleName, report);

		String sourceResourcesDir = System.getProperty("dashboard.sourceResourcesDir");
		if (sourceResourcesDir != null && dataset.startsWith("/"))
		{
			Path csvPath = Paths.get(sourceResourcesDir).resolve(dataset.substring(1));
			updateExpectedLengths(csvPath, capturedLengths);
			System.out.println("Captured expected_length for " + capturedLengths.size()
				+ " route(s) in " + csvPath);
		}

		System.out.println("Dashboard run summary:");
		System.out.println(" - tested: " + scenarios.size() + "/" + allScenarios.size());
		System.out.println(" - dataset: " + dataset);

		long unreachable = runs.stream().filter(r -> !r.reached).count();
		if (unreachable > 0)
		{
			System.out.printf("%d unreachable target(s). See %s%n", unreachable, siteRoot.resolve("index.html"));
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private List<DashboardScenario> loadScenarios(String dataset) throws IOException
	{
		if (dataset.startsWith("/"))
		{
			return loader.loadFromResource(dataset);
		}
		return loader.loadFromCsv(Paths.get(dataset));
	}

	private static String datasetLabel(String dataset)
	{
		if (dataset.startsWith("/"))
		{
			return dataset;
		}
		Path path = Paths.get(dataset);
		return path.getFileName() != null ? path.getFileName().toString() : dataset;
	}

	private static void updateExpectedLengths(Path csvPath, Map<String, Integer> lengths) throws IOException
	{
		List<String> lines = Files.readAllLines(csvPath);
		if (lines.isEmpty())
		{
			return;
		}
		String[] headers = lines.get(0).split(",", -1);
		int nameIdx = -1;
		int expectedLengthIdx = -1;
		for (int i = 0; i < headers.length; i++)
		{
			if ("name".equals(headers[i]))
			{
				nameIdx = i;
			}
			if ("expected_length".equals(headers[i]))
			{
				expectedLengthIdx = i;
			}
		}
		if (nameIdx < 0 || expectedLengthIdx < 0)
		{
			return;
		}
		List<String> updated = new ArrayList<>();
		updated.add(lines.get(0));
		for (int i = 1; i < lines.size(); i++)
		{
			String line = lines.get(i);
			String[] cols = line.split(",", -1);
			if (nameIdx < cols.length)
			{
				Integer length = lengths.get(cols[nameIdx]);
				if (length != null)
				{
					String[] expanded = cols.length > expectedLengthIdx
							? cols
							: Arrays.copyOf(cols, expectedLengthIdx + 1);
					expanded[expectedLengthIdx] = String.valueOf(length);
					line = String.join(",", expanded);
				}
			}
			updated.add(line);
		}
		Files.write(csvPath, updated);
	}

	private static boolean isReachedOrAdjacent(PathfinderResult result, int target)
	{
		if (result == null || result.getPathSteps().isEmpty())
		{
			return false;
		}
		List<PathStep> path = result.getPathSteps();
		return WorldPointUtil.distanceBetween(
			path.get(path.size() - 1).getPackedPosition(), target) <= 1;
	}

	private static String formatBankEventsSummary(PathfinderDashboardModels.RunRecord run)
	{
		if (run.bankEvents == null || run.bankEvents.isEmpty())
		{
			return "none";
		}
		return run.bankEvents.stream()
			.map(ev ->
			{
				String name = ev.bankName != null ? ev.bankName : "unknown";
				return name + " (step " + ev.stepIndex + ")";
			})
			.collect(Collectors.joining(", "));
	}
}
