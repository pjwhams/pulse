package com.zutubi.util;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Random;

/**
 */
public class RandomUtils
{
    private static final Random RAND = new Random(System.currentTimeMillis());

    private static final char[] ALPHA_NUMERIC_CHARACTERS = new char[]{'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
            'W', 'X', 'Y', 'Z', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'};

    public static String randomString(int length)
    {
        return randomString(RAND, length);
    }

    public static String secureRandomString(int length) throws GeneralSecurityException
    {
        return randomString(SecureRandom.getInstance("SHA1PRNG"), length);
    }

    /**
     * Version of the randomString function that allows you to provide the random instance.
     *
     * @param rand instance of the java.util.Random used to randomly select the characters.
     * @param length of the string to be created.
     * @return a pseudo random string of specified length generated using the provided Random instance.  The
     * randomness of the generated string will be limited by the randomness of the numbers generated by Random.nextInt()
     */
    public static String randomString(Random rand, int length)
    {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < length; i++)
        {
            buffer.append(ALPHA_NUMERIC_CHARACTERS[rand.nextInt(ALPHA_NUMERIC_CHARACTERS.length)]);
        }
        return buffer.toString();
    }

    /**
     * Generate a random token of the specified length.  If possible, this token will use a
     * secure algorithm to generate the token, otherwise just the random number generated.
     * This method is most useful for those processes for which security is not critical.
     *
     * @param length    of the generated token
     * @return the generated token string.
     */
    public static String randomToken(int length)
    {
        try
        {
            return secureRandomString(length);
        }
        catch (GeneralSecurityException e)
        {
            return randomString(length);
        }
    }
}
