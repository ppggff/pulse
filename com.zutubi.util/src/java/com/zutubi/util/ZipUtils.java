package com.zutubi.util;

import com.zutubi.util.io.IOUtils;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.zutubi.util.io.IOUtils.joinStreams;

/**
 * Basic utilities for zip files.
 */
public class ZipUtils
{
    /**
     * Extracts the files from the given zip stream to into the given
     * destination directory.
     *
     * @param zin zip stream to extract files from
     * @param dir destination directory
     * @throws IOException on error
     */
    public static void extractZip(ZipInputStream zin, File dir) throws IOException
    {
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null)
        {
            File file = new File(dir, entry.getName());

            if (entry.isDirectory())
            {
                if (!file.isDirectory())
                {
                    FileSystemUtils.createDirectory(file);
                }
            }
            else
            {
                // Ensure that the file's parents already exist.
                if (!file.getParentFile().isDirectory())
                {
                    FileSystemUtils.createDirectory(file.getParentFile());
                }

                unzip(zin, file);
            }

            file.setLastModified(entry.getTime());
        }
    }

    private static void unzip(InputStream zin, File file) throws IOException
    {
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream(file);
            joinStreams(zin, out);
        }
        finally
        {
            IOUtils.close(out);
        }
    }

    /**
     * Compresses a single file using gzip.
     *
     * @param in  file to compress
     * @param out file to store the compressed output in
     * @throws IOException on an error reading the input or writing the output
     *
     * @see #uncompressFile(java.io.File, java.io.File)
     */
    public static void compressFile(File in, File out) throws IOException
    {
        InputStream is = null;
        OutputStream os = null;
        try
        {
            is = new FileInputStream(in);
            os = new GZIPOutputStream(new FileOutputStream(out));
            IOUtils.joinStreams(is, os);
        }
        finally
        {
            IOUtils.close(is);
            IOUtils.close(os);
        }
    }

    /**
     * Uncompresses a single file using gzip.
     *
     * @param in  file to uncompress
     * @param out file to store the uncompressed output in
     * @throws IOException on an error reading the input or writing the output
     *
     * @see #compressFile(java.io.File, java.io.File)
     */
    public static void uncompressFile(File in, File out) throws IOException
    {
        InputStream is = null;
        OutputStream os = null;
        try
        {
            is = new GZIPInputStream(new FileInputStream(in));
            os = new FileOutputStream(out);
            IOUtils.joinStreams(is, os);
        }
        finally
        {
            IOUtils.close(is);
            IOUtils.close(os);
        }
    }
}