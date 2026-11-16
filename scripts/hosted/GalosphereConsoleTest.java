import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;

public final class GalosphereConsoleTest {
    public static void main(String[] args) throws Exception {
        var logger = LogManager.getLogger("test");
        logger.warn("Recoverable errors when loading section: galosphere:crystal_canyons");
        logger.error("Unknown structure start: galosphere:forgotten_ruins");
        logger.error("MULTILINE galosphere:error\nsecond line", new IllegalStateException("DETAIL_RETAINED"));
        logger.error("OTHER_ERROR_VISIBLE");
        logger.warn("OTHER_WARNING_VISIBLE");
        logger.info("INFO galosphere:VISIBLE");
        logger.fatal("FATAL galosphere:VISIBLE");
        LogManager.shutdown();
        String file = Files.readString(Path.of("logs", "latest.log"));
        for (String expected : new String[]{"galosphere:crystal_canyons", "galosphere:forgotten_ruins",
            "MULTILINE", "DETAIL_RETAINED", "OTHER_ERROR_VISIBLE", "OTHER_WARNING_VISIBLE",
            "INFO galosphere:VISIBLE", "FATAL galosphere:VISIBLE"}) {
            if (!file.contains(expected)) throw new IllegalStateException("File log lost " + expected);
        }
    }
}
