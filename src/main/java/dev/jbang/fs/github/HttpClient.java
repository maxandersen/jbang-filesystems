package dev.jbang.fs.github;

import java.io.IOException;

/**
 * Simple HTTP client interface for making HTTP requests.
 * This abstraction allows the filesystem to work independently
 * of any specific HTTP client implementation.
 */
public interface HttpClient {
	/**
	 * Downloads content from a URL as a string.
	 *
	 * @param url HTTP URL of the content to be downloaded
	 * @return The content as a string
	 * @throws IOException if the request fails
	 */
	String downloadString(String url) throws IOException;

	/**
	 * Downloads a file from a URL and returns a path to the cached file.
	 * The implementation should handle caching internally.
	 *
	 * @param url HTTP URL of the file to be downloaded
	 * @return Path to the downloaded/cached file
	 * @throws IOException if the request fails
	 */
	java.nio.file.Path downloadAndCacheFile(String url) throws IOException;
}

