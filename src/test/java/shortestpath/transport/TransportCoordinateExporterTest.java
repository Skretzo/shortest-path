package shortestpath.transport;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.TsvParser;

public class TransportCoordinateExporterTest {
    @Test
    public void testTsvParserCapturesSourceReference() {
        String contents = "# Origin\tDestination\tDuration\n"
            + "# Comment line\n"
            + "3200 3200 0\t3300 3300 0\t5\n";

        List<TransportRecord> records = new TsvParser().parse(contents, "transports/test.tsv");

        Assert.assertEquals(1, records.size());
        Assert.assertEquals("transports/test.tsv:3", records.get(0).getSourceReference());
    }

    @Test
    public void testExporterIncludesSourceForOriginAndDestinationRows() {
        String contents = "# Origin\tDestination\tDisplay info\n"
            + "3100 3100 0\t\tAIQ\n"
            + "\t3300 3300 0\tAIQ\n";

        List<TransportCoordinateItem> items = new TransportCoordinateExporter().exportFromContents("transports/fairy.tsv", contents, TransportType.FAIRY_RING);

        Assert.assertEquals(2, items.size());
        Assert.assertEquals("transports/fairy.tsv:2", items.get(0).getSource());
        Assert.assertEquals("transports/fairy.tsv:3", items.get(1).getSource());
    }

    @Test
    public void testExporterMergesLabelsAndSourcesForRepeatedCoordinates() {
        String contents = "# Origin\tDestination\tDisplay info\n"
            + "3100 3100 0\t\tAIQ\n"
            + "3100 3100 0\t\tBJR\n";

        List<TransportCoordinateItem> items = new TransportCoordinateExporter().exportFromContents("transports/fairy.tsv", contents, TransportType.FAIRY_RING);

        Assert.assertEquals(1, items.size());
        Assert.assertEquals("transports/fairy.tsv:2; transports/fairy.tsv:3", items.get(0).getSource());
        Assert.assertTrue(items.get(0).getLabel().contains("AIQ"));
        Assert.assertTrue(items.get(0).getLabel().contains("BJR"));
    }
}
