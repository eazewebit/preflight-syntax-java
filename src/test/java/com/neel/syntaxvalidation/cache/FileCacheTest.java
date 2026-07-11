package com.neel.syntaxvalidation.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void getOrLoad_readsFileIntoCache() throws IOException {
        Path file = createFile("app.js", "line one\nline two\n");

        FileCache cache = new FileCache();
        FileCacheEntry entry = cache.getOrLoad(file);

        assertThat(entry.getLines()).containsExactly("line one", "line two");
        assertThat(entry.getLineCount()).isEqualTo(2);
        assertThat(entry.getAbsolutePath()).isEqualTo(file.toAbsolutePath().toString());
        assertThat(cache.contains(file)).isTrue();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void getOrLoad_returnsSameEntryWithoutRereading() throws IOException {
        Path file = createFile("a.js", "hello\n");

        FileCache cache = new FileCache();
        FileCacheEntry first = cache.getOrLoad(file);
        FileCacheEntry second = cache.getOrLoad(file);

        assertThat(second).isSameAs(first);
    }

    @Test
    void getOrLoad_reloadsWhenFileModified() throws IOException, InterruptedException {
        Path file = createFile("a.js", "v1\n");

        FileCache cache = new FileCache();
        FileCacheEntry first = cache.getOrLoad(file);

        // Bump the modification time so the cache detects staleness.
        Thread.sleep(20);
        Files.writeString(file, "v2\nvery different\n");

        FileCacheEntry reloaded = cache.getOrLoad(file);

        assertThat(reloaded.getLines()).containsExactly("v2", "very different");
        assertThat(reloaded).isNotSameAs(first);
    }

    @Test
    void get_returnsCachedEntryWithoutDiskAccess() throws IOException {
        Path file = createFile("a.js", "x\n");

        FileCache cache = new FileCache();
        cache.getOrLoad(file);

        assertThat(cache.get(file)).isPresent();
        assertThat(cache.get(tempDir.resolve("missing.js"))).isEmpty();
    }

    @Test
    void invalidate_removesEntry() throws IOException {
        Path file = createFile("a.js", "x\n");

        FileCache cache = new FileCache();
        cache.getOrLoad(file);

        assertThat(cache.invalidate(file)).isTrue();
        assertThat(cache.contains(file)).isFalse();
        assertThat(cache.invalidate(file)).isFalse();
    }

    @Test
    void clear_removesAllEntries() throws IOException {
        Path a = createFile("a.js", "1\n");
        Path b = createFile("b.js", "2\n");

        FileCache cache = new FileCache();
        cache.getOrLoad(a);
        cache.getOrLoad(b);
        assertThat(cache.size()).isEqualTo(2);

        cache.clear();
        assertThat(cache.size()).isZero();
    }

    @Test
    void getOrLoad_propagatesIoExceptionForMissingFile() {
        FileCache cache = new FileCache();
        Path missing = tempDir.resolve("nope.js");

        assertThatThrownBy(() -> cache.getOrLoad(missing))
                .isInstanceOf(IOException.class);
    }

    @Test
    void getContent_joinsLinesWithNewline() throws IOException {
        Path file = createFile("a.js", "a\nb\nc\n");

        FileCache cache = new FileCache();
        FileCacheEntry entry = cache.getOrLoad(file);

        assertThat(entry.getContent()).isEqualTo("a\nb\nc");
    }

    private Path createFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
