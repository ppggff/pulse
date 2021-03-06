/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.util.io;

import com.google.common.base.Predicate;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.zutubi.util.*;
import com.zutubi.util.logging.Logger;

import java.io.*;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Miscellaneous utilities for manipulating the file system.
 *
 * @author jsankey
 */
public class FileSystemUtils
{
    private static final Logger LOG = Logger.getLogger(FileSystemUtils.class);

    private static final String PROPERTY_USE_EXTERNAL_COPY = "pulse.use.external.copy";
    private static final String PROPERTY_ROBUST_DELAY_MILLIS = "pulse.fs.robust.delay.millis";
    private static final String PROPERTY_ROBUST_RETRIES = "pulse.fs.robust.retries";
    public static final String PROPERTY_RMDIR_COMMAND = "pulse.fs.rmdir.command";

    private static final int ROBUST_DELAY_MILLIS = Integer.getInteger(PROPERTY_ROBUST_DELAY_MILLIS, 100);
    private static final int ROBUST_RETRIES = Integer.getInteger(PROPERTY_ROBUST_RETRIES, 3);

    private static final String VARIABLE_DIR_OLD = "${dir}";
    private static final String VARIABLE_DIR = "$(dir)";

    public static final String NORMAL_SEPARATOR = "/";
    public static final char NORMAL_SEPARATOR_CHAR = NORMAL_SEPARATOR.charAt(0);

    // Unix-style file mode values

    public static final int PERMISSION_OWNER_READ = 0x100;
    public static final int PERMISSION_OWNER_WRITE = 0x080;
    public static final int PERMISSION_OWNER_EXECUTE = 0x040;
    public static final int PERMISSION_GROUP_READ = 0x020;
    public static final int PERMISSION_GROUP_WRITE = 0x010;
    public static final int PERMISSION_GROUP_EXECUTE = 0x008;
    public static final int PERMISSION_OTHER_READ = 0x004;
    public static final int PERMISSION_OTHER_WRITE = 0x002;
    public static final int PERMISSION_OTHER_EXECUTE = 0x001;

    public static final int PERMISSION_OWNER_FULL = PERMISSION_OWNER_READ | PERMISSION_OWNER_WRITE | PERMISSION_OWNER_EXECUTE;
    public static final int PERMISSION_GROUP_FULL = PERMISSION_GROUP_READ | PERMISSION_GROUP_WRITE | PERMISSION_GROUP_EXECUTE;
    public static final int PERMISSION_OTHER_FULL = PERMISSION_OTHER_READ | PERMISSION_OTHER_WRITE | PERMISSION_OTHER_EXECUTE;

    public static final int PERMISSION_ALL_READ = PERMISSION_OWNER_READ | PERMISSION_GROUP_READ | PERMISSION_OTHER_READ;
    public static final int PERMISSION_ALL_WRITE = PERMISSION_OWNER_WRITE | PERMISSION_GROUP_WRITE | PERMISSION_OTHER_WRITE;
    public static final int PERMISSION_ALL_EXECUTE = PERMISSION_OWNER_EXECUTE | PERMISSION_GROUP_EXECUTE | PERMISSION_OTHER_EXECUTE;
    public static final int PERMISSION_ALL_FULL = PERMISSION_OWNER_FULL | PERMISSION_GROUP_FULL | PERMISSION_OTHER_FULL;

    public static final boolean CP_AVAILABLE;
    public static final boolean LN_AVAILABLE;
    public static final boolean STAT_AVAILABLE;
    public static final boolean ZIP_AVAILABLE;
    public static final String THIS_DIRECTORY = ".";
    public static final String PARENT_DIRECTORY = "..";

    static
    {
        CP_AVAILABLE = SystemUtils.unixBinaryAvailable("cp");
        LN_AVAILABLE = SystemUtils.unixBinaryAvailable("ln");
        STAT_AVAILABLE = SystemUtils.unixBinaryAvailable("stat");
        ZIP_AVAILABLE = SystemUtils.unixBinaryAvailable("zip");
    }

    /**
     * Recursively delete a directory and its contents.
     *
     * @param dir the directory to delete
     * @throws IOException if the directory could not be deleted
     */
    public static void rmdir(File dir) throws IOException
    {
        if (dir == null)
        {
            throw new IllegalArgumentException("dir cannot be null");
        }

        if (!dir.exists())
        {
            return;
        }

        if (dir.isFile())
        {
            throw new IllegalArgumentException(String.format("removeDirectory can only be used on directories. %s is not a directory.", dir));
        }

        if (System.getProperty(PROPERTY_RMDIR_COMMAND) == null)
        {
            internalRmdir(dir);
        }
        else
        {
            externalRmdir(dir);
        }
    }

    private static void internalRmdir(File dir) throws IOException
    {
        String canonicalDir = dir.getCanonicalPath();
        String[] contents = list(dir);
        if (contents.length > 0)
        {
            for (String child : contents)
            {
                File file = new File(dir, child);
                String canonicalFile;

                // The canonical path lets us distinguish symlinks from actual
                // directories.
                canonicalFile = file.getCanonicalPath();

                // We don't want to traverse symbolic links to directories.
                // The canonical path tells us where the file really is, and we
                // double check it is under the directory (using the canonical
                // path for the directory too).
                if (file.isDirectory() && canonicalFile.equals(composeFilename(canonicalDir, file.getName())))
                {
                    rmdir(file);
                }
                else
                {
                    if (!robustDelete(file))
                    {
                        throw new IOException("Unable to remove file '" + file.getAbsolutePath() + "'");
                    }
                }
            }
        }

        if (!robustDelete(dir))
        {
            throw new IOException("Unable to remove directory '" + dir.getAbsolutePath() + "'");
        }
    }

    private static void externalRmdir(File dir) throws IOException
    {
        ProcessBuilder processBuilder = new ProcessBuilder(createRmdirCommand(dir));
        processBuilder.redirectErrorStream(true);
        Process child = processBuilder.start();
        try
        {
            ByteStreams.copy(child.getInputStream(), ByteStreams.nullOutputStream());
            int exitCode = child.waitFor();
            if(exitCode != 0)
            {
                throw new IOException("External rmdir process returned code " + exitCode);
            }
        }
        catch (InterruptedException e)
        {
            throw new IOException("Interrupted waiting for external rmdir process");
        }
        finally
        {
            child.destroy();
        }
    }

    private static List<String> createRmdirCommand(File dir)
    {
        List<String> command = new LinkedList<String>();
        for (String piece: StringUtils.split(System.getProperty(PROPERTY_RMDIR_COMMAND)))
        {
            if (VARIABLE_DIR.equals(piece) || VARIABLE_DIR_OLD.equals(piece))
            {
                command.add(dir.getAbsolutePath());
            }
            else
            {
                command.add(piece);
            }
        }

        return command;
    }

    private static boolean robustFn(File f, Predicate<File> fn)
    {
        boolean success = false;
        for (int i = 0; i < ROBUST_RETRIES; i++)
        {
            success = fn.apply(f);
            if (success)
            {
                break;
            }
            else
            {
                // Yes, this is obscene, but it works around bugs in some
                // Windows JVMs.
                System.gc();
                try
                {
                    Thread.sleep(ROBUST_DELAY_MILLIS);
                }
                catch (InterruptedException e)
                {
                    // Just pass it on.
                    Thread.currentThread().interrupt();
                }
            }
        }

        return success;
    }

    /**
     * A more robust version of {@link File#delete} which retries a few times
     * on failure.  Most useful on Windows where deleting can fail due to
     * external forces beyond our control.
     *
     * @param f file to delete
     * @return true iff the file was successfully deleted
     */
    public static boolean robustDelete(File f)
    {
        return robustFn(f, new Predicate<File>()
        {
            public boolean apply(File file)
            {
                return file.delete();
            }
        });
    }

    /**
     * A more robust version of {@link File#renameTo(java.io.File)} which
     * retries a few times on failure.  Most useful on Windows where renaming
     * can fail due to external forces beyond our control.
     *
     * @param src  source file to be renamed
     * @param dest destination to rename the file to
     * @throws java.io.IOException if the rename fails
     */
    public static void robustRename(File src, final File dest) throws IOException
    {
        boolean success = robustFn(src, new Predicate<File>()
        {
            public boolean apply(File file)
            {
                return file.renameTo(dest);
            }
        });

        if (!success)
        {
            String message = "Unable to rename '" + src.getAbsolutePath() + "' to '" + dest.getAbsolutePath() + "'";
            if (!src.exists())
            {
                // It may exist but just be invisible to us.
                File sourceParent = src.getParentFile();
                if (sourceParent != null && sourceParent.exists() && !sourceParent.canRead())
                {
                    message += ": source's parent directory is not readable";
                }
                else
                {
                    message += ": source does not exist";
                }
            }

            if (dest.exists())
            {
                message += ": destination already exists";
            }
            else
            {
                File destParent = dest.getParentFile();
                if (destParent != null && !destParent.canWrite())
                {
                    message += ": destination's parent directory is not writable";
                }
            }
            
            throw new IOException(message);
        }
    }

    public static void cleanOutputDir(File output) throws IOException
    {
        if (output.isDirectory())
        {
            FileSystemUtils.rmdir(output);
        }

        if (!output.mkdirs())
        {
            throw new IOException("Unable to create output directory '" + output.getPath() + "'");
        }
    }

    /**
     * Gets the location of the JVM's working directory.
     * 
     * @return the working directory
     */
    public static File getWorkingDirectory()
    {
        String userDir = System.getProperty("user.dir");
        if (StringUtils.stringSet(userDir))
        {
            return new File(userDir);
        }
        else
        {
            return new File(".");
        }
    }
    
    /**
     * Gets the location to use for storing temporary files, controlled by a
     * standard system property.
     *
     * @return the system temporary directory
     */
    public static File getSystemTempDir()
    {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Create a temporary directory using pre-defined prefix and suffix values.  Use this when you
     * really don't care what the directory is called.
     *
     * @return a File object for the created directory
     * @throws IOException if the directory cannot be created
     */
    public static File createTempDir() throws IOException
    {
        return createTempDir("dir");
    }

    /**
     * Create a temporary directory using a pre-defined suffix values.  The directory name will
     * begin with the given prefix.
     *
     * @param prefix string to use as the start of the directory name
     * @return a File object for the created directory
     * @throws IOException if the directory cannot be created
     */
    public static File createTempDir(String prefix) throws IOException
    {
        return createTempDir(prefix, ".tmp");
    }

    public static File createTempDir(String prefix, String suffix) throws IOException
    {
        return createTempDir(prefix, suffix, null);
    }

    public static File createTempDir(String prefix, String suffix, File base) throws IOException
    {
        if (base != null && !base.exists() && !base.mkdirs())
        {
            throw new IOException("Failed to create temporary directory. Base directory does not exist: " + base.getAbsolutePath());
        }

        if (base == null)
        {
            base = getSystemTempDir();
        }

        if (!StringUtils.stringSet(prefix))
        {
            prefix = "";
        }
        if (!StringUtils.stringSet(suffix))
        {
            suffix = "";
        }

        File tmp;
        int tries = 0;
        do
        {
            tmp = new File(base, prefix + RandomUtils.insecureRandomString(7 + tries / 10) + suffix);
            tries++;
        }
        while (tmp.exists());

        if (!tmp.mkdirs())
        {
            throw new IOException("Failed to create temporary directory. Reason: tmpDir.mkdirs failed.");
        }
        return tmp;
    }

    /**
     * Creates a new file at the given path.
     * 
     * @param file the file to create
     * @param deleteExisting if true, when the file already exists it will be replaced with a new,
     *                       empty file (otherwise an error is thrown in this case)
     * @throws IOException if the file already exists and deleteExisting is false, if an existing
     *                     file could not be deleted, or if creation fails for some other reason
     */
    public static void createNewFile(File file, boolean deleteExisting) throws IOException
    {
        if (file.exists())
        {
            if (deleteExisting)
            {
                if (!file.delete())
                {
                    throw new IOException(String.format("Can not create file. Existing file '%s' could not be deleted.", file));                    
                }
            }
            else
            {
                throw new IOException(String.format("Can not create file. File '%s' already exists.", file));
            }
        }
        
        if (!file.createNewFile())
        {
            throw new IOException(String.format("Failed to create file '%s'", file));
        }
    }

    public static void createDirectory(File file) throws IOException
    {
        if (file.exists())
        {
            if (!file.isDirectory())
            {
                throw new IOException(String.format("Can not create directory. File '%s' already exists.", file));
            }
            return;
        }
        if (!file.mkdirs())
        {
            throw new IOException(String.format("Failed to create directory '%s'", file));
        }
    }

    // code snippet taken and adapted from org.apache.commons.vfs.provider.AbstractFileName
    public static String relativePath(File from, File to)
    {
        final String path = to.getPath();

        // Calculate the common prefix
        final int basePathLen = from.getPath().length();
        final int pathLen = path.length();

        // Deal with root
        if (basePathLen == 1 && pathLen == 1)
        {
            return ".";
        }
        else if (basePathLen == 1)
        {
            return path.substring(1);
        }

        final int maxlen = Math.min(basePathLen, pathLen);
        int index = 0;
        while (index < maxlen && from.getPath().charAt(index) == path.charAt(index))
        {
            index++;
        }

        if (index == basePathLen && index == pathLen)
        {
            // Same names
            return ".";
        }
        else if (index == basePathLen && index < pathLen && path.charAt(index) == File.separatorChar)
        {
            // A descendent of the base path
            return path.substring(index + 1);
        }

        // Strip the common prefix off the path
        final StringBuilder builder = new StringBuilder();
        if (pathLen > 1 && (index < pathLen || from.getPath().charAt(index) != File.separatorChar))
        {
            // Not a direct ancestor, need to back up
            index = from.getPath().lastIndexOf(File.separatorChar, index);
            builder.append(path.substring(index));
        }

        // Prepend a '../' for each element in the base path past the common
        // prefix
        builder.insert(0, "..");
        index = from.getPath().indexOf(File.separatorChar, index + 1);
        while (index != -1)
        {
            builder.insert(0, "../");
            index = from.getPath().indexOf(File.separatorChar, index + 1);
        }
        return builder.toString();
    }

    public static boolean isParentOf(File parent, File child) throws IOException
    {
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();

        return childPath.startsWith(parentPath);
    }

    /**
     * Returns true iff the given file is a relative symlink.
     *
     * @param file file to test
     * @return true if the file is a symlink with a relative path
     */
    public static boolean isRelativeSymlink(File file)
    {
        // WARNING: only detects relative symlinks
        if (!SystemUtils.IS_WINDOWS)
        {
            // Try testing the canonical path then.
            File parent = file.getParentFile();
            if (parent != null)
            {
                try
                {
                    String parentCanonical = parent.getCanonicalPath() + "/";
                    String fileCanonical = file.getCanonicalPath();

                    if (fileCanonical.startsWith(parentCanonical))
                    {
                        String canonicalName = fileCanonical.substring(parentCanonical.length());
                        return !canonicalName.equals(file.getName());
                    }
                }
                catch (IOException e)
                {
                    LOG.warning(e);
                }
            }
        }

        return false;
    }

    /**
     * On supported systems, returns the permissions of the given file
     * encoded as a single integer.  The encoding depends on the platform:
     * <p/>
     * - Un*x: the same mode format used by chmod/stat.
     * <p/>
     * On unsupported platforms, this call always returns -1.
     *
     * @param file the file to return the permissions for
     * @return the permissions of the given file, or -1 if they cannot be
     *         determined
     */
    public static int getPermissions(File file)
    {
        int result = -1;

        if (SystemUtils.IS_WINDOWS || !STAT_AVAILABLE)
        {
            return -1;
        }

        Process process = null;
        try
        {
            try
            {
                String[] command;
                if (SystemUtils.IS_LINUX)
                {
                    command = new String[]{"stat", "-c", "%a", file.getAbsolutePath()};
                }
                else
                {
                    command = new String[]{"stat", "-f", "%Lp", file.getAbsolutePath()};
                }

                process = Runtime.getRuntime().exec(command);
            }
            catch (IOException e)
            {
                // This occurs when there is no stat: i.e. unsupported platform.
                return 0;
            }

            InputStreamReader stdoutReader = new InputStreamReader(process.getInputStream());
            StringWriter stdoutWriter = new StringWriter();
            CharStreams.copy(stdoutReader, stdoutWriter);

            int exitCode = process.waitFor();

            if (exitCode == 0)
            {
                result = Integer.parseInt(stdoutWriter.getBuffer().toString().trim(), 8);
            }
            else
            {
                LOG.warning("Unable to get permissions for '%s': stat exited with code %d", file.getAbsolutePath(), exitCode);
            }
        }
        catch (Exception e)
        {
            LOG.warning("Unable to get permissions for '" + file.getAbsolutePath() + "': " + e.getMessage(), e);
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
            }
        }

        return result;
    }

    /**
     * Attempts to set the permissions on the given file to the given
     * permissions.  Not supported on all systems.
     *
     * @param file        the file to set permissions on
     * @param permissions the permissions to set, as encoded by
     *                    {@link #getPermissions(java.io.File)}
     * @return true if the operation succeeded
     */
    public static boolean setPermissions(File file, int permissions)
    {
        if (SystemUtils.IS_WINDOWS || permissions < 0)
        {
            return false;
        }

        return runChmod(file, Integer.toString(permissions, 8));
    }

    public static boolean setExecutable(File file)
    {
        return setExecutable(file, true);
    }

    public static boolean setExecutable(File file, boolean executable)
    {
        if (executable)
        {
            return runChmod(file, "a+x");
        }
        else
        {
            return runChmod(file, "a-x");
        }
    }

    public static boolean setWritable(File file)
    {
        return setWritable(file, true);
    }

    public static boolean setWritable(File file, boolean writable)
    {
        if (writable)
        {
            if (SystemUtils.IS_WINDOWS)
            {
                try
                {
                    SystemUtils.runCommand("attrib", "-R", file.getAbsolutePath());
                    return true;
                }
                catch (IOException e)
                {
                    LOG.warning(e);
                    return false;
                }
            }
            else
            {
                return runChmod(file, "a+w");
            }
        }
        else
        {
            return file.setReadOnly();
        }
    }

    private static boolean runChmod(File file, String arg)
    {
        if (!SystemUtils.IS_WINDOWS)
        {
            Process p = null;
            try
            {
                p = Runtime.getRuntime().exec(new String[]{"chmod", arg, file.getAbsolutePath()});
                int exitCode = p.waitFor();
                return exitCode == 0;
            }
            catch (Exception e)
            {
                // Oh well, we tried
            }
            finally
            {
                if (p != null)
                {
                    p.destroy();
                }
            }
        }

        return false;
    }

    /**
     * Renames a file or directory, optionally deleting any existing
     * destination, and retrying on non-obvious failures (to work around
     * problems on windows).
     *
     * @param src   source file
     * @param dest  detination file
     * @param force if true, delete the destination if it already exists before
     *              renaming
     * @throws java.io.IOException f the rename fails
     */
    public static void rename(File src, File dest, boolean force) throws IOException
    {
        if (dest.exists())
        {
            if (force)
            {
                if (dest.isDirectory())
                {
                    rmdir(dest);
                }
                else
                {
                    robustDelete(dest);
                }
            }
        }

        robustRename(src, dest);
    }

    public static File createTempFile(String prefix, String suffix, String data) throws IOException
    {
        File file = File.createTempFile(prefix, suffix);
        Files.write(data, file, Charset.defaultCharset());
        return file;
    }

    public static void createFile(File file, byte[] data) throws IOException
    {
        FileOutputStream os = null;

        try
        {
            os = new FileOutputStream(file);
            os.write(data);
        }
        finally
        {
            IOUtils.close(os);
        }
    }

    /**
     * Creates an empty temporary file within the given directory, creating the
     * directory if necessary.
     *
     * @param dir the directory to create the file within
     * @return the newly-created File
     * @throws IOException if the directory or file cannot be created
     */
    public static File createTempFile(File dir) throws IOException
    {
        if (!dir.exists() && !dir.mkdirs())
        {
            throw new IOException("Failed to create new directory '" + dir.getCanonicalPath() + "'.");
        }
        return File.createTempFile("tmp", null, dir);
    }

    public static File createTempFile(String prefix, String suffix, byte[] data) throws IOException
    {
        File file = File.createTempFile(prefix, suffix);
        createFile(file, data);
        return file;
    }

    public static boolean createSymlink(File symlink, File destination) throws IOException
    {
        if (LN_AVAILABLE)
        {
            SystemUtils.runCommand("ln", "-s", destination.getAbsolutePath(), symlink.getAbsolutePath());
            return true;
        }

        return false;
    }

    public static File composeFile(String... parts)
    {
        String result = composeFilename(parts);
        return new File(result);
    }

    public static String join(String... parts)
    {
        return FileSystemUtils.composeFilename(parts);
    }

    public static String composeFilename(String... parts)
    {
        return StringUtils.join(File.separator, parts);
    }

    public static String composeFilename(Collection<String> parts)
    {
        return StringUtils.join(File.separator, parts);
    }

    public static String composeSearchPath(String... parts)
    {
        return StringUtils.join(File.pathSeparator, parts);
    }

    /**
     * Returns the absolute path of the given file, canonicalised as best as it
     * can be.  At the very least separators are normalised to their local
     * form, with duplicate and trailing separators removed.
     *
     * @param file file to get the path for
     * @return a best effort of a normalised absolute path to the file
     */
    public static String getNormalisedAbsolutePath(File file)
    {
        String path;
        try
        {
            path = file.getCanonicalPath();
        }
        catch (IOException e)
        {
            // Continue on as best we can.
            path = file.getAbsolutePath();
        }

        return localiseSeparators(path);
    }

    /**
     * Ensures all separator characters are forward slashes, which works on all
     * supported file systems.
     *
     * @param path the path to normalise
     * @return an equivalent path, but with all separators as forward slashes
     */
    public static String normaliseSeparators(String path)
    {
        if (path == null)
        {
            return null;
        }
        return path.replace('\\', NORMAL_SEPARATOR_CHAR);
    }

    /**
     * Converts any separator characters (/ or \) to the local separator
     * character and removes unnecessary duplicate and trailing separators.
     *
     * @param path path to convert
     * @return the path with all separators in local form, with unnecesary
     *         separators removed
     */
    public static String localiseSeparators(String path)
    {
        // Convert all separators to the same format.
        char otherSeparator;
        String separatorRegex;
        if (File.separatorChar == '/')
        {
            otherSeparator = '\\';
            separatorRegex = "/{2,}";
        }
        else
        {
            otherSeparator = '/';
            separatorRegex = "\\\\{2,}";
        }

        path = path.replace(otherSeparator, File.separatorChar);

        // Replace sequences of separators with a single separator.
        path = path.replaceAll(separatorRegex, Matcher.quoteReplacement(File.separator));

        // Strip any trailing separator.
        if (path.length() > 1 && path.endsWith(File.separator))
        {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Converts all line endings to single newline characters.
     *
     * @param s string to normalise
     * @return a string equivalent to s but with all line separators converted to single newline characters
     */
    public static String normaliseNewlines(String s)
    {
        return s.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
    }

    /**
     * This method returns true if the specified file is the root of a file system.
     *
     * @param f file to test
     * @return true iff the given file is a root
     */
    public static boolean isRoot(File f)
    {
        return f.getParentFile() == null;
    }

    /**
     * Returns true iff the given path is set and refers to a directory.
     *
     * @param path the path to test (may be null)
     * @return true iff path is a directory
     */
    public static boolean isDirectory(String path)
    {
        if (StringUtils.stringSet(path))
        {
            File f = new File(path);
            return f.isDirectory();
        }

        return false;
    }

    /**
     * Returns true iff the given path is set and refers to a regular file.
     *
     * @param path the path to test (may be null)
     * @return true iff path is a regular file
     */
    public static boolean isFile(String path)
    {
        if (StringUtils.stringSet(path))
        {
            File f = new File(path);
            return f.isFile();
        }

        return false;
    }

    public static String getMimeType(File file)
    {
        String type = URLConnection.guessContentTypeFromName(file.getName());
        if (type == null)
        {
            FileInputStream fis = null;
            try
            {
                fis = new FileInputStream(file);
                type = URLConnection.guessContentTypeFromStream(fis);
            }
            catch (IOException e)
            {
                // Oh well
            }
            finally
            {
                IOUtils.close(fis);
            }

            if (type == null)
            {
                type = "text/plain";
            }
        }
        return type;
    }

    /**
     * Returns the windows-style file extension for the given filename.  This
     * is considered to by everything after the last period, except when this
     * period is also the first character in the filename (which is the case
     * for Unix-style dotfiles), in which case the extension is considered
     * to be empty.
     *
     * @param filename the filename to retrieve the extension from
     * @return the filenames's extension
     */
    public static String getFilenameExtension(String filename)
    {
        int index = filename.lastIndexOf('.');
        if (index > 0)
        {
            return filename.substring(index + 1);
        }
        else
        {
            return "";
        }
    }

    /**
     * Translates all line endings (CR, CRLF or LF) in the given file to the
     * given bytes.
     *
     * @param file                the file to translate
     * @param eol                 the new line ending as a byte array
     * @param preservePermissions if true, the permissions on the file will
     *                            be preserved
     * @throws IOException if an error occurs
     */
    public static void translateEOLs(File file, byte[] eol, boolean preservePermissions) throws IOException
    {
        File tempFile = null;
        int permissions = -1;

        if (preservePermissions)
        {
            permissions = getPermissions(file);
        }

        try
        {
            tempFile = File.createTempFile(file.getName(), ".tmp", file.getParentFile());

            InputStream in = null;
            OutputStream out = null;

            try
            {
                in = new FileInputStream(file);
                out = new BufferedOutputStream(new FileOutputStream(tempFile));

                byte[] buffer = new byte[1024];
                int n;
                boolean skipNewline = false;

                while ((n = in.read(buffer)) > 0)
                {
                    for (int i = 0; i < n; i++)
                    {
                        byte b = buffer[i];
                        switch (b)
                        {
                            case'\r':
                                out.write(eol);
                                skipNewline = true;
                                break;

                            case'\n':
                                if (skipNewline)
                                {
                                    skipNewline = false;
                                }
                                else
                                {
                                    out.write(eol);
                                }
                                break;

                            default:
                                skipNewline = false;
                                out.write(b);
                                break;
                        }

                    }
                }
            }
            finally
            {
                IOUtils.close(in);
                IOUtils.close(out);
            }

            if (!file.delete() || !tempFile.renameTo(file))
            {
                throw new IOException("Unable to rename temporary file '" + tempFile.getAbsolutePath() + "' to '" + file.getAbsolutePath() + "'");
            }

            if (permissions >= 0)
            {
                setPermissions(file, permissions);
            }

            tempFile = null;
        }
        finally
        {
            if (tempFile != null)
            {
                if (!tempFile.delete())
                {
                    tempFile.deleteOnExit();
                }
            }
        }
    }

    /**
     * Copies source the source file(s) to the destination.  A few modes are
     * supported:
     * <p/>
     * Single source:
     * File -> File: copies a file to a file, overwriting an existing dest
     * File -> Dir : copy file into an existing dest directory, overwriting
     * any existing child file (but not existing child dir!)
     * Dir  -> Dir : recursive copy of directory, overwrites existing dest,
     * (even if it is a file) creates dest if necessary
     * <p/>
     * Multiple sources (must all be files):
     * File(s) -> Dir: copies files into existing dest dir, overwrites
     * existing dest, creates dest if necessary
     *
     * @param dest destination to copy file(s) to
     * @param src  source files to be copied
     * @throws IOException on any error
     */
    public static void copy(File dest, File... src) throws IOException
    {
        if (src.length == 0)
        {
            return;
        }

        if (SystemUtils.getBooleanProperty(PROPERTY_USE_EXTERNAL_COPY, CP_AVAILABLE))
        {
            unixCopy(dest, src);
        }
        else
        {
            javaCopy(dest, src);
        }
    }

    public static void delete(File f) throws IOException
    {
        if (f.exists())
        {
            if (f.isDirectory())
            {
                rmdir(f);
            }
            else
            {
                if (!robustDelete(f))
                {
                    throw new IOException("Cannot remove existing file '" + f.getAbsolutePath() + "'");
                }
            }
        }
    }

    public static void ensureEmptyDirectory(File dir) throws IOException
    {
        delete(dir);
        if (!dir.mkdirs())
        {
            throw new IOException("Unable to create destination directory '" + dir.getAbsolutePath() + "'");
        }
    }

    private static void ensureNoDirectories(File[] files) throws IOException
    {
        for (File f : files)
        {
            if (f.isDirectory())
            {
                throw new IOException("Copy failed: multiple sources including an existing directory '" + f.getAbsolutePath() + "'");
            }
        }
    }

    static void unixCopy(File dest, File... src) throws IOException
    {
        // Use the Unix cp command because it:
        //   - preserves permissions; and
        //   - is likely to be faster when it matters (i.e. large copy)
        String flags = "-pR";
        if (SystemUtils.IS_LINUX)
        {
            flags += "d";
        }

        if (src.length == 1)
        {
            if (src[0].isDirectory())
            {
                // cp handles file->file and file->dir as expected.  Help is
                // required for dir->dir, we need to eliminate an existing dest.
                delete(dest);
            }
            else
            {
                // Avoid link preservation for a single file copy -- it only
                // makes sense when picking up both the link and what it links
                // to.
                flags = "-p";
            }
        }
        else
        {
            ensureNoDirectories(src);
            ensureEmptyDirectory(dest);
        }

        List<String> argsList = new LinkedList<String>();
        argsList.add("cp");
        argsList.add(flags);
        for (File f : src)
        {
            argsList.add(f.getAbsolutePath());
        }
        argsList.add(dest.getAbsolutePath());

        String[] args = argsList.toArray(new String[argsList.size()]);
        Process child = Runtime.getRuntime().exec(args);
        try
        {
            int exit = child.waitFor();
            if (exit != 0)
            {
                // Attempt to copy ourselves.
                LOG.warning("Copy using '" + StringUtils.join(" ", args) + "' failed (" + exit + "), trying internal copy");
                rmdir(dest);
                javaCopy(dest, src);
            }
        }
        catch (InterruptedException e)
        {
            IOException ioe = new IOException("Interrupted while executing '" + StringUtils.join(" ", args) + "'");
            ioe.initCause(e);
            throw ioe;
        }
        finally
        {
            if (child != null)
            {
                child.destroy();
            }
        }
    }

    public static void javaCopy(File dest, File... src) throws IOException
    {
        if (src.length == 1)
        {
            File singleSource = src[0];
            if (singleSource.isFile())
            {
                if (dest.isDirectory())
                {
                    dest = new File(dest, singleSource.getName());
                    if (dest.isDirectory())
                    {
                        throw new IOException("Copy failed: destination directory contains existing directory '" + dest.getAbsolutePath() + "' with same name as source file");
                    }
                }

                delete(dest);
                Files.copy(singleSource, dest);
            }
            else if (singleSource.isDirectory())
            {
                ensureEmptyDirectory(dest);
                recursiveCopy(dest, singleSource);
            }
            else
            {
                throw new IOException(("Copy failed: source '" + singleSource.getAbsolutePath() + "' does not exist"));
            }
        }
        else
        {
            ensureNoDirectories(src);
            ensureEmptyDirectory(dest);

            for (File f : src)
            {
                // copy into dest directory.
                recursiveCopy(new File(dest, f.getName()), f);
            }
        }
    }

    // WARNING: will not handle recursive symlinks
    public static void recursiveCopy(File dest, File src, String... excludeDirs) throws IOException
    {
        if (src.isDirectory())
        {
            if (CollectionUtils.contains(excludeDirs, src.getName()))
            {
                return;
            }

            if (!dest.isDirectory() && !dest.mkdirs())
            {
                throw new IOException(String.format("Copy failed. Failed to create dir %s", dest.getAbsolutePath()));
            }

            for (String file : list(src))
            {
                recursiveCopy(new File(dest, file), new File(src, file), excludeDirs);
            }
        }
        else
        {
            if (dest.isFile())
            {
                // trouble..
                throw new IOException(String.format("Copy failed. Failed to copy to file %s, it already exists.", dest.getAbsolutePath()));
            }
            if (!dest.createNewFile())
            {
                throw new IOException(String.format("Copy failed. Failed to create file %s", dest.getAbsolutePath()));
            }
            Files.copy(src, dest);
        }
    }

    /**
     * Joins a path to a base, canonicalising any separators and occurrences of
     * '.' or '..'.  The base path, if given, should already be an a canonical
     * form.  If the path is absolute (begins with a separator) it is returned
     * unchanged.
     *
     * @param basePath base to append to, should be in canonical form already
     * @param path     path to append - may include current '.' and parent '..'
     *                 directory references
     * @return the appended path, or null if appending results in no more path
     *         remaining (happens when a parent reference '..' takes the result
     *         to or above the root)
     */
    public static String appendAndCanonicalise(String basePath, String path)
    {
        path = nullSafeNormalise(path);
        if (path != null && path.startsWith(NORMAL_SEPARATOR))
        {
            return path;
        }
        else
        {
            String result = nullSafeNormalise(basePath);
            if (path != null)
            {
                for (String element: StringUtils.split(path, NORMAL_SEPARATOR_CHAR))
                {
                    if (!element.equals(THIS_DIRECTORY))
                    {
                        if (element.equals(PARENT_DIRECTORY))
                        {
                            result = up(result);
                        }
                        else
                        {
                            if (result == null)
                            {
                                result = element;
                            }
                            else
                            {
                                result = StringUtils.join(NORMAL_SEPARATOR, true, true, result, element);
                            }
                        }
                    }
                }
            }

            return result;
        }
    }

    private static String up(String result)
    {
        if (result != null)
        {
            int i = result.lastIndexOf(NORMAL_SEPARATOR_CHAR);
            if (i != -1)
            {
                result = result.substring(0, i);
            }
            else
            {
                result = null;
            }
        }
        
        return result;
    }

    private static String nullSafeNormalise(String path)
    {
        return path == null ? null : normaliseSeparators(path);
    }

    /**
     * Percent-encodes a single component of a filename ensuring that it is
     * safe to create on all file systems.  Slashes are also encoded, so they
     * will not be treated as separators.
     *
     * @param component the string to encode
     * @return the given component with special characters, percent encoded
     *         to avoid file system restrictions
     */
    public static String encodeFilenameComponent(String component)
    {
        return WebUtils.percentEncode(component, new Predicate<Character>()
        {
            public boolean apply(Character ch)
            {
                if (Character.isLetterOrDigit(ch))
                {
                    return true;
                }
                else
                {
                    switch (ch)
                    {
                        case '-':
                        case '_':
                        case '.':
                            return true;
                        default:
                            return false;
                    }
                }
            }
        });
    }

    /**
     * A recursive search through the specified directory, returning all of the files
     * that match the predicate.
     *
     * @param base          the base directory of the search
     * @param predicate     the predicate that defined which files are matched.
     * @return  a list of all the matched files.
     */
    public static List<File> filter(File base, Predicate<File> predicate)
    {
        List<File> satisfiedFiles = new LinkedList<File>();
        for (File f : listFiles(base))
        {
            filter(f, predicate, satisfiedFiles);
        }
        return satisfiedFiles;
    }

    private static void filter(File base, Predicate<File> predicate, List<File> satisfiedFiles)
    {
        if (predicate.apply(base))
        {
            satisfiedFiles.add(base);
        }
        if (base.isDirectory())
        {
            for (File f : listFiles(base))
            {
                filter(f, predicate, satisfiedFiles);
            }
        }
    }

    private static List<File> listFiles(File dir)
    {
        List<File> result = new LinkedList<File>();
        if (dir.isDirectory())
        {
            File[] listing = dir.listFiles();
            if (listing != null)
            {
                result.addAll(Arrays.asList(listing));
            }
        }
        return result;
    }

    /**
     * Return an array of the specified directory.  If the directory is empty,
     * an empty array is returned.
     *
     * @param dir   the directory being listed.
     * @return  an array of strings naming the files and directories within the
     * specified directory, or an empty array if non exist.
     *
     * @see java.io.File#list()
     */
    public static String[] list(File dir)
    {
        String[] listing = dir.list();
        if (listing != null)
        {
            return listing;
        }
        return new String[0];
    }

    public static File findFirstChildMatching(File dir, final String regex)
    {
        File[] matchingFiles = dir.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.matches(regex);
            }
        });
        
        if (matchingFiles == null || matchingFiles.length == 0)
        {
            throw new RuntimeException("No file matching '" + regex + "' in '" + dir.getAbsolutePath() + "'");
        }
        
        return matchingFiles[0];
    }

    public static long getFreeDiskSpace(File file)
    {
        long freeSpace = 0;
        while (file != null && freeSpace == 0)
        {
            freeSpace = file.getFreeSpace();
            file = file.getParentFile();
        }

        return freeSpace;
    }
}
