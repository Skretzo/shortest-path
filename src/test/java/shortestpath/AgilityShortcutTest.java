package shortestpath;

import net.runelite.client.game.AgilityShortcut;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;


/**
 * this test checks every AgilityShortcut enum constant against the list of shortcuts
 * in the agility_shortcuts.tsv resource file, to ensure that our list is complete.
 * 
 * This test should only fail when a new AgilityShortcut enum constant is added
 * without a corresponding entry in the TSV file.
 * 
 * There are some known exceptions where an AgilityShortcut cannot be matched to a TSV entry,
 * for example when the Object ID changes as the result of "unlocking" the shortcut via a quest.
 * These exceptions can be added to the `excludedIds` set in the test method. Use this sparingly.
 */
public class AgilityShortcutTest
{
    @Test
    public void everyEnumHasTsvEntry()
    {
        // Load TSV from resources
        InputStream in = getClass().getResourceAsStream("/transports/agility_shortcuts.tsv");
        Assert.assertNotNull("agility_shortcuts.tsv resource not found", in);

        Set<String> coordSet = new HashSet<>();
        Set<Integer> objectIds = new HashSet<>();

        // IDs that are known to be checked elsewhere but cannot be matched
        // to the TSV entries. Add IDs here (one per entry) to exclude them
        // from the matching check.
        Set<Integer> excludedIds = new HashSet<>();

        // these two IDs represent the same shortcut before and after a step in the `Making Friends with My Arm` quest,
        // see https://oldschool.runescape.wiki/w/Broken_fence_(Weiss)
        excludedIds.add(46815);
        excludedIds.add(46817); 

        try (BufferedReader r = new BufferedReader(new InputStreamReader(in)))
        {
            String line;
            while ((line = r.readLine()) != null)
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                {
                    continue;
                }

                // TSV columns: Origin\tDestination\tmenuOption menuTarget objectID ...
                String[] cols = line.split("\t");
                if (cols.length < 3)
                {
                    continue;
                }

                // origin and destination coordinates are space-separated triples like "2220 3155 0"
                String origin = cols[0].trim();
                String dest = cols[1].trim();
                coordSet.add(origin);
                coordSet.add(dest);

                // objectID may appear in the 3rd column after menu text, try to extract tokens that are integers
                String third = cols[2];
                String[] tokens = third.split("\\s+");
                for (String t : tokens)
                {
                    try
                    {
                        int id = Integer.parseInt(t);
                        objectIds.add(id);
                    }
                    catch (NumberFormatException ignored)
                    {
                    }
                }

                // also some lines include object IDs in later columns, scan remaining columns
                for (int i = 3; i < cols.length; i++)
                {
                    String[] subtokens = cols[i].split("\\D+");
                    for (String st : subtokens)
                    {
                        if (st.isEmpty()) continue;
                        try
                        {
                            objectIds.add(Integer.parseInt(st));
                        }
                        catch (NumberFormatException ignored) { }
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        StringBuilder missing = new StringBuilder();
        for (AgilityShortcut s : AgilityShortcut.values())
        {
            boolean matched = false;
            boolean excluded = false;

            // check worldLocation (x y z) if present
            if (s.getWorldLocation() != null)
            {
                String coord = s.getWorldLocation().getX() + " " + s.getWorldLocation().getY() + " " + s.getWorldLocation().getPlane();
                if (coordSet.contains(coord)) matched = true;
            }

            // check obstacle IDs
            if (!matched && s.getObstacleIds() != null)
            {
                for (int id : s.getObstacleIds())
                {
                    if (excludedIds.contains(id))
                    {
                        // skip known-excluded IDs
                        System.out.println("Skipping excluded ID " + id + " for " + s.name());
                        excluded = true;
                        continue;
                    }

                    if (objectIds.contains(id))
                    {
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched && !excluded)
            {
                missing.append(s.name()).append('\n');
            }
        }

        if (missing.length() > 0)
        {
            System.out.println("Missing TSV entries for the following AgilityShortcut enum constants:\n" + missing.toString());
            Assert.fail("Missing TSV entries for AgilityShortcut enum constants (see stdout for list)");
        }
    }
}
