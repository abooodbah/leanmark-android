package io.github.abooodbah.leanmark;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves test documents through pipes, as many real providers do, so the app
 * cannot rely on seeking or on a file path. Names ending in "-nosize" report no
 * size, like providers that stream from the network or an archive.
 *
 * <p>Written in Java on purpose: Android runs this provider in the test
 * package's own process, where the Kotlin standard library is missing because
 * the build leaves it out of the test APK (the app already has it).
 */
public final class FixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.abooodbah.leanmark.fixtures";

    /** About 1 MiB: the "300-page" document used for memory and scrolling. */
    public static final long LARGE_BYTES = 1L * 1024 * 1024;

    /** Well past the 32 MiB limit, so a reader that stops at the limit visibly stops early. */
    public static final long HUGE_BYTES = 40L * 1024 * 1024;

    private static final ConcurrentHashMap<String, Long> WRITTEN = new ConcurrentHashMap<>();

    public static Uri uri(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    private interface Sink {
        void write(byte[] chunk) throws IOException;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        return name != null && name.endsWith(".txt") ? "text/plain" : "text/markdown";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String name = name(uri);
        String[] columns = projection != null
            ? projection
            : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = name.replace("-nosize", "");
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                try {
                    row[i] = name.contains("-nosize") ? null : sizeOf(name);
                } catch (IOException e) {
                    throw new IllegalArgumentException(name, e);
                }
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("read only");
        String name = name(uri);
        ParcelFileDescriptor[] pipe;
        try {
            sizeOf(name); // Throws for unknown names before a pipe is made.
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException e) {
            throw new FileNotFoundException(e.toString());
        }
        WRITTEN.remove(name);
        ParcelFileDescriptor write = pipe[1];
        new Thread(() -> {
            long[] total = {0};
            try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(write)) {
                write(name, chunk -> {
                    out.write(chunk);
                    total[0] += chunk.length;
                });
            } catch (IOException closed) {
                // The reader stopped early, which is what the size limit should do.
            }
            WRITTEN.put(name, total[0]);
        }).start();
        return pipe[0];
    }

    /** "written": how many bytes the last stream of arg delivered, or -1 while it runs. */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"written".equals(method) || arg == null) return null;
        Bundle result = new Bundle();
        Long bytes = WRITTEN.get(arg);
        result.putLong("bytes", bytes == null ? -1 : bytes);
        return result;
    }

    private static String name(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) throw new IllegalArgumentException(String.valueOf(uri));
        return name;
    }

    private long sizeOf(String name) throws IOException {
        long[] total = {0};
        write(name, chunk -> total[0] += chunk.length);
        return total[0];
    }

    private void write(String name, Sink out) throws IOException {
        String base = name.replace("-nosize", "");
        switch (base) {
            case "simple.md":
            case "showcase.md":
            case "security.md":
            case "linked-document.md":
                out.write(asset("fixtures/" + base));
                break;
            case "notes.txt":
                out.write(asset("fixtures/simple.md"));
                break;
            case "legacy-encoding.md":
                // The showcase as a legacy editor would save it in Windows-1252.
                // Characters outside that code page become "?", as they do there.
                out.write(new String(asset("fixtures/showcase.md"), StandardCharsets.UTF_8)
                    .getBytes(Charset.forName("windows-1252")));
                break;
            case "large.md":
                repeatReal(LARGE_BYTES, out);
                break;
            case "huge.md":
                repeatReal(HUGE_BYTES, out);
                break;
            default:
                throw new FileNotFoundException(name);
        }
    }

    /** The desktop repositories' own documentation, repeated up to minimum bytes. */
    private void repeatReal(long minimum, Sink out) throws IOException {
        String[] names = assets().list("real");
        if (names == null) throw new FileNotFoundException("real");
        Arrays.sort(names);
        byte[][] parts = new byte[names.length][];
        byte[] gap = "\n\n".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < names.length; i++) {
            byte[] text = asset("real/" + names[i]);
            parts[i] = Arrays.copyOf(text, text.length + gap.length);
            System.arraycopy(gap, 0, parts[i], text.length, gap.length);
        }
        long written = 0;
        while (written < minimum) {
            for (byte[] part : parts) {
                out.write(part);
                written += part.length;
            }
        }
    }

    private AssetManager assets() {
        return getContext().getAssets();
    }

    private byte[] asset(String path) throws IOException {
        try (InputStream in = assets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
