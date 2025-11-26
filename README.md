# GitHub FileSystem

A quick'n'dirty Java NIO FileSystemProvider implementation for accessing GitHub repositories as file systems.

## Overview

This project provides a `FileSystemProvider` that allows you to access GitHub repositories using the standard Java NIO FileSystem APIs. You can read files and directories from GitHub repositories as if they were local file systems.

### Missing things

* Caching (you hit rate-limits very fast this way)
* Token support (to reliably work)
* Fuller tests
* Gists

## Usage

```java
URI uri = URI.create("github://github.com/owner/repo/tree/main/src");
FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
Path file = fs.getPath("main/java/App.java");
String content = Files.readString(file);
```

## Building

```bash
./gradlew publishToMavenLocal
```

## Testing

```bash
./gradlew test
```

## Run Example

```bash
jbang Example.java
```


