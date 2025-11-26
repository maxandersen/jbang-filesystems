package dev.jbang.fs.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A FileSystemProvider that allows accessing GitHub repositories as if they
 * were local file systems. This enables using Java NIO FileSystem APIs with
 * GitHub URLs.
 * 
 * Example usage:
 * 
 * <pre>
 * URI uri = URI.create("github://github.com/owner/repo/tree/branch/path");
 * FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
 * Path file = fs.getPath("/src/main/java/App.java");
 * </pre>
 */
public class GitHubFileSystemProvider extends FileSystemProvider {

	private static final String SCHEME = "github";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";
	private static final String GITHUB_RAW_BASE = "https://raw.githubusercontent.com";

	private final Map<URI, GitHubFileSystem> filesystems = new ConcurrentHashMap<>();
	private final Gson gson = new Gson();
	private final HttpClient httpClient;

	public GitHubFileSystemProvider() {
		this.httpClient = new SimpleHttpClient();
	}

	public GitHubFileSystemProvider(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		if (filesystems.containsKey(uri)) {
			throw new FileSystemAlreadyExistsException("FileSystem already exists for: " + uri);
		}
		GitHubFileSystem fs = new GitHubFileSystem(this, parseGitHubUri(uri));
		filesystems.put(uri, fs);
		return fs;
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		GitHubFileSystem fs = filesystems.get(uri);
		if (fs == null) {
			throw new FileSystemNotFoundException("GitHub filesystem not found for: " + uri);
		}
		return fs;
	}

	void removeFileSystem(GitHubFileSystem fs) {
		filesystems.entrySet().removeIf(entry -> entry.getValue() == fs);
	}

	@Override
	public Path getPath(URI uri) {
		GitHubFileSystem fs = filesystems.values()
			.stream()
			.filter(f -> f.getRepoInfo().matches(uri))
			.findFirst()
			.orElseThrow(() -> new FileSystemNotFoundException("No filesystem found for: " + uri));
		return fs.getPath(uri.getPath());
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		if (path instanceof GitHubPath) {
			GitHubPath ghPath = (GitHubPath) path;
			Map<String, Object> attrs = new HashMap<>();
			if (attributes.equals("*") || attributes.startsWith("basic:")) {
				BasicFileAttributes basicAttrs = readAttributes(ghPath, BasicFileAttributes.class, options);
				attrs.put("size", basicAttrs.size());
				attrs.put("creationTime", basicAttrs.creationTime());
				attrs.put("lastModifiedTime", basicAttrs.lastModifiedTime());
				attrs.put("isRegularFile", basicAttrs.isRegularFile());
				attrs.put("isDirectory", basicAttrs.isDirectory());
				attrs.put("isSymbolicLink", basicAttrs.isSymbolicLink());
				attrs.put("isOther", basicAttrs.isOther());
				attrs.put("fileKey", basicAttrs.fileKey());
			}
			return attrs;
		}
		throw new ProviderMismatchException("Path is not a GitHub path: " + path);
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return null;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		if (path instanceof GitHubPath) {
			GitHubPath ghPath = (GitHubPath) path;
			if (type == BasicFileAttributes.class) {
				@SuppressWarnings("unchecked")
				A attrs = (A) new GitHubFileAttributes(ghPath);
				return attrs;
			}
		}
		throw new UnsupportedOperationException("Attributes type not supported: " + type);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
			throws IOException {
		if (dir instanceof GitHubPath) {
			GitHubPath ghPath = (GitHubPath) dir;
			return new GitHubDirectoryStream(ghPath, filter);
		}
		throw new ProviderMismatchException("Path is not a GitHub path: " + dir);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
			FileAttribute<?>... attrs) throws IOException {
		if (path instanceof GitHubPath) {
			GitHubPath ghPath = (GitHubPath) path;
			if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.CREATE)
					|| options.contains(StandardOpenOption.CREATE_NEW)) {
				throw new UnsupportedOperationException("GitHub filesystem is read-only");
			}
			return new GitHubSeekableByteChannel(ghPath);
		}
		throw new ProviderMismatchException("Path is not a GitHub path: " + path);
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		if (path instanceof GitHubPath) {
			GitHubPath ghPath = (GitHubPath) path;
			String url = getRawContentUrl(ghPath);
			Path cachedFile = httpClient.downloadAndCacheFile(url);
			return Files.newInputStream(cachedFile);
		}
		throw new ProviderMismatchException("Path is not a GitHub path: " + path);
	}

	@Override
	public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		throw new UnsupportedOperationException("FileChannel not supported for GitHub filesystem");
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public void delete(Path path) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return path.equals(path2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		return false;
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		throw new UnsupportedOperationException("FileStore not supported for GitHub filesystem");
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		if (path instanceof GitHubPath) {
			GitHubPath ghPath = (GitHubPath) path;
			if (!exists(ghPath)) {
				throw new NoSuchFileException(ghPath.toString());
			}
			for (AccessMode mode : modes) {
				if (mode == AccessMode.WRITE || mode == AccessMode.EXECUTE) {
					throw new AccessDeniedException("GitHub filesystem is read-only");
				}
			}
			return;
		}
		throw new ProviderMismatchException("Path is not a GitHub path: " + path);
	}

	@Override
	public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public void createLink(Path link, Path existing) throws IOException {
		throw new UnsupportedOperationException("GitHub filesystem is read-only");
	}

	@Override
	public Path readSymbolicLink(Path link) throws IOException {
		throw new UnsupportedOperationException("Symbolic links not supported");
	}

	List<Path> listDirectory(GitHubPath dir) throws IOException {
		GitHubRepoInfo repoInfo = dir.getFileSystem().getRepoInfo();
		String repoPath = toRepoPath(dir.getPathString(), repoInfo.getBasePath());
		String apiUrl = String.format("%s/%s/%s/contents%s?ref=%s", GITHUB_API_BASE, repoInfo.getOwner(),
				repoInfo.getRepo(), repoPath, repoInfo.getRef());

		String jsonContent = httpClient.downloadString(apiUrl);
		JsonArray contents = gson.fromJson(jsonContent, JsonArray.class);

		List<Path> paths = new ArrayList<>();
		String basePath = repoInfo.getBasePath();
		for (JsonElement element : contents) {
			JsonObject item = element.getAsJsonObject();
			String repoPathStr = item.get("path").getAsString();

			// Convert repo path to filesystem path by removing basePath prefix
			String fsPath = toFilesystemPath(repoPathStr, basePath);
			Path childPath = dir.getFileSystem().getPath(fsPath);
			paths.add(childPath);
		}
		return paths;
	}

	boolean exists(GitHubPath path) throws IOException {
		try {
			GitHubRepoInfo repoInfo = path.getFileSystem().getRepoInfo();
			String repoPath = toRepoPath(path.getPathString(), repoInfo.getBasePath());
			String apiUrl = String.format("%s/%s/%s/contents%s?ref=%s", GITHUB_API_BASE, repoInfo.getOwner(),
					repoInfo.getRepo(), repoPath, repoInfo.getRef());
			httpClient.downloadString(apiUrl);
			return true;
		} catch (IOException e) {
			if (e.getMessage() != null && e.getMessage().contains("404")) {
				return false;
			}
			throw e;
		}
	}

	boolean isDirectory(GitHubPath path) throws IOException {
		try {
			GitHubRepoInfo repoInfo = path.getFileSystem().getRepoInfo();
			String repoPath = toRepoPath(path.getPathString(), repoInfo.getBasePath());
			String apiUrl = String.format("%s/%s/%s/contents%s?ref=%s", GITHUB_API_BASE, repoInfo.getOwner(),
					repoInfo.getRepo(), repoPath, repoInfo.getRef());
			String jsonContent = httpClient.downloadString(apiUrl);
			JsonElement element = gson.fromJson(jsonContent, JsonElement.class);
			if (element.isJsonArray()) {
				return true;
			}
			JsonObject item = element.getAsJsonObject();
			return "dir".equals(item.get("type").getAsString());
		} catch (IOException e) {
			if (e.getMessage() != null && e.getMessage().contains("404")) {
				return false;
			}
			throw e;
		}
	}

	String getRawContentUrl(GitHubPath path) {
		GitHubRepoInfo repoInfo = path.getFileSystem().getRepoInfo();
		String repoPath = toRepoPath(path.getPathString(), repoInfo.getBasePath());
		return String.format("%s/%s/%s/%s%s", GITHUB_RAW_BASE, repoInfo.getOwner(), repoInfo.getRepo(),
				repoInfo.getRef(), repoPath);
	}

	/**
	 * Converts a filesystem path to a repository path by combining with basePath.
	 * Filesystem root "/" maps to basePath, other paths are appended to basePath.
	 */
	private String toRepoPath(String fsPath, String basePath) {
		if (fsPath.equals("/")) {
			return basePath.isEmpty() ? "/" : basePath;
		}
		// Remove leading slash from fsPath for combination
		String fsPathWithoutSlash = fsPath.startsWith("/") ? fsPath.substring(1) : fsPath;
		if (basePath.isEmpty() || basePath.equals("/")) {
			return "/" + fsPathWithoutSlash;
		}
		// Ensure basePath doesn't end with / and fsPath doesn't start with /
		String base = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
		return base + "/" + fsPathWithoutSlash;
	}

	/**
	 * Converts a repository path to a filesystem path by removing the basePath prefix.
	 * If basePath is empty or "/", returns the path as-is (with leading slash).
	 * GitHub API returns paths without leading slash, so we need to handle both cases.
	 */
	private String toFilesystemPath(String repoPath, String basePath) {
		if (basePath.isEmpty() || basePath.equals("/")) {
			return repoPath.startsWith("/") ? repoPath : "/" + repoPath;
		}
		// Remove leading slash from basePath for comparison (GitHub API paths don't have it)
		String basePathWithoutSlash = basePath.startsWith("/") ? basePath.substring(1) : basePath;
		
		// Remove leading slash from repoPath for comparison
		String repoPathWithoutSlash = repoPath.startsWith("/") ? repoPath.substring(1) : repoPath;
		
		// Check if repoPath starts with basePath
		if (repoPathWithoutSlash.startsWith(basePathWithoutSlash)) {
			String relative = repoPathWithoutSlash.substring(basePathWithoutSlash.length());
			// Remove leading slash if present, then ensure result starts with /
			if (relative.startsWith("/")) {
				relative = relative.substring(1);
			}
			// If relative is empty, we're at the root
			return relative.isEmpty() ? "/" : "/" + relative;
		}
		// If path doesn't start with basePath, return as-is (shouldn't happen in normal usage)
		return repoPath.startsWith("/") ? repoPath : "/" + repoPath;
	}

	private GitHubRepoInfo parseGitHubUri(URI uri) throws IOException {
		String scheme = uri.getScheme();
		if (!SCHEME.equals(scheme)) {
			throw new IllegalArgumentException("Invalid scheme: " + scheme);
		}

		String ssp = uri.getSchemeSpecificPart();
		// Format: //github.com/owner/repo/tree/branch/path
		if (!ssp.startsWith("//")) {
			throw new IllegalArgumentException("Invalid GitHub URI format: " + uri);
		}

		String path = ssp.substring(2); // Remove "//"
		String[] parts = path.split("/", 6);

		if (parts.length < 4 || !"github.com".equals(parts[0])) {
			throw new IllegalArgumentException("Invalid GitHub URI format: " + uri);
		}

		String owner = parts[1];
		String repo = parts[2];
		String ref = "main"; // default
		String filePath = "";

		if (parts.length >= 4) {
			if ("tree".equals(parts[3]) && parts.length >= 5) {
				ref = parts[4];
				if (parts.length >= 6) {
					filePath = "/" + parts[5];
				}
			} else if ("blob".equals(parts[3]) && parts.length >= 5) {
				ref = parts[4];
				if (parts.length >= 6) {
					filePath = "/" + parts[5];
				}
			} else {
				// Assume it's a branch/ref name
				ref = parts[3];
				if (parts.length >= 5) {
					filePath = "/" + parts[4];
				}
			}
		}

		return new GitHubRepoInfo(owner, repo, ref, filePath);
	}

	public static GitHubRepoInfo parseGitHubUrl(String url) {
		// Parse URLs like:
		// https://github.com/owner/repo/tree/branch/path
		// https://github.com/owner/repo/blob/branch/path
		// https://raw.githubusercontent.com/owner/repo/branch/path

		if (url.startsWith("https://raw.githubusercontent.com/")) {
			String path = url.substring("https://raw.githubusercontent.com/".length());
			String[] parts = path.split("/", 4);
			if (parts.length >= 3) {
				return new GitHubRepoInfo(parts[0], parts[1], parts[2],
						parts.length > 3 ? "/" + parts[3] : "");
			}
		} else if (url.startsWith("https://github.com/")) {
			String path = url.substring("https://github.com/".length());
			String[] parts = path.split("/");
			if (parts.length >= 3) {
				String owner = parts[0];
				String repo = parts[1];
				String ref = "main";
				String filePath = "";

				if (parts.length >= 4) {
					if ("tree".equals(parts[2]) && parts.length >= 4) {
						ref = parts[3];
						if (parts.length > 4) {
							filePath = "/" + String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length));
						}
					} else if ("blob".equals(parts[2]) && parts.length >= 4) {
						ref = parts[3];
						if (parts.length > 4) {
							filePath = "/" + String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length));
						}
					}
				}

				return new GitHubRepoInfo(owner, repo, ref, filePath);
			}
		}

		throw new IllegalArgumentException("Invalid GitHub URL: " + url);
	}

	public static URI toGitHubUri(GitHubRepoInfo repoInfo) {
		try {
			String uriStr = String.format("github://github.com/%s/%s/tree/%s%s", repoInfo.getOwner(),
					repoInfo.getRepo(), repoInfo.getRef(), repoInfo.getBasePath());
			return new URI(uriStr);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid GitHub repo info", e);
		}
	}
}
