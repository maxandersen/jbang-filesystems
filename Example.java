//DEPS 
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class Example {
    public static void main(String[] args) throws Exception {
        // Create a GitHub filesystem
        URI uri = URI.create("github://github.com/jbangdev/jbang/tree/main/src");
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
        
        // Close the filesystem
        fs.close();
    }
}
```

## Custom HTTP Client

You can provide a custom HTTP client implementation:

```java
import dev.jbang.fs.github.GitHubFileSystemProvider;
import dev.jbang.fs.github.HttpClient;

// Create a custom HTTP client
HttpClient customClient = new MyCustomHttpClient();

// Create provider with custom client
GitHubFileSystemProvider provider = new GitHubFileSystemProvider(customClient);
```

