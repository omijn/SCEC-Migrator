import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Migrator {

    private static boolean isCorePHPFile(String fileContent) {
        return fileContent.substring(0, Math.min(fileContent.length(), 100)).contains("coreshow.php");
    }

    private static class PathUtils {

        private static String stripAbsolutePathPrefix(String path) {
            return path.substring(path.indexOf("/scec"));
        }
        private static String stripQuotes(String path) {
            return path.replaceAll("['\"]", "");
        }

        private static String relativizePaths(String filePath, String otherPath) {
            Path p1 = Paths.get(stripAbsolutePathPrefix(filePath));
            Path p2 = Paths.get(stripAbsolutePathPrefix(stripQuotes(otherPath)));

            Path relativized = p1.relativize(p2);
            String relativizedString = relativized.toString().replaceFirst("\\.\\.", "dirname(__FILE__).\"") + "\"";
            return relativizedString;
        }

        private static String resolvePaths(String filePath, String otherPath) {
            String resolvedString = Paths.get(filePath).resolveSibling(Paths.get(otherPath)).normalize().toString();
            return resolvedString;
        }

        private static String resolvePaths(Path filePath, Path otherPath) {
            return filePath.resolveSibling(otherPath).normalize().toString();
        }
    }

    private static class FileEditor {

        private static final Pattern absolutePathPattern = Pattern.compile("(?:[\"'])/info(?:/[\\w.$-]+)*/?(?:[\"'])");
        private static final Pattern relativePathPattern = Pattern.compile("(?:[\"'])\\.{1,2}(?:/[\\w.$-]+)*/?(?:[\"'])");
        private static final Pattern chdirLinePattern = Pattern.compile("chdir\\(.*?; ?");


        private static String editCoreFile(String fileContent, String filePath) {
            String coreshowIncludePath = "/info/vh/scec/eqcountry/shakeout/coreshow.php";
            fileContent = chdirLinePattern.matcher(fileContent).replaceFirst("");
            fileContent = relativePathPattern.matcher(fileContent).replaceFirst(matchResult -> PathUtils.relativizePaths(filePath, coreshowIncludePath));
            return fileContent;
        }

        private static String editRegularPHPFile(String fileContent, String filePath) {
            fileContent = absolutePathPattern.matcher(fileContent).replaceAll(matchResult -> Matcher.quoteReplacement(PathUtils.relativizePaths(filePath, matchResult.group())));
//            fileContent = relativePathPattern.matcher(fileContent).replaceAll(matchResult -> Matcher.quoteReplacement(PathUtils.resolvePaths(filePath, matchResult.group())));
            return fileContent;
        }
    }

    private static class FileManager {
        private static String readFile(String filename) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return sb.toString();
        }


        private static List<String> readFilesInDirectory(String dirname) {
            List<String> files = Collections.emptyList();

            try {
                files = Files.walk(Paths.get(dirname).toAbsolutePath())
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> FilenameUtils.isExtension(path.toString(), new String[]{"php"}))
                        .map(Path::toString)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
            }

            return files;
        }

        private static void writeFile(String filename, String fileContent) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, false), "UTF-8"))) {
                writer.write(fileContent);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static void backupDirectory(String dirname) {
            if (Files.isDirectory(Paths.get(dirname)) && !dirname.endsWith("/"))
                dirname += File.separator;

            File srcDir = new File(dirname);
            File destDir = new File(FilenameUtils.getFullPathNoEndSeparator(dirname) + ".bak");

            try {
                FileUtils.copyDirectory(srcDir, destDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        String dirname = args[0];
        List<String> fileList = FileManager.readFilesInDirectory(dirname);

        FileManager.backupDirectory(dirname);

        for (String filePath: fileList) {
            String fileContent = FileManager.readFile(filePath);
            String editedFile;
            if (isCorePHPFile(fileContent)) {
                editedFile = FileEditor.editCoreFile(fileContent, filePath);
            }
            else {
                editedFile = FileEditor.editRegularPHPFile(fileContent, filePath);
            }

            FileManager.writeFile(filePath, editedFile);

            System.out.println(filePath);

//            System.out.println("Original File: \n" + fileContent + "\n");
//            System.out.println("Edited File: \n" + editedFile + "\n");
        }
    }
}
