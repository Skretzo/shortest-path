package shortestpath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Destination {
    private static void addDestinations(Map<String, Set<Integer>> destinations, String path) {
        final String DELIM_COLUMN = "\t";
        final String PREFIX_COMMENT = "#";
        final String FILE_EXTENSION = ".";
        final String DELIM_PATH = "/";
        final String DELIM = " ";

        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);

            // Header line is the first line in the file and will start with either '#' or '# '
            String headerLine = scanner.nextLine();
            headerLine = headerLine.startsWith(PREFIX_COMMENT + " ") ? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT) : headerLine;
            headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
            String[] headers = headerLine.split(DELIM_COLUMN);

            String[] parts = path.replace(FILE_EXTENSION, DELIM_PATH).split(DELIM_PATH);
            String entry = parts[parts.length - 2];

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(DELIM_COLUMN);
                Map<String, List<String>> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length) {
                        List<String> values = fieldMap.getOrDefault(headers[i], new ArrayList<>());
                        values.add(fields[i]);
                        fieldMap.put(headers[i], values);
                    }
                }
                for (String field : fieldMap.keySet()) {
                    if ("Destination".equals(field)) {
                        for (String value : fieldMap.get(field)) {
                            try {
                                String[] destinationArray = value.split(DELIM);
                                if (destinationArray.length == 3) {
                                    Set<Integer> entryDestinations = destinations.getOrDefault(entry, new HashSet<>());
                                    entryDestinations.add(WorldPointUtil.packWorldPoint(
                                        Integer.parseInt(destinationArray[0]),
                                        Integer.parseInt(destinationArray[1]),
                                        Integer.parseInt(destinationArray[2])));
                                    destinations.put(entry, entryDestinations);
                                }
                            } catch (NumberFormatException e) {
                                log.error("Invalid destination coordinate", e);
                            }
                        }
                    }
                }
            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Set<Integer>> loadAllFromResources() {
        Map<String, Set<Integer>> destinations = new HashMap<>(10);
        addDestinations(destinations, "/destinations/game_features/altar.tsv");
        addDestinations(destinations, "/destinations/game_features/bank.tsv");
        addDestinations(destinations, "/destinations/training/anvil.tsv");
        addDestinations(destinations, "/destinations/shopping/apothecary.tsv");
        return destinations;
    }
}
