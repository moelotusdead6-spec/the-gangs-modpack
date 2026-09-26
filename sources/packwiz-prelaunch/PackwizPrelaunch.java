import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PackwizPrelaunch {
    private static final String PACKWIZ_URL =
            "https://moelotusdead6-spec.github.io/the-gangs-modpack/pack.toml";

    private PackwizPrelaunch() {
    }

    public static void main(String[] args) throws Exception {
        Path instanceDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path minecraftDir = instanceDir.resolve("minecraft");
        Path bootstrapJar = instanceDir.resolve("packwiz-installer-bootstrap.jar");
        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();

        Process process = new ProcessBuilder(
                javaExecutable,
                "-jar",
                bootstrapJar.toString(),
                "-g",
                "-s",
                "both",
                PACKWIZ_URL)
                .directory(minecraftDir.toFile())
                .inheritIO()
                .start();

        System.exit(process.waitFor());
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }
}
