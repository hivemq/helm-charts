package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @see <a
 *         href="https://github.com/apache/commons-io/blob/master/src/main/java/org/apache/commons/io/FilenameUtils.java">FilenameUtils</a>
 */
class FilenameUtils {

    private static final int NOT_FOUND = -1;

    /**
     * The extension separator character.
     */
    public static final char EXTENSION_SEPARATOR = '.';

    /**
     * The Unix separator character.
     */
    private static final char UNIX_SEPARATOR = '/';

    /**
     * The Windows separator character.
     */
    private static final char WINDOWS_SEPARATOR = '\\';

    /**
     * The system separator character.
     */
    private static final char SYSTEM_SEPARATOR = File.separatorChar;

    /**
     * The separator character that is the opposite of the system separator.
     */
    private static final char OTHER_SEPARATOR;

    static {
        if (isSystemWindows()) {
            OTHER_SEPARATOR = UNIX_SEPARATOR;
        } else {
            OTHER_SEPARATOR = WINDOWS_SEPARATOR;
        }
    }

    /**
     * Instances should NOT be constructed in standard programming.
     */
    private FilenameUtils() {
    }

    /**
     * Removes the extension from a fileName.
     * <p>
     * This method returns the textual part of the fileName before the last dot.
     * There must be no directory separator after the dot.
     * <pre>
     * foo.txt    --&gt; foo
     * a\b\c.jpg  --&gt; a\b\c
     * a\b\c      --&gt; a\b\c
     * a.b\c      --&gt; a.b\c
     * </pre>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     *
     * @param fileName the fileName to query, null returns null
     * @return the fileName minus the extension
     */
    public static @NotNull String removeExtension(final @NotNull String fileName) {
        requireNonNullChars(fileName);

        final var index = indexOfExtension(fileName);
        if (index == NOT_FOUND) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    /**
     * Checks the input for null bytes, a sign of unsanitized data being passed to file level functions.
     * <p/>
     * This may be used for poison byte attacks.
     *
     * @param path the path to check
     */
    private static void requireNonNullChars(final String path) {
        if (path.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Null byte present in file/path name. There are no " +
                    "known legitimate use cases for such data, but several injection attacks may use it");
        }
    }

    /**
     * Returns the index of the last extension separator character, which is a dot.
     * <p>
     * This method also checks that there is no directory separator after the last dot. To do this it uses
     * {@link #indexOfLastSeparator(String)} which will handle a file in either Unix or Windows format.
     * </p>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on, with the
     * exception of a possible {@link IllegalArgumentException} on Windows (see below).
     * </p>
     * <b>Note:</b> This method used to have a hidden problem for names like "foo.exe:bar.txt".
     * In this case, the name wouldn't be the name of a file, but the identifier of an
     * alternate data stream (bar.txt) on the file foo.exe. The method used to return
     * ".txt" here, which would be misleading. Commons IO 2.7, and later versions, are throwing
     * an {@link IllegalArgumentException} for names like this.
     *
     * @param fileName the fileName to find the last extension separator in, null returns -1
     * @return the index of the last extension separator character, or -1 if there is no such character
     * @throws IllegalArgumentException <b>Windows only:</b> The fileName parameter is, in fact,
     *                                  the identifier of an Alternate Data Stream, for example "foo.exe:bar.txt".
     */
    private static int indexOfExtension(final @NotNull String fileName) throws IllegalArgumentException {
        if (isSystemWindows()) {
            // special handling for NTFS ADS: don't accept colon in the fileName
            final var offset = fileName.indexOf(':', getAdsCriticalOffset(fileName));
            if (offset != -1) {
                throw new IllegalArgumentException("NTFS ADS separator (':') in file name is forbidden.");
            }
        }
        final var extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        final var lastSeparator = indexOfLastSeparator(fileName);
        return lastSeparator > extensionPos ? NOT_FOUND : extensionPos;
    }

    /**
     * Determines if Windows file system is in use.
     *
     * @return true if the system is Windows
     */
    private static boolean isSystemWindows() {
        return SYSTEM_SEPARATOR == WINDOWS_SEPARATOR;
    }

    /**
     * Special handling for NTFS ADS: Don't accept colon in the fileName.
     *
     * @param fileName a file name
     * @return ADS offsets.
     */
    private static int getAdsCriticalOffset(final @NotNull String fileName) {
        // step 1: remove leading path segments
        final var offset1 = fileName.lastIndexOf(SYSTEM_SEPARATOR);
        final var offset2 = fileName.lastIndexOf(OTHER_SEPARATOR);
        if (offset1 == -1) {
            if (offset2 == -1) {
                return 0;
            }
            return offset2 + 1;
        }
        if (offset2 == -1) {
            return offset1 + 1;
        }
        return Math.max(offset1, offset2) + 1;
    }

    /**
     * Returns the index of the last directory separator character.
     * <p>
     * This method will handle a file in either Unix or Windows format.
     * The position of the last forward or backslash is returned.
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     *
     * @param fileName the fileName to find the last path separator in, null returns -1
     * @return the index of the last separator character, or -1 if there
     *         is no such character
     */
    private static int indexOfLastSeparator(final @NotNull String fileName) {
        final var lastUnixPos = fileName.lastIndexOf(UNIX_SEPARATOR);
        final var lastWindowsPos = fileName.lastIndexOf(WINDOWS_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }
}
