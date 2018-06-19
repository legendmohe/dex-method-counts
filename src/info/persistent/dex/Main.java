/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.persistent.dex;

import vendor.com.android.dexdeps.DexData;
import vendor.com.android.dexdeps.DexDataException;
import vendor.com.android.dexdeps.MethodRef;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class Main {

    private static final String STACK_TRACE_METHOD_EXPRESSION = "%t %c\\.%m\\(%a\\)";
    private static final String STACK_TRACE_CLASS_EXPRESSION = "%c";

    private String[] mMappingFiles;

    public static void main(String[] args) {
        Main main = new Main();
        main.run(args);
    }

    void run(String[] args) {
        try {
            String[] inputFileNames = parseArgs(args);
//            String[] inputFileNames = new String[]{"old.apk", "new.apk"};
            String[] mappingFileNames = mMappingFiles;
//            String[] mappingFileNames = new String[]{"old-mapping.txt", "new-mapping.txt"};

            Set<String> oldMethods = new HashSet<>();
            Set<String> oldClasses = new HashSet<>();
            String oldFileName = "";

            List<String> collectFileNames = collectFileNames(inputFileNames);
            for (int i = 0, collectFileNamesSize = collectFileNames.size(); i < collectFileNamesSize; i++) {
                String fileName = collectFileNames.get(i);
                System.out.println("Processing " + fileName);
                System.out.println("file size:" + Util.readableFileSize(new File(fileName).length()));

                Set<String> newMethods = new HashSet<>();
                Set<String> newClasses = new HashSet<>();
                for (RandomAccessFile dexFile : openInputFiles(fileName)) {
                    DexData dexData = new DexData(dexFile);
                    dexData.load();

                    processMethods(newMethods, dexData);
                    processClasses(dexData, newClasses);

                    dexFile.close();
                }

                System.out.println("total classes[" + newClasses.size() + "]");
                System.out.println("total methods[" + newMethods.size() + "]");


                if (mappingFileNames != null && mappingFileNames.length > 0) {
                    if (mappingFileNames.length > i) {
                        System.out.println("unmapping...");
                        Util.unmapping(mappingFileNames[i], STACK_TRACE_CLASS_EXPRESSION, false, newClasses);
                        Util.unmapping(mappingFileNames[i], STACK_TRACE_METHOD_EXPRESSION, false, newMethods);
                    } else {
                        System.err.println("unmatched mapping file for file[" + fileName + "]");
                    }
                }

                if (oldClasses.size() != 0) {
                    System.out.println("====class diff old:" + oldFileName + " new:" + fileName + "====");
                    processOldAndNewStringList(oldClasses, newClasses);
                }
                if (oldMethods.size() != 0) {
                    System.out.println("====method diff old:" + oldFileName + " new:" + fileName + "====");
                    processOldAndNewStringList(oldMethods, newMethods);
                }

                oldMethods = newMethods;
                oldClasses = newClasses;
                oldFileName = fileName;
            }
        } catch (UsageException ue) {
            usage();
            System.exit(2);
        } catch (IOException ioe) {
            if (ioe.getMessage() != null) {
                System.err.println("Failed: " + ioe);
            }
            System.exit(1);
        } catch (DexDataException dde) {
            /* a message was already reported, just bail quietly */
            System.exit(1);
        }
    }

    private void processClasses(DexData dexData, Set<String> newClasses) {
        Set<String> allClassNames = dexData.getAllClassNames();
        for (String className : allClassNames) {
            String elementString = formatTypeElementString(className);
            if (elementString != null && elementString.length() > 0) {
                newClasses.add(elementString);
            }
        }
    }

    private void processMethods(Set<String> newMethods, DexData dexData) {
        MethodRef[] methodRefs = dexData.getMethodRefs();
        for (MethodRef methodRef : methodRefs) {
            String methodString = getFormattedMethodString(methodRef);
            if (methodString != null && methodString.length() > 0) {
                newMethods.add(methodString);
            }
        }
    }

    private void processOldAndNewStringList(Set<String> oldStrings, Set<String> newStrings) {
        Set<String> addedMethodSet = new HashSet<>(newStrings);
        addedMethodSet.removeAll(oldStrings);

        Set<String> removedMethodSet = new HashSet<>(oldStrings);
        removedMethodSet.removeAll(newStrings);

        List<String> orderedAddSet = new ArrayList<>(addedMethodSet);
        Collections.sort(orderedAddSet);

        StringBuilder sb = new StringBuilder();
        sb.append("<" + orderedAddSet.size() + " added>\n");
        for (String s : orderedAddSet) {
            sb.append(s).append('\n');
        }

        List<String> orderedRemoveSet = new ArrayList<>(removedMethodSet);
        Collections.sort(orderedRemoveSet);

        sb.append("\n").append("<" + orderedRemoveSet.size() + " removed>\n");
        for (String s : orderedRemoveSet) {
            sb.append(s).append('\n');
        }

        System.out.println(sb.toString());
    }

    private String getFormattedMethodString(MethodRef methodRef) {
        try {

            // class name
            String className = methodRef.getDeclClassName();
            className = className.substring(1, className.length() - 1).replace("/", ".");

            // method name
            String methodName = methodRef.getName();
            // 跳过access***方法
            if (methodName.contains("$")) {
                return null;
            }

            // params
            String desc = methodRef.getDescriptor();
            int sepIndex = desc.indexOf(")");
            String paramListString = desc.substring(1, sepIndex);
            paramListString = formatParamListString(paramListString);

            // return value
            String returnString = methodRef.getReturnTypeName();
            returnString = formatTypeElementString(returnString);

            // combine
            return returnString + " " + className + "." + methodName + "(" + paramListString + ")";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 标识字符	含义
     * B	基本类型byte
     * C	char
     * D	double
     * F	float
     * I	int
     * J	long
     * S	short
     * Z	boolean
     * V	void
     * L	对象类型，如Ljava/lang/Object;
     *
     * @param param
     * @return
     */
    private String formatParamListString(String param) {
        StringBuilder resultSb = new StringBuilder();
        int i = 0;
        while (i < param.length()) {
            char head = param.charAt(i);

            // array
            boolean inArray = false;
            if (head == '[') {
                inArray = true;
                head = param.charAt(++i);
            }

            switch (head) {
                case 'B':
                    resultSb.append("byte");
                    break;
                case 'C':
                    resultSb.append("char");
                    break;
                case 'D':
                    resultSb.append("double");
                    break;
                case 'F':
                    resultSb.append("float");
                    break;
                case 'J':
                    resultSb.append("long");
                    break;
                case 'S':
                    resultSb.append("short");
                    break;
                case 'I':
                    resultSb.append("int");
                    break;
                case 'V':
                    resultSb.append("void");
                    break;
                case 'Z':
                    resultSb.append("boolean");
                    break;
            }
            if (head == 'L') {
                int objectEndMark = param.indexOf(";", i);
                String objectStr = param.substring(i, objectEndMark);
                resultSb.append(formatTypeElementString(objectStr));

                i = objectEndMark + 1;
            } else {
                i++;
            }

            if (inArray) {
                resultSb.append("[]");
            }
            resultSb.append(", ");
        }

        if (resultSb.length() > 0) {
            resultSb.deleteCharAt(resultSb.length() - 1)
                    .deleteCharAt(resultSb.length() - 1);
        }
        return resultSb.toString();
    }

    /**
     * 标识字符	含义
     * B	基本类型byte
     * C	char
     * D	double
     * F	float
     * I	int
     * J	long
     * S	short
     * Z	boolean
     * V	void
     * L	对象类型，如Ljava/lang/Object;
     *
     * @param returnString
     * @return
     */
    private String formatTypeElementString(String returnString) {
        boolean isArray = false;
        if (returnString.startsWith("[")) {
            returnString = returnString.substring(1);
            isArray = true;
        }

        // base
        switch (returnString) {
            case "B":
                return "byte";
            case "C":
                return "char";
            case "D":
                return "double";
            case "F":
                return "float";
            case "J":
                return "long";
            case "S":
                return "short";
            case "I":
                return "int";
            case "V":
                return "void";
            case "Z":
                return "boolean";
        }
        // Object
        if (returnString.startsWith("L")) {
            returnString = returnString.substring(1).replace("/", ".");
        }
        if (returnString.endsWith(";")) {
            returnString = returnString.substring(0, returnString.length() - 1);
        }
        // array
        if (isArray) {
            returnString += "[]";
        }
        return returnString;
    }

    /**
     * Opens an input file, which could be a .dex or a .jar/.apk with a
     * classes.dex inside.  If the latter, we extract the contents to a
     * temporary file.
     */
    List<RandomAccessFile> openInputFiles(String fileName) throws IOException {
        List<RandomAccessFile> dexFiles = new ArrayList<RandomAccessFile>();

        openInputFileAsZip(fileName, dexFiles);
        if (dexFiles.size() == 0) {
            File inputFile = new File(fileName);
            RandomAccessFile dexFile = new RandomAccessFile(inputFile, "r");
            dexFiles.add(dexFile);
        }

        return dexFiles;
    }

    /**
     * Tries to open an input file as a Zip archive (jar/apk) with a
     * "classes.dex" inside.
     */
    void openInputFileAsZip(String fileName, List<RandomAccessFile> dexFiles) throws IOException {
        ZipFile zipFile;

        // Try it as a zip file.
        try {
            zipFile = new ZipFile(fileName);
        } catch (FileNotFoundException fnfe) {
            // not found, no point in retrying as non-zip.
            System.err.println("Unable to open '" + fileName + "': " +
                    fnfe.getMessage());
            throw fnfe;
        } catch (ZipException ze) {
            // not a zip
            return;
        }

        // Open and add all files matching "classes.*\.dex" in the zip file.
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            if (entry.getName().matches("classes.*\\.dex")) {
                dexFiles.add(openDexFile(zipFile, entry));
            }
        }

        zipFile.close();
    }

    RandomAccessFile openDexFile(ZipFile zipFile, ZipEntry entry) throws IOException {
        // We know it's a zip; see if there's anything useful inside.  A
        // failure here results in some type of IOException (of which
        // ZipException is a subclass).
        InputStream zis = zipFile.getInputStream(entry);

        // Create a temp file to hold the DEX data, open it, and delete it
        // to ensure it doesn't hang around if we fail.
        File tempFile = File.createTempFile("dexdeps", ".dex");
        RandomAccessFile dexFile = new RandomAccessFile(tempFile, "rw");
        tempFile.delete();

        // Copy all data from input stream to output file.
        byte copyBuf[] = new byte[32768];
        int actual;

        while (true) {
            actual = zis.read(copyBuf);
            if (actual == -1)
                break;

            dexFile.write(copyBuf, 0, actual);
        }

        dexFile.seek(0);

        return dexFile;
    }

    private String[] parseArgs(String[] args) {
        int idx = 0;

        for (idx = 0; idx < args.length; idx++) {
            String arg = args[idx];

            if (arg.equals("--") || !arg.startsWith("--")) {
                break;
            } else if (arg.startsWith("--mapping")) {
                mMappingFiles = arg.substring(arg.indexOf('=') + 1).split(",");
            } else {
                System.err.println("Unknown option '" + arg + "'");
                throw new UsageException();
            }
        }

        int fileCount = args.length - idx;
        if (fileCount == 0) {
            throw new UsageException();
        }
        String[] inputFileNames = new String[fileCount];
        System.arraycopy(args, idx, inputFileNames, 0, fileCount);
        return inputFileNames;
    }

    private void usage() {
        System.err.print(
                "DEX per-package/class method diff v1.0\n" +
                        "Usage: dex-method-diff --mapping=<mapping file,> <file.{dex,apk,jar,directory}> ...\n"
        );
    }

    /**
     * Checks if input files array contain directories and
     * adds it's contents to the file list if so.
     * Otherwise just adds a file to the list.
     *
     * @return a List of file names to process
     */
    private List<String> collectFileNames(String[] inputFileNames) {
        List<String> fileNames = new ArrayList<String>();
        for (String inputFileName : inputFileNames) {
            File file = new File(inputFileName);
            if (file.isDirectory()) {
                String dirPath = file.getAbsolutePath();
                for (String fileInDir : file.list()) {
                    fileNames.add(dirPath + File.separator + fileInDir);
                }
            } else {
                fileNames.add(inputFileName);
            }
        }
        return fileNames;
    }

    private static class UsageException extends RuntimeException {
    }
}
