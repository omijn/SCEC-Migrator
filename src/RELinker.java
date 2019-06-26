import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Relink broken absolute symlinks as relative links
 */

public class RELinker {

    private static Path relativize(Path src, Path dest) {

        src = Paths.get(Migrator.PathUtils.stripAbsolutePathPrefix(src.toString()));
        dest = Paths.get(Migrator.PathUtils.stripAbsolutePathPrefix(dest.toString()));

        if (dest.isAbsolute()) {
            String relativized = src.relativize(dest).toString();
            return Paths.get(relativized.replaceFirst("\\.\\./", ""));
        } else
            return dest;
    }

    public static void main(String[] args) {
        String brokenLinksFile = args[0];

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(brokenLinksFile)))) {
            String linkName;
            while ((linkName = br.readLine()) != null) {
                Path src = Paths.get(linkName);
                Path dest = Files.readSymbolicLink(src);
                Path newDest = relativize(src, dest);
                Files.delete(src);
                Path result = Files.createSymbolicLink(src, newDest);
                System.out.println(src + " : \n" + dest + " -> " + newDest);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
