package shortestpath;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Non-growth dependency-rule lint: the plugin's leaf packages
 * ({@code transport}, {@code pathfinder}, {@code requirement},
 * {@code leagues}, {@code overlay}, {@code settings} — subtrees included)
 * must not reference
 * {@code ShortestPathPlugin}. Cross-cutting configuration and world-geometry
 * access goes through owned services (the effective-config settings service,
 * the POH service) or leaf utilities; any new coupling arrives through an
 * injected seam instead.
 *
 * <p>Every pre-existing violation is frozen in {@link #ALLOWLIST}, keyed by
 * {@code path:line} relative to {@code src/main/java/}. The
 * set-equality assertion makes the lint self-maintaining: extraction work
 * <em>removes</em> entries as each site migrates — entries are removed,
 * never added.
 *
 * <p>The same freeze applies to leaf-to-leaf coupling: {@link #LEAF_EDGES}
 * records every package pair linked by a leaf-package import, so a new edge
 * — mutual or one-way — fails the build until it is justified in the table. The resource-anchor
 * call sites moved into {@code ResourceLoader} when the data
 * loader landed self-anchored. An unlisted reference site fails the build
 * (new coupling); an allowlist key with no matching site fails it too
 * (stale entry — remove it in the same change that removed the reference).
 *
 * <p>Run with {@code ./gradlew test --tests
 * 'shortestpath.PluginDependencyRuleTest'}. The lint lives in this repo's
 * own test tree, so it rides the regular {@code test} task and fails for
 * anyone working directly in the plugin — not only in the composite
 * harness, which also names it as a blocking filter in its verify lint
 * tier and CI's dependency-boundary step. Gradle fails a
 * {@code --tests} selection that matches no class, so the gate cannot
 * pass vacuously.
 *
 * <p>{@link #observedSites(Path)} is factored out so
 * {@link #flagsUnlistedCoupling()} can prove both failure branches against
 * temporary fixture roots — the submodule tree is never written to.
 *
 * <p>Known gaps, by design. The original scan token is the dotted form
 * {@code ShortestPathPlugin.} — static coupling only, and a raw substring
 * at that. Instance coupling is now covered separately by
 * {@link #leafToPluginTypeReferences()}, which sees imports, parameter
 * types, and {@code plugin.} reads on comment-stripped lines; its
 * {@link #TYPE_REFERENCE_EXCEPTIONS} table would hold any adjudicated
 * residual; it is empty — {@code DebugOverlayPanel}'s
 * {@code super(plugin)} seam was the last one and it retired with the
 * panel's move onto the no-arg {@code OverlayPanel} ctor.
 * The last allowlist entry retired with the resource
 * anchors' migration to the self-anchored loader, so the formerly
 * vacuous-guard assertion in {@link #scanHasReach()} was relaxed in the
 * same change; the freeze mechanism stays — any new
 * {@code ShortestPathPlugin.} site in a leaf package still fails the
 * build.
 *
 * <p>Two maintenance properties to know before touching allowlisted code.
 * The match is a raw substring scan — comments and string literals
 * containing {@code ShortestPathPlugin.} count as sites. And keys are
 * {@code path:line}, so edits above an allowlisted reference shift its
 * line number and must update the affected keys in the same change; that
 * line-granularity is what lets the lint detect stale entries.
 */
public class PluginDependencyRuleTest
{
	private static final Path MAIN_ROOT = Paths.get("src/main/java");

	// New leaf packages must be added here — the lint only covers what it enumerates.
	private static final List<String> LEAF_PACKAGES = List.of(
		"transport", "pathfinder", "requirement", "leagues", "overlay",
		"settings", "items", "spirittree", "poh", "scheduler", "sailing",
		"widgets", "api", "presentation");

	private static final String PLUGIN_REFERENCE = "ShortestPathPlugin.";

	/**
	 * Frozen census of existing {@code ShortestPathPlugin} reference sites in
	 * the leaf packages. Key = {@code path:line} relative to
	 * {@code src/main/java/}; value = justification + owning
	 * migration. Extraction work removes entries as sites migrate — entries
	 * are removed, never added.
	 */
	private static final Map<String, String> ALLOWLIST = new TreeMap<>();

	/**
	 * Matches an import of a leaf-package type:
	 * {@code import [static] shortestpath.<pkg>.<Type>}. The captured group
	 * is the first package segment, so subpackage imports
	 * ({@code shortestpath.requirement.model.X}) resolve to their leaf.
	 */
	private static final Pattern LEAF_IMPORT =
		Pattern.compile("^\\s*import\\s+(?:static\\s+)?shortestpath\\.(\\w+)\\.");

	/**
	 * Frozen census of leaf-to-leaf package edges. Key = {@code "from -> to"}
	 * — observed when a source file under leaf package {@code from} imports
	 * {@code shortestpath.<to>.}; value = justification. Like
	 * {@link #ALLOWLIST} the table is self-maintaining: new coupling fails
	 * the build until it is justified here, and an entry whose edge
	 * disappeared fails until it is removed in the same change.
	 *
	 * <p>The {@code items} pairs are the extraction's consumer/producer
	 * seams: {@code items -> requirement}/{@code requirement -> items}
	 * share the eligibility snapshot and player-state source, and
	 * {@code pathfinder -> items} is {@code PathfinderConfig} holding the
	 * {@code ItemStateService} reference (revision counter + bank state)
	 * for the config-owned bank-pickup projection. The
	 * {@code spirittree -> requirement} edge is the service reading
	 * {@code PlayerStateSource} for the availability refresh and the
	 * patch table in {@code SpiritTreePatches}; requirement code consumes
	 * the patch table directly and asks {@code ClientPlayerStateSource}
	 * for the modal-widget state, so no requirement -> spirittree edge
	 * remains.
	 */
	private static final Map<String, String> LEAF_EDGES = new TreeMap<>();

	static
	{
		// items package — the extraction's consumer/producer seams.
		LEAF_EDGES.put("items -> requirement",
			"ItemStateService.collectEligibility shares PlayerStateSource/TransportEligibility/Unlock; BankPickupResult is shared from presentation/BankPickupRequirements");
		LEAF_EDGES.put("items -> settings",
			"ItemChange/ItemStateService read the TeleportationItem setting and Effect facts");
		LEAF_EDGES.put("pathfinder -> items",
			"PathfinderConfig holds the ItemStateService reference (revision counter and bank state) for the config-owned bank-pickup projection");
		LEAF_EDGES.put("requirement -> items",
			"ClientPlayerStateSource/RequirementContext read OwnedItems and collectEligibility");

		// spirittree package — the extraction's seams.
		LEAF_EDGES.put("pathfinder -> spirittree",
			"PathfinderConfig holds the SpiritTreeService reference");
		LEAF_EDGES.put("spirittree -> requirement",
			"SpiritTreeService reads PlayerStateSource for the availability refresh and SpiritTreePatches for the travel-menu rows");
		LEAF_EDGES.put("spirittree -> settings",
			"TreeChange/SpiritTreeService read Effect facts and the teleportation settings");
		LEAF_EDGES.put("spirittree -> widgets",
			"SpiritTreeService reads the travel-menu row texts through the injected GameWidgets seam");

		// poh package — the extraction's seams. The inbound edges are the
		// consumers that used to read the shell statics; the outbound edges
		// are the service's reads of sibling leaf types.
		LEAF_EDGES.put("overlay -> poh",
			"PathTileOverlay reads PohService display/exit-info output");
		LEAF_EDGES.put("pathfinder -> poh",
			"PathfinderConfig/TransportAvailability delegate POH destination and transport remaps to PohService");
		LEAF_EDGES.put("poh -> settings",
			"PohChange/PohService carry Effect facts and share the Settings config-group constant");
		LEAF_EDGES.put("poh -> transport",
			"PohService remap signatures read Transport/TransportType");
		LEAF_EDGES.put("poh -> widgets",
			"PohService reads the nexus keyed-row and slot-list texts through the injected GameWidgets seam");
		LEAF_EDGES.put("requirement -> poh",
			"RoutingPolicy/Requirements delegate the POH gates' enablement reads to PohService");
		LEAF_EDGES.put("settings -> poh",
			"Settings/EffectiveConfig expose POH-facing effective-config reads");
		LEAF_EDGES.put("transport -> poh",
			"TransportTypeConfig reads POH-facing config types");

		// scheduler package — the extraction's seams. The outbound edges are
		// the engine inputs and the attach seam; the inbound edge is the
		// debug overlay reading the injectable diagnostics state.
		LEAF_EDGES.put("overlay -> scheduler",
			"DebugOverlayPanel injects and reads DebugState directly");
		LEAF_EDGES.put("scheduler -> items",
			"PathScheduler.attach takes the ItemStateService for refresh routing");
		LEAF_EDGES.put("scheduler -> pathfinder",
			"PathScheduler drives Pathfinder/ActiveSearch/PathfinderConfig/PathStep");
		// The coordinator extraction adds the declared-fact channels:
		// producers inject RefreshCoordinator (inbound) and the coordinator
		// reads the fact types it translates (outbound).
		LEAF_EDGES.put("items -> scheduler",
			"ItemStateService declares ItemChange facts to the injected RefreshCoordinator");
		LEAF_EDGES.put("poh -> scheduler",
			"PohService declares PohChange facts to the injected RefreshCoordinator");
		LEAF_EDGES.put("spirittree -> scheduler",
			"SpiritTreeService declares TreeChange facts to the injected RefreshCoordinator");
		LEAF_EDGES.put("scheduler -> poh",
			"RefreshCoordinator reads PohChange/PohService through the poh channel");
		LEAF_EDGES.put("scheduler -> settings",
			"RefreshCoordinator reads ConfigChange/ShortestPathConfig through the config channel");
		LEAF_EDGES.put("scheduler -> spirittree",
			"RefreshCoordinator reads TreeChange through the spirit-tree channel");
		// sailing package — the sailing service's seams. The outbound edges
		// are the producer channel plus the service's reads of sibling leaf
		// types; the inbound edges are the coordinator/scheduler/config
		// consumers.
		LEAF_EDGES.put("pathfinder -> sailing",
			"PathfinderConfig holds the SailingService seam and derives dispatch inputs from SailingState");
		LEAF_EDGES.put("sailing -> scheduler",
			"SailingService declares SailingChange facts to the injected RefreshCoordinator");
		LEAF_EDGES.put("sailing -> requirement",
			"SailingService reads PlayerStateSource and builds a ClientPlayerStateSource on the event arm");
		LEAF_EDGES.put("sailing -> settings",
			"SailingChange carries Effect values");
		LEAF_EDGES.put("scheduler -> sailing",
			"RefreshCoordinator admits SailingChange; PathScheduler/PathRenderState read SailingService/SailingState");

		// api + presentation packages — the plugin-message/menu-verb and
		// transport-presentation extraction's seams. The api leaf's outbound
		// edges are the QueryResponder contract plus the serializers' reads
		// of the injected seams; the presentation leaf's outbound edges are
		// the annotator's edge-walk inputs. The inbound edges are the
		// consumers that read BankPickupResult at its new home after the
		// requirement/ -> presentation/ whole-type move.
		LEAF_EDGES.put("api -> pathfinder",
			"PluginMessageHandler serializers walk PathStep/PathfinderResult/ActiveSearch; MenuVerbs reads ActiveSearch/PathStep");
		LEAF_EDGES.put("api -> requirement",
			"PluginMessageTargets reads ItemRequirement/TransportItems at their requirement/model home");
		LEAF_EDGES.put("api -> presentation",
			"PluginMessageHandler serializers and MenuVerbs annotate edges through the injected PathAnnotator");
		LEAF_EDGES.put("api -> scheduler",
			"PluginMessageHandler implements the QueryResponder seam and reads PathScheduler/RefreshCoordinator state");
		LEAF_EDGES.put("api -> settings",
			"PluginMessageHandler reads Settings for CONFIG_GROUP and the config-override echo path");
		LEAF_EDGES.put("api -> transport",
			"the transports serializer emits Transport records in the wire payload");
		LEAF_EDGES.put("api -> widgets",
			"MenuVerbs reads the injected WidgetGeometry seam (getSelectedWorldPoint)");
		LEAF_EDGES.put("overlay -> presentation",
			"overlays read the PathAnnotator seam and BankPickupRequirements/BankPickupResult at its new presentation home");
		LEAF_EDGES.put("overlay -> widgets",
			"PathMapOverlay/PathMinimapOverlay/PathMapTooltipOverlay read the injected WidgetGeometry for map/minimap geometry");
		LEAF_EDGES.put("pathfinder -> presentation",
			"PathfinderConfig owns BankPickupProjection, whose getBankPickup signature references BankPickupResult");
		LEAF_EDGES.put("presentation -> items",
			"PathAnnotator reads bank-open state through the injected ItemStateService");
		LEAF_EDGES.put("presentation -> pathfinder",
			"PathAnnotator walks edges via PathStep/PathfinderConfig/TransportAvailability; BankPickupRequirements keeps its pathfinder reads");
		LEAF_EDGES.put("presentation -> poh",
			"PathAnnotator reads PohService for POH exit info and inside-POH display policy");
		LEAF_EDGES.put("presentation -> requirement",
			"BankPickupRequirements carries its TransportEligibility/TransportItems reads through the requirement/ move; PathAnnotator reads TransportEligibility");
		LEAF_EDGES.put("presentation -> transport",
			"PathAnnotator/BankPickupRequirements read Transport/TransportType for display formatting and eligibility");

		// Pre-existing edges frozen at lint introduction.
		LEAF_EDGES.put("leagues -> requirement",
			"LeagueModeState builds requirement checks");
		LEAF_EDGES.put("leagues -> transport",
			"LeagueRegionChecker delegates the regions TSV load to ResourceLoader");
		LEAF_EDGES.put("overlay -> pathfinder",
			"overlays read PathStep/PathfinderConfig path state");
		LEAF_EDGES.put("overlay -> requirement",
			"highlight overlays read requirement evaluation results");
		LEAF_EDGES.put("overlay -> settings",
			"overlays read config-facing settings types");
		LEAF_EDGES.put("overlay -> transport",
			"overlays read Transport records and destination geometry");
		LEAF_EDGES.put("pathfinder -> leagues",
			"league-region restrictions gate the search");
		LEAF_EDGES.put("pathfinder -> requirement",
			"PathfinderConfig consumes requirement evaluation");
		LEAF_EDGES.put("pathfinder -> settings",
			"PathfinderConfig reads effective-config settings");
		LEAF_EDGES.put("pathfinder -> transport",
			"the search consumes Transport/TransportAvailability data");
		LEAF_EDGES.put("requirement -> leagues",
			"requirements consult league-region checks");
		LEAF_EDGES.put("requirement -> pathfinder",
			"Requirements/TransportEligibility read BankVisitState; BankPickupRequirements moved to presentation");
		LEAF_EDGES.put("requirement -> settings",
			"RoutingPolicy/TransportEligibility read settings types");
		LEAF_EDGES.put("requirement -> transport",
			"requirements evaluate Transport records");
		LEAF_EDGES.put("settings -> pathfinder",
			"Settings/EffectiveConfig expose pathfinder-facing config");
		LEAF_EDGES.put("settings -> requirement",
			"Settings/EffectiveConfig expose requirement-facing config");
		LEAF_EDGES.put("settings -> transport",
			"Settings/EffectiveConfig expose transport-facing config");
		LEAF_EDGES.put("transport -> leagues",
			"Transport records reference league regions");
		LEAF_EDGES.put("transport -> requirement",
			"transport parsers build requirement models");
		LEAF_EDGES.put("transport -> settings",
			"TransportTypeConfig reads settings types");
	}

	/**
	 * Word-boundary token for the plugin class name — catches import
	 * lines, parameter/field types, {@code ShortestPathPlugin.class},
	 * and static accesses alike. Applied to comment-stripped lines so
	 * javadoc design notes do not count as coupling sites.
	 */
	private static final Pattern PLUGIN_TYPE_TOKEN =
		Pattern.compile("\\bShortestPathPlugin\\b");

	/**
	 * Word-boundary token for instance reads through a {@code plugin}
	 * field or parameter ({@code plugin.foo(...)},
	 * {@code plugin.foo = ...}) — the instance-coupling half of the
	 * type-reference scan. Also on comment-stripped lines.
	 */
	private static final Pattern PLUGIN_READ_TOKEN =
		Pattern.compile("\\bplugin\\.");

	/**
	 * Field-declaration shape for the public-mutable-field scan:
	 * {@code public [modifier-or-annotation...] <type> <name>
	 * [=|;|,]}. {@code public static} and {@code public final}
	 * declarations are excluded by the lookahead on the first
	 * post-{@code public} word; the admitted pre-type tokens are
	 * {@code volatile}, {@code transient}, and annotations, so
	 * {@code public volatile int x}, {@code public transient Foo f},
	 * {@code public @Nullable Foo f}, multi-declarator
	 * {@code public int a, b}, and column-0 declarations all match.
	 * A {@code static}/{@code final} word behind an annotation or
	 * {@code volatile} still misses the tail ({@code public @Ann
	 * final int x} reads {@code final} as the type), which keeps
	 * those exempt too. Package-private seams and accessor-mediated
	 * mutation are outside this grammar by design (a recorded
	 * coverage limit).
	 */
	private static final Pattern PUBLIC_FIELD =
		Pattern.compile("^\\s*public\\s+(?!static\\b|final\\b)"
			+ "(?:(?:volatile|transient)\\s+|@[\\w.]+(?:\\([^)]*\\))?\\s+)*"
			+ "[A-Za-z0-9_<>,.\\[\\]]+\\s+[a-zA-Z_]\\w*\\s*[=;,]");

	/**
	 * Injection marker for the mutable-field scan: a
	 * {@code public} field whose own declaration line carries
	 * {@code @Inject} (e.g. {@code public @Inject Foo f;}) is
	 * exempt — RuneLite's injector owns those references.
	 */
	private static final Pattern INJECT_ANNOTATION = Pattern.compile("@Inject\\b");

	/**
	 * A line that is only an {@code @Inject} annotation —
	 * optionally with arguments — and nothing else. Only this
	 * standalone form exempts the field declared on the next
	 * non-blank line: a previous line that merely contains
	 * {@code @Inject} (e.g. a compact same-line injected member
	 * such as {@code @Inject private Foo a;}) annotates a
	 * different member and must not bleed its exemption onto the
	 * following declaration.
	 */
	private static final Pattern INJECT_ONLY_LINE =
		Pattern.compile("^\\s*@Inject(?:\\([^)]*\\))?\\s*$");

	/**
	 * Named exceptions for {@link #leafToPluginTypeReferences()} —
	 * {@code path:line} keys, each naming the adjudicated reason the
	 * site survives. Removing a seam without removing its entry fails
	 * the scan on the stale-entry arm, so the table cannot silently
	 * outlive the code it covers.
	 */
	private static final Map<String, String> TYPE_REFERENCE_EXCEPTIONS = new TreeMap<>();

	/**
	 * Approved sites for {@link #subpackageToPluginStaticCoupling()} —
	 * {@code path:line} keys. Empty today: no subpackage may reach the
	 * plugin class statically.
	 */
	private static final Map<String, String> SUBPACKAGE_STATIC_EXCEPTIONS = new TreeMap<>();

	/**
	 * Approved sites for {@link #publicMutableFieldSeams()} —
	 * {@code path:line} keys. Empty today: leaf packages expose no
	 * public mutable state.
	 */
	private static final Map<String, String> MUTABLE_FIELD_EXCEPTIONS = new TreeMap<>();

	static
	{
	}

	/**
	 * Scan the leaf packages under {@code mainRoot} (expected layout:
	 * {@code <mainRoot>/shortestpath/<pkg>} plus nested subdirectories) and
	 * return the sorted
	 * set of {@code path:line} keys — one per source line containing a
	 * {@code ShortestPathPlugin.} reference, paths relative to
	 * {@code mainRoot}. A line with several references yields one key.
	 */
	static SortedSet<String> observedSites(Path mainRoot) throws IOException
	{
		SortedSet<String> sites = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		for (String pkg : LEAF_PACKAGES)
		{
			Path dir = base.resolve(pkg);
			if (!Files.isDirectory(dir))
			{
				continue;
			}
			List<Path> sources;
			try (Stream<Path> stream = Files.walk(dir))
			{
				sources = stream
					.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
					.collect(Collectors.toList());
			}
			for (Path source : sources)
			{
				String rel = mainRoot.relativize(source).toString()
					.replace(File.separatorChar, '/');
				List<String> lines = Files.readAllLines(source);
				for (int i = 0; i < lines.size(); i++)
				{
					if (lines.get(i).contains(PLUGIN_REFERENCE))
					{
						sites.add(rel + ":" + (i + 1));
					}
				}
			}
		}
		return sites;
	}

	/**
	 * Scan the leaf packages under {@code mainRoot} for leaf-to-leaf edges
	 * and return the sorted set of {@code "from -> to"} keys — one per leaf
	 * package pair where a source file in {@code from} has an
	 * {@code import shortestpath.<to>.} line. Same-package imports and
	 * imports of non-leaf packages are ignored; fully-qualified references
	 * without an import and javadoc links are not counted — the same
	 * known-gap policy as {@link #observedSites(Path)}.
	 */
	static SortedSet<String> observedEdges(Path mainRoot) throws IOException
	{
		SortedSet<String> edges = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		for (String fromPkg : LEAF_PACKAGES)
		{
			Path dir = base.resolve(fromPkg);
			if (!Files.isDirectory(dir))
			{
				continue;
			}
			List<Path> sources;
			try (Stream<Path> stream = Files.walk(dir))
			{
				sources = stream
					.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
					.collect(Collectors.toList());
			}
			for (Path source : sources)
			{
				for (String line : Files.readAllLines(source))
				{
					Matcher m = LEAF_IMPORT.matcher(line);
					if (m.find())
					{
						String toPkg = m.group(1);
						if (!toPkg.equals(fromPkg) && LEAF_PACKAGES.contains(toPkg))
						{
							edges.add(fromPkg + " -> " + toPkg);
						}
					}
				}
			}
		}
		return edges;
	}

	/**
	 * Strip comment text from {@code line}, updating {@code inBlock} —
	 * the one-element block-comment carry state across lines. String
	 * and char literals are preserved verbatim so {@code //} or
	 * {@code /*} inside quotes cannot truncate the scan, and a token
	 * inside a literal still counts as a site (same conservative
	 * policy as {@link #observedSites(Path)}: a literal mentioning the
	 * plugin counts until argued otherwise). Token scans mean "code
	 * references", so javadoc and line comments must be invisible —
	 * {@code overlay/ArrowHead}'s "port-tasks plugin." javadoc and
	 * {@code pathfinder/sailing/BoatHull}'s {@code ShortestPathPlugin}
	 * design comment are the motivating false positives.
	 */
	private static String stripComments(String line, boolean[] inBlock)
	{
		StringBuilder out = new StringBuilder(line.length());
		boolean inString = false;
		boolean inChar = false;
		for (int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			if (inBlock[0])
			{
				if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/')
				{
					inBlock[0] = false;
					i++;
				}
				continue;
			}
			if (inString || inChar)
			{
				out.append(c);
				if (c == '\\' && i + 1 < line.length())
				{
					out.append(line.charAt(++i));
				}
				else if (inString && c == '"')
				{
					inString = false;
				}
				else if (inChar && c == '\'')
				{
					inChar = false;
				}
				continue;
			}
			if (c == '"')
			{
				inString = true;
				out.append(c);
				continue;
			}
			if (c == '\'')
			{
				inChar = true;
				out.append(c);
				continue;
			}
			if (c == '/' && i + 1 < line.length())
			{
				if (line.charAt(i + 1) == '/')
				{
					break;
				}
				if (line.charAt(i + 1) == '*')
				{
					inBlock[0] = true;
					i++;
					continue;
				}
			}
			out.append(c);
		}
		return out.toString();
	}

	/**
	 * All {@code .java} files under {@code dir} (recursive), or none
	 * when the directory does not exist — the walk shared by the
	 * token-scan variants.
	 */
	private static List<Path> javaSources(Path dir) throws IOException
	{
		if (!Files.isDirectory(dir))
		{
			return Collections.emptyList();
		}
		try (Stream<Path> stream = Files.walk(dir))
		{
			return stream
				.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
				.collect(Collectors.toList());
		}
	}

	/**
	 * {@code path:line}-style path key for a source file —
	 * {@code mainRoot}-relative with {@code /} separators.
	 */
	private static String relKey(Path mainRoot, Path source)
	{
		return mainRoot.relativize(source).toString().replace(File.separatorChar, '/');
	}

	/**
	 * Scan the leaf packages for {@code ShortestPathPlugin} type
	 * references — imports, parameter/field types,
	 * {@code .class} literals, static accesses — and for
	 * {@code plugin.} instance reads, on comment-stripped lines, and
	 * return the sorted set of {@code path:line} keys. This is the
	 * scan that sees the instance-coupling residual the dotted-token
	 * {@link #observedSites(Path)} scan deliberately does not count.
	 */
	static SortedSet<String> observedTypeReferences(Path mainRoot) throws IOException
	{
		SortedSet<String> sites = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		for (String pkg : LEAF_PACKAGES)
		{
			for (Path source : javaSources(base.resolve(pkg)))
			{
				String rel = relKey(mainRoot, source);
				List<String> lines = Files.readAllLines(source);
				boolean[] inBlock = {false};
				for (int i = 0; i < lines.size(); i++)
				{
					String code = stripComments(lines.get(i), inBlock);
					if (PLUGIN_TYPE_TOKEN.matcher(code).find()
						|| PLUGIN_READ_TOKEN.matcher(code).find())
					{
						sites.add(rel + ":" + (i + 1));
					}
				}
			}
		}
		return sites;
	}

	/**
	 * Scan <em>every</em> {@code .java} file in a subdirectory of
	 * {@code mainRoot/shortestpath} — leaf packages and any
	 * unclassified package alike — for {@code ShortestPathPlugin.}
	 * static accesses on comment-stripped lines, and return the
	 * sorted set of {@code path:line} keys. Root-package files are
	 * excluded: shell classes legitimately reference the plugin
	 * ({@code @ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)},
	 * self-references inside {@code ShortestPathPlugin} itself). An
	 * unclassified new package cannot smuggle coupling past this
	 * scan — the census fails on the package itself, and this scan
	 * fails on the reference.
	 */
	static SortedSet<String> observedSubpackageStaticCoupling(Path mainRoot) throws IOException
	{
		SortedSet<String> sites = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		if (!Files.isDirectory(base))
		{
			return sites;
		}
		List<Path> sources;
		try (Stream<Path> stream = Files.walk(base))
		{
			sources = stream
				.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
				.filter(p -> !p.getParent().equals(base))
				.collect(Collectors.toList());
		}
		for (Path source : sources)
		{
			String rel = relKey(mainRoot, source);
			List<String> lines = Files.readAllLines(source);
			boolean[] inBlock = {false};
			for (int i = 0; i < lines.size(); i++)
			{
				if (stripComments(lines.get(i), inBlock).contains(PLUGIN_REFERENCE))
				{
					sites.add(rel + ":" + (i + 1));
				}
			}
		}
		return sites;
	}

	/**
	 * Scan the leaf packages for public mutable field declarations —
	 * the {@link #PUBLIC_FIELD} grammar covers the bare, column-0,
	 * {@code volatile}/{@code transient}, annotation-bearing, and
	 * multi-declarator forms — on comment-stripped lines and return
	 * the sorted set of {@code path:line} keys. {@code public
	 * static} / {@code public final} cannot match, and a declaration
	 * carrying {@code @Inject} on its own line or directly under a
	 * standalone {@code @Inject} annotation line is exempt —
	 * RuneLite's injector owns those references. An
	 * {@code @Inject} that decorates a different member on the
	 * previous line does not bleed the exemption across.
	 */
	static SortedSet<String> observedPublicMutableFields(Path mainRoot) throws IOException
	{
		SortedSet<String> sites = new TreeSet<>();
		Path base = mainRoot.resolve("shortestpath");
		for (String pkg : LEAF_PACKAGES)
		{
			for (Path source : javaSources(base.resolve(pkg)))
			{
				String rel = relKey(mainRoot, source);
				List<String> lines = Files.readAllLines(source);
				boolean[] inBlock = {false};
				String previous = "";
				for (int i = 0; i < lines.size(); i++)
				{
					String code = stripComments(lines.get(i), inBlock);
					if (PUBLIC_FIELD.matcher(code).find()
						&& !INJECT_ANNOTATION.matcher(code).find()
						&& !INJECT_ONLY_LINE.matcher(previous).find())
					{
						sites.add(rel + ":" + (i + 1));
					}
					if (!code.trim().isEmpty())
					{
						previous = code;
					}
				}
			}
		}
		return sites;
	}

	/**
	 * The set of package names directly under
	 * {@code mainRoot/shortestpath} — directories only; the
	 * root-package files ({@code ShortestPathPlugin},
	 * {@code ShortestPathConfig}, the shared utilities) are not
	 * packages and never count. A missing {@code shortestpath}
	 * directory degrades to the empty set — the same policy as the
	 * other walkers — so the census reports every declared leaf as
	 * missing instead of erroring on the walk itself.
	 */
	static SortedSet<String> observedLeafPackages(Path mainRoot) throws IOException
	{
		Path base = mainRoot.resolve("shortestpath");
		if (!Files.isDirectory(base))
		{
			return Collections.emptySortedSet();
		}
		try (Stream<Path> stream = Files.list(base))
		{
			return stream.filter(Files::isDirectory)
				.map(p -> p.getFileName().toString())
				.collect(Collectors.toCollection(TreeSet::new));
		}
	}

	@Test
	public void leafPackagesDoNotReferencePlugin() throws IOException
	{
		SortedSet<String> observed = observedSites(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(ALLOWLIST.keySet());

		SortedSet<String> stale = new TreeSet<>(ALLOWLIST.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new leaf-package coupling to ShortestPathPlugin is not ")
				.append("allowlisted — route cross-cutting config and world-geometry ")
				.append("access through an owned service or injected seam instead: ")
				.append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("stale allowlist entries with no matching reference site ")
				.append("— remove this allowlist entry in the change that removed the ")
				.append("reference: ").append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	/**
	 * Reach check: every enumerated leaf package must still resolve to a
	 * directory containing {@code .java} sources, so a renamed or relocated
	 * package cannot silently empty the scan. The frozen allowlist may now be
	 * empty — all plugin-class resource anchors migrated to the
	 * self-anchored loader — and the plugin-reference scan remains enforced
	 * with zero tolerated sites.
	 */
	@Test
	public void scanHasReach() throws IOException
	{
		Path base = MAIN_ROOT.resolve("shortestpath");
		for (String pkg : LEAF_PACKAGES)
		{
			Path dir = base.resolve(pkg);
			assertTrue("leaf package directory missing: " + dir, Files.isDirectory(dir));
			long sources;
			try (Stream<Path> stream = Files.walk(dir))
			{
				sources = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).count();
			}
			assertTrue("leaf package scanned no .java files: " + dir, sources > 0);
		}
	}

	/**
	 * Red-direction proof: {@link #observedSites(Path)} must flag an
	 * unlisted {@code ShortestPathPlugin.} site, and an empty fixture root
	 * must mark every allowlist entry stale. Fixture roots live in the temp
	 * directory — the submodule is never written.
	 */
	@Test
	public void flagsUnlistedCoupling() throws IOException
	{
		Path fixture = newFixtureRoot("dep-rule-probe");
		Path dir = fixture.resolve("shortestpath").resolve("transport");
		Files.createDirectories(dir);
		Files.write(dir.resolve("LintProbeTmp.java"), Collections.singletonList(
			"class LintProbeTmp { String s = ShortestPathPlugin.CONFIG_GROUP; }"));

		SortedSet<String> observed = observedSites(fixture);
		assertTrue("probe reference not observed: " + observed,
			observed.contains("shortestpath/transport/LintProbeTmp.java:1"));

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(ALLOWLIST.keySet());
		assertEquals("the probe site must land in the unlisted-hit set",
			Collections.singleton("shortestpath/transport/LintProbeTmp.java:1"), unlisted);

		Path empty = newFixtureRoot("dep-rule-empty");
		SortedSet<String> nothing = observedSites(empty);
		assertTrue("an empty fixture root must observe no sites: " + nothing,
			nothing.isEmpty());
		SortedSet<String> stale = new TreeSet<>(ALLOWLIST.keySet());
		stale.removeAll(nothing);
		assertEquals("an empty fixture must mark every allowlist entry stale",
			new TreeSet<>(ALLOWLIST.keySet()), stale);
	}

	/**
	 * The leaf-to-leaf edge set is frozen: a new leaf-package dependency
	 * fails until the coupling is justified in {@link #LEAF_EDGES}, and a
	 * removed edge fails until its entry leaves the table in the same
	 * change. Mutual edges (cycles) are documented where they exist rather
	 * than forbidden outright — the check exists so coupling cannot grow
	 * silently.
	 */
	@Test
	public void leafPackageEdgesAreFrozen() throws IOException
	{
		SortedSet<String> observed = observedEdges(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(LEAF_EDGES.keySet());

		SortedSet<String> stale = new TreeSet<>(LEAF_EDGES.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new leaf-package edge is not documented — justify ")
				.append("the coupling in LEAF_EDGES or route through a neutral ")
				.append("seam instead: ").append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("stale edge entries with no matching import — remove ")
				.append("the entry in the change that removed the edge: ")
				.append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	/**
	 * Red-direction proof for {@link #observedEdges(Path)}: a fixture import
	 * of a leaf package must surface as an unlisted edge, and a same-package
	 * import must not produce an edge.
	 */
	@Test
	public void flagsUnlistedEdge() throws IOException
	{
		Path fixture = newFixtureRoot("dep-rule-edge");
		Path dir = fixture.resolve("shortestpath").resolve("leagues");
		Files.createDirectories(dir);
		Files.write(dir.resolve("EdgeProbeTmp.java"), Arrays.asList(
			"package shortestpath.leagues;",
			"import shortestpath.items.ItemStateService;",
			"import shortestpath.leagues.LeagueModeState;",
			"class EdgeProbeTmp { }"));

		SortedSet<String> edges = observedEdges(fixture);
		assertEquals("the probe edge must be the only observed edge: " + edges,
			Collections.singleton("leagues -> items"), edges);

		SortedSet<String> unlisted = new TreeSet<>(edges);
		unlisted.removeAll(LEAF_EDGES.keySet());
		assertEquals("the undocumented probe edge must land in the unlisted set",
			Collections.singleton("leagues -> items"), unlisted);
	}

	/**
	 * Leaf-package code must not reference the
	 * {@code ShortestPathPlugin} type — imports, parameter/field
	 * types, {@code .class} literals — and must not read through a
	 * {@code plugin} field, beyond the adjudicated entries in
	 * {@link #TYPE_REFERENCE_EXCEPTIONS}. This is the scan that sees
	 * the instance coupling the dotted-token scan's known gap leaves
	 * uncounted; comments and javadoc are stripped, so design notes
	 * do not count as sites.
	 */
	@Test
	public void leafToPluginTypeReferences() throws IOException
	{
		SortedSet<String> observed = observedTypeReferences(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(TYPE_REFERENCE_EXCEPTIONS.keySet());

		SortedSet<String> stale = new TreeSet<>(TYPE_REFERENCE_EXCEPTIONS.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new leaf-package reference to ShortestPathPlugin (type ")
				.append("or plugin. read) is not an adjudicated exception — route ")
				.append("through an owned service or injected seam instead: ")
				.append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("exception entries with no matching reference site — ")
				.append("remove the entry in the change that removed the seam: ")
				.append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	/**
	 * No subpackage — leaf or unclassified — may reach the plugin
	 * class statically: {@code ShortestPathPlugin.} accesses outside
	 * the root package must all be approved in
	 * {@link #SUBPACKAGE_STATIC_EXCEPTIONS}.
	 */
	@Test
	public void subpackageToPluginStaticCoupling() throws IOException
	{
		SortedSet<String> observed = observedSubpackageStaticCoupling(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(SUBPACKAGE_STATIC_EXCEPTIONS.keySet());

		SortedSet<String> stale = new TreeSet<>(SUBPACKAGE_STATIC_EXCEPTIONS.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new subpackage static coupling to ShortestPathPlugin ")
				.append("is not approved — route through an owned service or ")
				.append("injected seam instead: ").append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("stale exception entries with no matching static-access ")
				.append("site — remove the entry in the change that removed the ")
				.append("reference: ").append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	/**
	 * Leaf packages expose no public mutable state:
	 * {@code public <type> <name> [=|;]} declarations fail unless
	 * approved in {@link #MUTABLE_FIELD_EXCEPTIONS} or injected
	 * ({@code @Inject}) — the mutable-field arm of the no-seams
	 * invariant.
	 */
	@Test
	public void publicMutableFieldSeams() throws IOException
	{
		SortedSet<String> observed = observedPublicMutableFields(MAIN_ROOT);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(MUTABLE_FIELD_EXCEPTIONS.keySet());

		SortedSet<String> stale = new TreeSet<>(MUTABLE_FIELD_EXCEPTIONS.keySet());
		stale.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unlisted.isEmpty())
		{
			message.append("new public mutable field in a leaf package is not ")
				.append("approved — leaf state is injected or package-private, ")
				.append("never publicly mutable: ").append(unlisted).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("stale exception entries with no matching field site — ")
				.append("remove the entry in the change that removed the field: ")
				.append(stale);
		}
		assertTrue(message.toString(), unlisted.isEmpty() && stale.isEmpty());
	}

	/**
	 * The leaf package set is closed: the directories under
	 * {@code shortestpath/} must equal {@link #LEAF_PACKAGES} exactly.
	 * A new unclassified package fails here before any of the scans
	 * can silently ignore it, and a removed package fails until its
	 * enumeration is retired in the same change.
	 */
	@Test
	public void leafPackageCensus() throws IOException
	{
		SortedSet<String> observed = observedLeafPackages(MAIN_ROOT);

		SortedSet<String> unclassified = new TreeSet<>(observed);
		unclassified.removeAll(LEAF_PACKAGES);

		SortedSet<String> missing = new TreeSet<>(LEAF_PACKAGES);
		missing.removeAll(observed);

		StringBuilder message = new StringBuilder();
		if (!unclassified.isEmpty())
		{
			message.append("new shortestpath/* package is not classified — add it ")
				.append("to LEAF_PACKAGES with its seams documented, or remove it: ")
				.append(unclassified).append(' ');
		}
		if (!missing.isEmpty())
		{
			message.append("declared leaf package has no directory on disk — ")
				.append("retire it from LEAF_PACKAGES in the same change: ")
				.append(missing);
		}
		assertTrue(message.toString(), unclassified.isEmpty() && missing.isEmpty());
	}

	/**
	 * Red-direction proof for {@link #observedTypeReferences(Path)}:
	 * an import, a parameter type, and a {@code plugin.} read must all
	 * flag; the same tokens inside javadoc and line comments must not.
	 */
	@Test
	public void flagsUnlistedTypeReference() throws IOException
	{
		Path fixture = newFixtureRoot("dep-rule-typeref");
		Path dir = fixture.resolve("shortestpath").resolve("transport");
		Files.createDirectories(dir);
		Files.write(dir.resolve("TypeRefProbeTmp.java"), Arrays.asList(
			"import shortestpath.ShortestPathPlugin;",
			"/**",
			" * Javadoc mentioning plugin. and ShortestPathPlugin must not count.",
			" */",
			"// a line comment mentioning plugin. and ShortestPathPlugin must not count.",
			"class TypeRefProbeTmp {",
			"    TypeRefProbeTmp(ShortestPathPlugin plugin) { }",
			"    Object read(Object plugin) { return plugin.getName(); }",
			"}"));

		SortedSet<String> observed = observedTypeReferences(fixture);
		assertEquals("import, parameter type, and plugin. read must flag — comments must not",
			new TreeSet<>(Arrays.asList(
				"shortestpath/transport/TypeRefProbeTmp.java:1",
				"shortestpath/transport/TypeRefProbeTmp.java:7",
				"shortestpath/transport/TypeRefProbeTmp.java:8")),
			observed);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(TYPE_REFERENCE_EXCEPTIONS.keySet());
		assertEquals("the probe sites must land in the unlisted set",
			new TreeSet<>(observed), unlisted);
	}

	/**
	 * Red-direction proof for {@link #observedSubpackageStaticCoupling(Path)}:
	 * a {@code ShortestPathPlugin.} access in a package not even in
	 * {@link #LEAF_PACKAGES} must flag — leaf coverage alone would
	 * never see it — while the same access in a root-package file and
	 * in a comment must not.
	 */
	@Test
	public void flagsUnlistedStaticCoupling() throws IOException
	{
		Path fixture = newFixtureRoot("dep-rule-static");
		Path dir = fixture.resolve("shortestpath").resolve("unclassified");
		Files.createDirectories(dir);
		Files.write(dir.resolve("StaticProbeTmp.java"), Arrays.asList(
			"package shortestpath.unclassified;",
			"class StaticProbeTmp { String s = ShortestPathPlugin.CONFIG_GROUP; }",
			"// ShortestPathPlugin.CONFIG_GROUP in a comment does not count."));
		Files.write(fixture.resolve("shortestpath").resolve("RootShell.java"),
			Collections.singletonList(
				"class RootShell { String s = ShortestPathPlugin.CONFIG_GROUP; }"));

		SortedSet<String> observed = observedSubpackageStaticCoupling(fixture);
		assertEquals("the unclassified-package static access must be the only site "
				+ "(root-package files and comments excluded): " + observed,
			Collections.singleton("shortestpath/unclassified/StaticProbeTmp.java:2"),
			observed);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(SUBPACKAGE_STATIC_EXCEPTIONS.keySet());
		assertEquals("the probe site must land in the unlisted set",
			new TreeSet<>(observed), unlisted);
	}

	/**
	 * Red-direction proof for {@link #observedPublicMutableFields(Path)}:
	 * {@code public} instance fields must flag — bare, column-0,
	 * {@code volatile}/{@code transient}, annotation-bearing, and
	 * multi-declarator forms alike — while {@code public static}/
	 * {@code public final} declarations, comment text, private
	 * fields, and {@code @Inject} fields must not.
	 */
	@Test
	public void flagsMutableField() throws IOException
	{
		Path fixture = newFixtureRoot("dep-rule-mutable");
		Path dir = fixture.resolve("shortestpath").resolve("items");
		Files.createDirectories(dir);
		Files.write(dir.resolve("MutableProbeTmp.java"), Arrays.asList(
			"class MutableProbeTmp {",
			"public int colZero;",
			"    public int counter = 0;",
			"    public String name;",
			"    public volatile int tick;",
			"    public transient Object cache;",
			"    public @Deprecated int legacy;",
			"    public int a, b;",
			"    public static int shared;",
			"    public final String id = \"x\";",
			"    public static final long MAX = 5;",
			"    /* public int hiddenInComment = 1; */",
			"    private int closed = 2;",
			"}"));
		Files.write(dir.resolve("InjectedProbeTmp.java"), Arrays.asList(
			"class InjectedProbeTmp {",
			"    @Inject",
			"    public Object injected;",
			"    public @Inject Object sameLine;",
			"    @Inject private Object helper;",
			"    public Object leaked;",
			"}"));

		SortedSet<String> observed = observedPublicMutableFields(fixture);
		assertEquals("the bare, column-0, modifier-bearing, annotated, and "
				+ "multi-declarator public instance fields must flag — "
				+ "static/final, comments, private fields, and fields whose own "
				+ "declaration or preceding standalone line is @Inject must not; "
				+ "an @Inject on a different member's line must not bleed over: "
				+ observed,
			new TreeSet<>(Arrays.asList(
				"shortestpath/items/MutableProbeTmp.java:2",
				"shortestpath/items/MutableProbeTmp.java:3",
				"shortestpath/items/MutableProbeTmp.java:4",
				"shortestpath/items/MutableProbeTmp.java:5",
				"shortestpath/items/MutableProbeTmp.java:6",
				"shortestpath/items/MutableProbeTmp.java:7",
				"shortestpath/items/MutableProbeTmp.java:8",
				"shortestpath/items/InjectedProbeTmp.java:6")),
			observed);

		SortedSet<String> unlisted = new TreeSet<>(observed);
		unlisted.removeAll(MUTABLE_FIELD_EXCEPTIONS.keySet());
		assertEquals("the probe sites must land in the unlisted set",
			new TreeSet<>(observed), unlisted);
	}

	/**
	 * Red-direction proof for {@link #observedLeafPackages(Path)}: a
	 * package directory outside {@link #LEAF_PACKAGES} must be
	 * observed, and a declared leaf with no directory must surface as
	 * missing.
	 */
	@Test
	public void flagsLeafPackageDrift() throws IOException
	{
		Path fixture = newFixtureRoot("dep-rule-census");
		Files.createDirectories(fixture.resolve("shortestpath").resolve("transport"));
		Files.createDirectories(fixture.resolve("shortestpath").resolve("newpkg"));

		SortedSet<String> observed = observedLeafPackages(fixture);
		assertTrue("an unclassified package must be observed: " + observed,
			observed.contains("newpkg"));

		SortedSet<String> unclassified = new TreeSet<>(observed);
		unclassified.removeAll(LEAF_PACKAGES);
		assertEquals("the drift arm must isolate the new package",
			Collections.singleton("newpkg"), unclassified);

		SortedSet<String> missing = new TreeSet<>(LEAF_PACKAGES);
		missing.removeAll(observed);
		assertTrue("every declared leaf absent from the fixture must surface as missing",
			missing.contains("items") && missing.contains("pathfinder"));

		Path bare = newFixtureRoot("dep-rule-census-bare");
		SortedSet<String> none = observedLeafPackages(bare);
		assertTrue("a fixture root with no shortestpath directory must observe "
				+ "no packages rather than error: " + none,
			none.isEmpty());
	}

	private final List<Path> fixtureRoots = new ArrayList<>();

	/**
	 * Allocate a fixture root under the system temp directory and
	 * register it for {@link #cleanFixtures()} — the red-direction
	 * proofs write only to these roots, never to the submodule
	 * tree.
	 */
	private Path newFixtureRoot(String prefix) throws IOException
	{
		Path dir = Files.createTempDirectory(prefix);
		fixtureRoots.add(dir);
		return dir;
	}

	/**
	 * Delete every fixture root the test allocated. The
	 * {@code dep-rule-*} trees are scan inputs, not artifacts —
	 * leaving them would grow the system temp dir by one tree per
	 * fixture test per run.
	 */
	@After
	public void cleanFixtures() throws IOException
	{
		for (Path root : fixtureRoots)
		{
			if (!Files.isDirectory(root))
			{
				continue;
			}
			List<Path> paths;
			try (Stream<Path> stream = Files.walk(root))
			{
				paths = stream.sorted(Comparator.reverseOrder())
					.collect(Collectors.toList());
			}
			for (Path path : paths)
			{
				Files.delete(path);
			}
		}
	}
}
