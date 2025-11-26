package dev.jbang.fs.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;


public class GitHubFileSystemProviderTest {

	@Test
	void testParseGitHubUrl() {
		GitHubRepoInfo info = GitHubFileSystemProvider.parseGitHubUrl(
				"https://github.com/jbangdev/jbang/tree/main/src");
		assertThat(info.getOwner()).isEqualTo("jbangdev");
		assertThat(info.getRepo()).isEqualTo("jbang");
		assertThat(info.getRef()).isEqualTo("main");
		assertThat(info.getBasePath()).isEqualTo("/src");

		info = GitHubFileSystemProvider.parseGitHubUrl(
				"https://github.com/jbangdev/jbang/blob/main/src/App.java");
		assertThat(info.getOwner()).isEqualTo("jbangdev");
		assertThat(info.getRepo()).isEqualTo("jbang");
		assertThat(info.getRef()).isEqualTo("main");
		assertThat(info.getBasePath()).isEqualTo("/src/App.java");

		info = GitHubFileSystemProvider.parseGitHubUrl(
				"https://raw.githubusercontent.com/jbangdev/jbang/branch/path/to/file.java");
		assertThat(info.getOwner()).isEqualTo("jbangdev");
		assertThat(info.getRepo()).isEqualTo("jbang");
		assertThat(info.getRef()).isEqualTo("branch");
		assertThat(info.getBasePath()).isEqualTo("/path/to/file.java");
	}

	@Test
	void testCreateFileSystem() throws IOException {
		GitHubRepoInfo repoInfo = new GitHubRepoInfo("jbangdev", "jbang", "main", "/src");
		URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);

		try (FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap())) {
			assertThat(fs).isNotNull();
			assertThat(fs.isReadOnly()).isTrue();

			// Test listing directory - filesystem root "/" maps to repo "/src"
			Path rootPath = fs.getPath("/");
			assertThat(Files.exists(rootPath)).isTrue();
			assertThat(Files.isDirectory(rootPath)).isTrue();

			List<Path> children = Files.list(rootPath).collect(Collectors.toList());
			assertThat(children.size()).isGreaterThan(0);
		}
	}

	@Test
	void testReadFile() throws IOException {
		GitHubRepoInfo repoInfo = new GitHubRepoInfo("jbangdev", "jbang", "main", "/");
		URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);

		try (FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap())) {
			Path filePath = fs.getPath("/build.gradle");
			assertThat(Files.exists(filePath)).isTrue();
			assertThat(Files.isRegularFile(filePath)).isTrue();

			String content = new String(Files.readAllBytes(filePath));
			assertThat(content).contains("description = 'JBang Command Line Interface'");
		}
	}

	@Test
	void testDirectoryListing() throws IOException {
		
		GitHubRepoInfo repoInfo = new GitHubRepoInfo("jbangdev", "jbang", "main", "src");
		URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);

		try (FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap())) {
			// Filesystem root "/" maps to repo "/src"
			Path rootPath = fs.getPath("/");
			List<Path> children = Files.list(rootPath).collect(Collectors.toList());
			assertThat(children.size()).isEqualTo(5);

			// Children should be paths like "/it", "/jreleaser" (relative to filesystem root)
			// Verify they don't have the "/src" prefix
			for (Path child : children) {
				String pathStr = child.toString();
				assertThat(pathStr).doesNotStartWith("/src/");
				assertThat(pathStr).startsWith("/");
			}

			List<String> names = children.stream()
				.map(p -> p.getFileName().toString())
				.collect(Collectors.toList());
			assertThat(names).containsExactlyInAnyOrder("it","jreleaser", "main", "native-image", "test");
		}
	}

	@Test
	void testBasePathNormalization() {
		// Test that basePath can be specified without leading slash
		GitHubRepoInfo repoInfo1 = new GitHubRepoInfo("owner", "repo", "main", "src");
		GitHubRepoInfo repoInfo2 = new GitHubRepoInfo("owner", "repo", "main", "/src");
		assertThat(repoInfo1.getBasePath()).isEqualTo("/src");
		assertThat(repoInfo2.getBasePath()).isEqualTo("/src");
		assertThat(repoInfo1).isEqualTo(repoInfo2);
	}

}
