# GitHub FileSystem

A Java NIO FileSystemProvider implementation for accessing GitHub repositories as file systems.

## Overview

This project provides a `FileSystemProvider` that allows you to access GitHub repositories using the standard Java NIO FileSystem APIs. You can read files and directories from GitHub repositories as if they were local file systems.

## Usage

```java
URI uri = URI.create("github://github.com/owner/repo/tree/main/src");
FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
Path file = fs.getPath("/src/main/java/App.java");
String content = Files.readString(file);
```

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

