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

package com.zutubi.util;

import com.google.common.base.Charsets;
import com.zutubi.util.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Miscellaneous security-related utility functions.
 */
public class SecurityUtils
{
    private static final int BUFFER_SIZE = 16384;

    public static final String ALGORITHM_MD5 = "MD5";
    public static final String ALGORITHM_SHA1 = "SHA-1";
    
    /**
     * Returns the specified type of digest of the given input as a hexadecimal
     * string.
     *
     * @param algorithm the name of the digest algorithm (see the ALGORITHM_*
     *                  constants, and {@link java.security.MessageDigest})
     * @param input     the input to digest
     * @return the digest as a hexadecimal string
     *
     * @throws NoSuchAlgorithmException if the given algorithm is unrecognised
     *
     * @see #digestUnsafe(String, byte...)
     */
    public static String digest(String algorithm, byte... input) throws NoSuchAlgorithmException
    {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] bytes = digest.digest(input);
        return StringUtils.toHexString(bytes);
    }

    /**
     * Returns the specified type of digest of the given input as a hexadecimal
     * string.  Errors are thrown as unchecked exceptions.
     *
     * @param algorithm the name of the digest algorithm (see the ALGORITHM_*
     *                  constants, and {@link java.security.MessageDigest})
     * @param input     the input to digest
     * @return the digest as a hexadecimal string
     *
     * @throws RuntimeException on any error
     *
     * @see #digest(String, byte...)
     */
    public static String digestUnsafe(String algorithm, byte... input)
    {
        try
        {
            return digest(algorithm, input);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the specified type of digest of the given input string,
     * converted to bytes using UTF-8, as a hexadecimal string.
     *
     * @param algorithm the name of the digest algorithm (see the ALGORITHM_*
     *                  constants, and {@link java.security.MessageDigest})
     * @param input     the input string to digest
     * @return the digest as a hexadecimal string
     *
     * @throws NoSuchAlgorithmException if the given algorithm is unrecognised
     *
     * @see #digestUnsafe(String, String)
     */
    public static String digest(String algorithm, String input) throws NoSuchAlgorithmException
    {
        return digest(algorithm, input.getBytes(Charsets.UTF_8));
    }

    /**
     * Returns the specified type of digest of the given input string,
     * converted to bytes using UTF-8, as a hexadecimal string.  Errors are
     * throw as unchecked exceptions.
     *
     * @param algorithm the name of the digest algorithm (see the ALGORITHM_*
     *                  constants, and {@link java.security.MessageDigest})
     * @param input     the input string to digest
     * @return the digest as a hexadecimal string
     *
     * @throws RuntimeException on any error
     *
     * @see #digest(String, String)
     */
    public static String digestUnsafe(String algorithm, String input)
    {
        try
        {
            return digest(algorithm, input);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an MD5 digest of the given input as a hexadecimal string.
     *
     * @param input the input to digest
     * @return the digest as a hexadecimal string
     *
     * @throws RuntimeException if MD5 digests are not supported
     */
    public static String md5Digest(byte... input)
    {
        return digestUnsafe(ALGORITHM_MD5, input);
    }

    /**
     * Returns an MD5 digest of the given input, converted to bytes using
     * UTF-8, as a hexadecimal string.
     *
     * @param input the input string to digest
     * @return the digest as a hexadecimal string
     *
     * @throws RuntimeException if MD5 digests or UTF-8 encoding are not
     *         supported
     */
    public static String md5Digest(String input)
    {
        return digestUnsafe(ALGORITHM_MD5, input);
    }

    /**
     * Returns a SHA-1 digest of the given input as a hexadecimal string.
     *
     * @param input the input to digest
     * @return the digest as a hexadecimal string
     *
     * @throws RuntimeException if SHA-1 digests are not supported
     */
    public static String sha1Digest(byte... input)
    {
        return digestUnsafe(ALGORITHM_SHA1, input);
    }

    /**
     * Returns an SHA-1 digest of the given input, converted to bytes using
     * UTF-8, as a hexadecimal string.
     *
     * @param input the input string to digest
     * @return the digest as a hexadecimal string
     *
     * @throws RuntimeException if SHA-1 digests or UTF-8 encoding are not
     *         supported 
     */
    public static String sha1Digest(String input)
    {
        return digestUnsafe(ALGORITHM_SHA1, input);
    }

    /**
     * Returns the specified type of digest of the content of the given input
     * file as a hexadecimal string.
     *
     * @param algorithm the name of the digest algorithm (see the ALGORITHM_*
     *                  constants, and {@link java.security.MessageDigest})
     * @param file      the input file to digest
     * @return the digest as a hexadecimal string
     *
     * @throws NoSuchAlgorithmException if the given algorithm is unrecognised
     * @throws java.io.IOException      if there is an error reading the file
     */
    public static String digest(String algorithm, File file) throws NoSuchAlgorithmException, IOException
    {
        InputStream is = null;
        try
        {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            is = new FileInputStream(file);
            byte buffer[] = new byte[BUFFER_SIZE];

            int n;
            while ((n = is.read(buffer)) >= 0)
            {
                digest.update(buffer, 0, n);
            }

            return StringUtils.toHexString(digest.digest());
        }
        finally
        {
            IOUtils.close(is);
        }
    }
}
