//DEPS dev.jbang.fs:jbang-filesystems:0.0.1-SNAPSHOT
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.jbang.fs.github.GitHubFileSystemProvider;
import dev.jbang.fs.github.HttpClient;



public class Example {
    public static void main(String[] args) throws Exception {
       
        // Create a GitHub filesystem using the provider
        URI uri = URI.create("github://github.com/jbangdev/jbang/tree/main");
        FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());

        // Access files
        Path readme = fs.getPath("/README.adoc");
        if (Files.exists(readme)) {
            String content = Files.readString(readme);
            System.out.println(content);
        }

        // List directory contents
        Path srcDir = fs.getPath("/src");
        Files.list(srcDir).forEach(System.out::println);

        Files.walkFileTree(srcDir, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                System.out.println(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                System.out.println(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });

        // Close the filesystem
        fs.close();
    }
}


