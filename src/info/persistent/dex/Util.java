package info.persistent.dex;

import vendor.retrace.FrameInfo;
import vendor.retrace.FramePattern;
import vendor.retrace.FrameRemapper;
import vendor.retrace.MappingReader;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Util {

    public static String readableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }


    ///////////////////////////////////////////////////////retrace///////////////////////////////////////////


    /**
     * process mapping
     */
    static boolean unmapping(String mappingFile, String regexString, boolean verbose, Set<String> targetStrings) throws IOException {
        // Create a pattern for stack frames.
        FramePattern pattern = new FramePattern(regexString, verbose);

        // Create a remapper.
        FrameRemapper mapper = new FrameRemapper();

        // Read the mapping file.
        MappingReader mappingReader = new MappingReader(new File(mappingFile));
        mappingReader.pump(mapper);

        Set<String> resultStrings = new HashSet<>();

        // Read and process the lines of the stack trace.
        for (String obfuscatedLine : targetStrings) {

            // Try to match it against the regular expression.
            FrameInfo obfuscatedFrame = pattern.parse(obfuscatedLine);
            if (obfuscatedFrame != null) {
                // Transform the obfuscated frame back to one or more
                // original frames.
                Iterator<FrameInfo> retracedFrames =
                        mapper.transform(obfuscatedFrame).iterator();

                String previousLine = null;

                while (retracedFrames.hasNext()) {
                    // Retrieve the next retraced frame.
                    FrameInfo retracedFrame = retracedFrames.next();

                    // Format the retraced line.
                    String retracedLine =
                            pattern.format(obfuscatedLine, retracedFrame);

                    // Clear the common first part of ambiguous alternative
                    // retraced lines, to present a cleaner list of
                    // alternatives.
                    String trimmedLine =
                            previousLine != null &&
                                    obfuscatedFrame.getLineNumber() == 0 ?
                                    trim(retracedLine, previousLine) :
                                    retracedLine;

                    // Print out the retraced line.
                    if (trimmedLine != null) {
                        resultStrings.add(trimmedLine);
                    }

                    previousLine = retracedLine;
                }
            } else {
                // Print out the original line.
                resultStrings.add(obfuscatedLine);
            }
        }

        if (resultStrings.size() > 0) {
            targetStrings.clear();
            targetStrings.addAll(resultStrings);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the first given string, with any leading characters that it has
     * in common with the second string replaced by spaces.
     */
    private static String trim(String string1, String string2) {
        StringBuilder line = new StringBuilder(string1);

        // Find the common part.
        int trimEnd = firstNonCommonIndex(string1, string2);
        if (trimEnd == string1.length()) {
            return null;
        }

        // Don't clear the last identifier characters.
        trimEnd = lastNonIdentifierIndex(string1, trimEnd) + 1;

        // Clear the common characters.
        for (int index = 0; index < trimEnd; index++) {
            if (!Character.isWhitespace(string1.charAt(index))) {
                line.setCharAt(index, ' ');
            }
        }

        return line.toString();
    }


    /**
     * Returns the index of the first character that is not the same in both
     * given strings.
     */
    private static int firstNonCommonIndex(String string1, String string2) {
        int index = 0;
        while (index < string1.length() &&
                index < string2.length() &&
                string1.charAt(index) == string2.charAt(index)) {
            index++;
        }

        return index;
    }


    /**
     * Returns the index of the last character that is not an identifier
     * character in the given string, at or before the given index.
     */
    private static int lastNonIdentifierIndex(String line, int index) {
        while (index >= 0 &&
                Character.isJavaIdentifierPart(line.charAt(index))) {
            index--;
        }

        return index;
    }
}
