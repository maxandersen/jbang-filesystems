package dev.jbang.fs.github;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple HTTP client implementation using Java's built-in HttpURLConnection.
 */
class SimpleHttpClient implements HttpClient {

	@Override
	public String downloadString(String url) throws IOException {
		System.out.println("Downloading string from URL: " + url);
		URL urlObj = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Accept", "application/json");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);

		int responseCode = connection.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
			throw new FileNotFoundException("Resource not found: " + url);
		}
		if (responseCode != HttpURLConnection.HTTP_OK) {
			throw new IOException("HTTP request failed with code " + responseCode + " for URL: " + url);
		}

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line).append("\n");
			}
			return response.toString().trim();
		}
	}

	@Override
	public Path downloadAndCacheFile(String url) throws IOException {
		// Simple implementation: download to temp file
		// In a real implementation, this would use a cache
		Path tempFile = Files.createTempFile("github-fs-", ".tmp");
		URL urlObj = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);

		int responseCode = connection.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
			throw new FileNotFoundException("Resource not found: " + url);
		}
		if (responseCode != HttpURLConnection.HTTP_OK) {
			throw new IOException("HTTP request failed with code " + responseCode + " for URL: " + url);
		}

		try (InputStream inputStream = connection.getInputStream()) {
			Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		return tempFile;
	}
}

