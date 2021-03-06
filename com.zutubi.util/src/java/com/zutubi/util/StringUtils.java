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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;

/**
 * Useful static methods for working with Strings.
 */
public class StringUtils
{
    /**
     * Returns true if the given string is not null and not empty.
     *
     * @param str string to test
     * @return true if str is not null and has non-zero length
     *
     * @see #trimmedStringSet
     */
    public static boolean stringSet(String str)
    {
        return str != null && str.length() > 0;
    }

    /**
     * Returns true if the given string is not null and contains at least one
     * non-whitespace character.
     *
     * @param str string to test
     * @return true if str is not null and has one or more non-whitespace characters
     *
     * @see #stringSet
     */
    public static boolean trimmedStringSet(String str)
    {
        return str != null && str.trim().length() > 0;
    }

    /**
     * Equivalent to calling {@link #trimmedString(CharSequence, int, String)} with
     * the given string and length and a trim message of "...".
     *
     * @param s      the string to trim
     * @param length the maximum length of the returned string
     * @return the given string, trimmed if necessary
     * @throws IllegalArgumentException if length is negative
     */
    public static String trimmedString(CharSequence s, int length)
    {
        return trimmedString(s, length, "...");
    }

    /**
     * Returns the given string, trimmed if necessary to the given maximum
     * length.  Upon trimming, the returned string will end with the given
     * trim message and be exactly length characters long including this
     * message.  If the trim message cannot fit within length, it will, be
     * truncated after length characters.
     *
     * @param s           the string to trim
     * @param length      the maximum length of the returned string
     * @param trimMessage message to append to a trimmed string (can be
     *                    empty)
     * @return the given string, trimmed if necessary
     * @throws IllegalArgumentException if length is negative
     */
    public static String trimmedString(CharSequence s, int length, String trimMessage)
    {
        if(length < 0)
        {
            throw new IllegalArgumentException("Length must be non-negative (got " + length + ")");
        }

        if (s.length() > length)
        {
            int trimMessageLength = trimMessage.length();
            if (length >= trimMessageLength)
            {
                return s.subSequence(0, length - trimMessageLength) + trimMessage;
            }
            else
            {
                return trimMessage.substring(0, length);
            }
        }

        return s.toString();
    }

    public static String wrapString(String s, int lineLength, String prefix)
    {
        return wrapString(s, lineLength, prefix, true);
    }

    /**
     * Returns a version of the given string wrapped if necessary to ensure
     * it does not exceed the line length satisfied.  The string will be
     * wrapped at whitespace if possible but if not will be broken whereever
     * necessary to ensure the line length is not exceeded.  Wrapping is done
     * by replacing a space with a newline, or inserting a newline (when no
     * space is found to replace).  Existing newlines are ignored.
     *
     * @param s          the string to wrap
     * @param lineLength the maximum length of lines returned in the result
     * @param prefix     An optional prefix to add after each inserted newline
     *                   character.  The returned lines will still not exceed
     *                   lineLength.  Thus the prefix length must be less than
     *                   lineLength - 1.  If null is passed, no prefix is used.
     * @param splitWord  if set to true, this method will introduce a newline
     *                   in the middle of a word to ensure the the lineLength is
     *                   not exceeded. If false, this method will search for the
     *                   nearest appropriate whitespace at which to split.
     * @return a version of the given string with newlines inserted to
     *         wrap lines at the given length
     */
    public static String wrapString(String s, int lineLength, String prefix, boolean splitWord)
    {
        if (prefix != null && prefix.length() >= lineLength - 1)
        {
            throw new IllegalArgumentException("prefix length must be less than line length -1");
        }

        // Short circuit a common case
        if (s.length() < lineLength)
        {
            return s;
        }

        int length = s.length();
        int effectiveLineLength = lineLength;
        StringBuilder result = new StringBuilder(length + length * 2 / lineLength);

        for (int i = 0; i < length;)
        {
            if (length - i <= effectiveLineLength)
            {
                // Last bit
                result.append(s.substring(i));
                break;
            }

            // Check for existing newlines in this span
            int j;
            boolean alreadySplit = false;
            for (j = i + effectiveLineLength; j >= i; j--)
            {
                if (s.charAt(j) == '\n')
                {
                    // Already split at this point, continue from the split
                    alreadySplit = true;
                    result.append(s.substring(i, j + 1));
                    if (prefix != null)
                    {
                        result.append(prefix);
                    }
                    i = j + 1;
                    break;
                }
            }

            if (!alreadySplit)
            {
                // Need to find a place to trim, starting at i + effectiveLineLength
                int candidate = i + effectiveLineLength;
                for (j = candidate; j > i; j--)
                {
                    if (s.charAt(j) == ' ')
                    {
                        // OK, found a spot to split
                        result.append(s.substring(i, j));
                        result.append('\n');
                        if (prefix != null)
                        {
                            result.append(prefix);
                        }

                        i = j + 1;
                        break;
                    }
                }

                if (j == i)
                {
                    // No space found.
                    if (splitWord)
                    {
                        result.append(s.substring(i, candidate));
                        result.append('\n');
                        if (prefix != null)
                        {
                            result.append(prefix);
                        }
                        i = candidate;
                    }
                    else
                    {
                        // find the next space and split on it.
                        for (int k = candidate; k < s.length(); k++)
                        {
                            if (Character.isWhitespace(s.charAt(k)))
                            {
                                // good point to split.
                                result.append(s.substring(i, k));
                                result.append('\n');
                                if (s.charAt(k) == '\n')
                                {
                                    // dont need a second new line.
                                    k = k + 1;
                                }

                                if (prefix != null)
                                {
                                    result.append(prefix);
                                }

                                i = k;
                                break;
                            }
                        }
                        if (j == i)
                        {
                            // no whitespace located.
                            result.append(s.substring(i));
                            break;
                        }
                    }
                }
            }

            if (prefix != null)
            {
                effectiveLineLength = lineLength - prefix.length();
            }
        }

        return result.toString();
    }

    /**
     * Returns the line'th line in the given string, where lines are
     * separated by any one of \r, \n or \r\n.
     *
     * @param s    the string to extract the line from
     * @param line the one-based number of the line to extract
     * @return the given line, or null if there are not that many lines
     */
    public static String getLine(String s, int line)
    {
        String[] lines = s.split("\r\n|\n|\r");
        if (lines.length >= line)
        {
            return lines[line - 1];
        }
        else
        {
            return null;
        }
    }

    public static int getLineOffset(String s, int line)
    {
        s = s.replace("\r\n", "\n");
        s = s.replace("\r", "\n");

        int currentLine = 1;
        for (int i = 0; i < s.length(); i++)
        {
            if (currentLine == line)
            {
                return i;
            }

            if (s.charAt(i) == '\r')
            {
                currentLine++;
                if (i + 1 < s.length() && s.charAt(i + 1) == '\n')
                {
                    i++;
                }
            }
            else if (s.charAt(i) == '\n')
            {
                currentLine++;
            }
        }

        return -1;
    }

    public static String[] split(String s, char c)
    {
        return split(s, c, false);
    }

    /**
     * Splits the given string around occurences of the given character.  The
     * behaviour is the same as {@link String#split}, but given the simpler
     * semantics (no regex, no limit) this implementation is faster.
     *
     * @param s         string to split
     * @param c         character to split on
     * @param skipEmpty if true, no empty strings are allowed in the result
     * @return the split pieces of the string
     */
    public static String[] split(String s, char c, boolean skipEmpty)
    {
        int length = s.length();
        while(length > 0 && s.charAt(length - 1) == c)
        {
            length--;
        }

        // String.split has unusual semantics: it trims trailing empty
        // strings from the result; with the exception of the case where the
        // input String is empty.  A special case here makes the usual code
        // path simpler.
        if(length == 0)
        {
            if(s.length() == 0 && !skipEmpty)
            {
                return new String[]{""};
            }
            else
            {
                return new String[]{};
            }
        }

        List<Integer> indices = new LinkedList<Integer>();
        int startIndex = 0;
        int emptyCount = 0;
        while(startIndex < length)
        {
            int nextIndex = s.indexOf(c, startIndex);
            if(nextIndex < 0)
            {
                nextIndex = length;
            }

            if(nextIndex == startIndex)
            {
                emptyCount++;
            }

            indices.add(nextIndex);
            startIndex = nextIndex + 1;
        }

        String[] result;
        if (skipEmpty)
        {
            result = new String[indices.size() - emptyCount];
            startIndex = 0;
            int i = 0;
            for(int index: indices)
            {
                if(index > startIndex)
                {
                    result[i++] = s.substring(startIndex, index);
                }
                startIndex = index + 1;
            }
        }
        else
        {
            result = new String[indices.size()];
            startIndex = 0;
            int i = 0;
            for(int index: indices)
            {
                result[i++] = s.substring(startIndex, index);
                startIndex = index + 1;
            }
        }

        return result;
    }

    /**
     * Splits the given string at spaces, allowing use of quoting to override
     * spaces (i.e. foo bar is split into [foo, bar] but "foo bar" gives
     * [foo bar]).  Backslashes may be used to escape quotes or spaces.
     *
     * @param s the string to split
     * @return a list containing the split parts of the string
     * @throws IllegalArgumentException if the string is poorly formatted
     */
    public static List<String> split(String s)
    {
        List<String> result = new LinkedList<String>();
        boolean inQuotes = false;
        boolean escaped = false;
        boolean haveData = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (escaped)
            {
                haveData = true;
                current.append(c);
                escaped = false;
            }
            else
            {
                switch (c)
                {
                    case'\\':
                    {
                        escaped = true;
                        break;
                    }
                    case' ':
                    {
                        if (inQuotes)
                        {
                            current.append(c);
                        }
                        else if (haveData)
                        {
                            result.add(current.toString());
                            current.delete(0, current.length());
                            haveData = false;
                        }

                        break;
                    }
                    case'"':
                    {
                        if (inQuotes)
                        {
                            inQuotes = false;
                        }
                        else
                        {
                            inQuotes = true;
                            // We always have data if we see quotes, which
                            // allows expression of the empty string as ""
                            haveData = true;
                        }

                        break;
                    }
                    default:
                    {
                        current.append(c);
                        haveData = true;
                    }
                }
            }
        }

        if (escaped)
        {
            throw new IllegalArgumentException("Unexpected end of input after backslash (\\)");
        }
        if (inQuotes)
        {
            throw new IllegalArgumentException("Unexpected end of input looking for end of quote (\")");
        }

        if (haveData)
        {
            result.add(current.toString());
        }

        return result;
    }

    /**
     * The inverse of split, which is *not* the same as joining.  Returns a
     * string that if passed to split would return the given list.  This
     * involves quoting any piece that contains a space or is empty, and
     * escaping any quote characters or backslashes.
     *
     * @param pieces pieces of string to unsplit
     * @return the inverse of split, as applied to pieces
     * @see StringUtils#split(String)
     */
    public static String unsplit(List<String> pieces)
    {
        StringBuilder result = new StringBuilder();
        StringBuilder current = new StringBuilder();
        boolean first = true;

        for (String piece : pieces)
        {
            boolean quote = piece.length() == 0;
            current.delete(0, current.length());

            for (int i = 0; i < piece.length(); i++)
            {
                char c = piece.charAt(i);
                switch (c)
                {
                    case '\\':
                    case '\"':
                        current.append('\\');
                        break;
                    case ' ':
                        quote = true;
                        break;
                }

                current.append(c);
            }

            if (first)
            {
                first = false;
            }
            else
            {
                result.append(' ');
            }

            if (quote)
            {
                result.append('\"');
            }

            result.append(current);

            if (quote)
            {
                result.append('\"');
            }
        }

        return result.toString();
    }

    public static String join(String glue, String... pieces)
    {
        return join(glue, false, false, pieces);
    }

    public static String join(String glue, boolean glueCheck, String... pieces)
    {
        return join(glue, glueCheck, false, pieces);
    }

    /**
     * Joins the given string pieces together with the glue in all the
     * joins.  The result is <piece1><glue><piece2>...<glue><pieceN>.
     *
     * @param glue      glue string to insert at all the join points
     * @param glueCheck if true, both sides of the join will be stripped of
     *                  any pre-existing glue (i.e. if the first piece ends
     *                  with glue it will be stripped, and if the second
     *                  starts with glue it will be stripped), ensuring
     *                  no duplication of glue at join points
     * @param skipEmpty if true, empty pieces are ignored
     * @param pieces    the pieces of string to join together
     * @return a string made up of the given pieces, joined with the glue
     */
    public static String join(String glue, boolean glueCheck, boolean skipEmpty, String... pieces)
    {
        StringBuilder result = new StringBuilder();

        // If skipping empty, move length backwards to ignore empty pieces at
        // the end (helps us know when we are on the last actual piece).
        int length = pieces.length;
        if(skipEmpty)
        {
            while(length > 0 && pieces[length - 1].length() == 0)
            {
                length--;
            }
        }

        boolean first = true;
        for (int i = 0; i < length; i++)
        {
            String piece = pieces[i];
            if(skipEmpty && piece.length() == 0)
            {
                continue;
            }

            if (glueCheck)
            {
                if (!first && piece.startsWith(glue))
                {
                    piece = piece.substring(glue.length());
                }

                if (i < length - 1 && piece.endsWith(glue))
                {
                    piece = piece.substring(0, piece.length() - glue.length());
                }
            }

            if (first)
            {
                first = false;
            }
            else
            {
                result.append(glue);
            }

            result.append(piece);
        }

        return result.toString();
    }

    /**
     * Equivalent to {@link #join(String, String...)}, converts the given
     * collection to an array to make the call.
     *
     * @param glue  glue used to join each part
     * @param parts parts to join
     * @return the joined string
     *
     * @see #join(String, String...)
     */
    public static String join(String glue, Iterable<String> parts)
    {
        return join(glue, Iterables.toArray(parts, String.class));
    }

    public static String join(char glue, boolean glueCheck, boolean skipEmpty, String... pieces)
    {
        StringBuilder result = new StringBuilder();

        // If skipping empty, move length backwards to ignore empty pieces at
        // the end (helps us know when we are on the last actual piece).
        int length = pieces.length;
        if (skipEmpty)
        {
            while (length > 0 && !stringSet(pieces[length - 1]))
            {
                length--;
            }
        }

        boolean first = true;
        for (int i = 0; i < length; i++)
        {
            String piece = pieces[i];
            if (skipEmpty && !stringSet(piece))
            {
                continue;
            }

            if (glueCheck)
            {
                if (!first && piece.charAt(0) == glue)
                {
                    piece = piece.substring(1);
                }

                if (i < length - 1 && piece.charAt(piece.length() - 1) == glue)
                {
                    piece = piece.substring(0, piece.length() - 1);
                }
            }

            if (first)
            {
                first = false;
            }
            else
            {
                result.append(glue);
            }

            result.append(piece);
        }

        return result.toString();
    }

    public static String pluralise(String singularNoun)
    {
        String pluralNoun = singularNoun;

        int nounLength = pluralNoun.length();

        if (nounLength == 1)
        {
            pluralNoun = pluralNoun + 's';
        }
        else if (nounLength > 1)
        {
            char secondToLastChar = pluralNoun.charAt(nounLength - 2);

            if (pluralNoun.endsWith("y"))
            {
                switch (secondToLastChar)
                {
                    case'a': // fall-through
                    case'e': // fall-through
                    case'i': // fall-through
                    case'o': // fall-through
                    case'u':
                        pluralNoun = pluralNoun + 's';
                        break;
                    default:
                        pluralNoun = pluralNoun.substring(0, nounLength - 1)
                                + "ies";
                }
            }
            else if (pluralNoun.endsWith("s"))
            {
                switch (secondToLastChar)
                {
                    case's':
                        pluralNoun = pluralNoun + "es";
                        break;
                    default:
                        pluralNoun = pluralNoun + "ses";
                }
            }
            else
            {
                pluralNoun = pluralNoun + 's';
            }
        }
        return pluralNoun;
    }

    /**
     * Splits the given string around the first occurence of the given
     * separator.
     *
     * @param s         the string to split
     * @param seperator separator character that delimits tokens
     * @param skipEmpty if true, empty tokens are skipped over
     * @return a pair of strings: the next token and the remaining string, or
     *         null if no more tokens are found 
     */
    public static String[] getNextToken(String s, char seperator, boolean skipEmpty)
    {
        if(s.length() == 0)
        {
            return null;
        }
        
        int index = s.indexOf(seperator);
        String token;
        String remainder;
        if(index < 0)
        {
            token = s;
            remainder = "";
        }
        else
        {
            token = s.substring(0, index);
            remainder = s.substring(index + 1);
        }

        if(token.length() == 0 && skipEmpty)
        {
            return getNextToken(s.substring(1), seperator, skipEmpty);
        }
        else
        {
            return new String[]{token, remainder};
        }
    }

    /**
     * @param c character to test
     * @return true iff c is an ascii letter or digit (a-z, A-Z or 0-9).
     */
    public static boolean isAsciiAlphaNumeric(char c)
    {
        return isAsciiAlphabetical(c) || isAsciiDigit(c);
    }

    /**
     * @param c character to test
     * @return true iff c is an ascii letter (a-z or A-Z).
     */
    public static boolean isAsciiAlphabetical(char c)
    {
        return isAsciiLowerCase(c) || isAsciiUpperCase(c);
    }

    /**
     * @param c character to test
     * @return true iff c is an upper case ascii letter (a-z).
     */
    public static boolean isAsciiUpperCase(char c)
    {
        return c >= 'A' && c <= 'Z';
    }

    /**
     * @param c character to test
     * @return true iff c is a lower case ascii letter (a-z).
     */
    public static boolean isAsciiLowerCase(char c)
    {
        return c >= 'a' && c <= 'z';
    }

    /**
     * @param c character to test
     * @return true iff c is an ascii digit (0-9).
     */
    public static boolean isAsciiDigit(char c)
    {
        return c >= '0' && c <= '9';
    }

    public static String stripLineBreaks(String s)
    {
        return s.replaceAll("\r|\n", "");
    }

    /**
     * Returns the given string with the given prefix stripped iff the string
     * begins with the prefix.  Otherwise, the string is returned unchanged.
     *
     * @param s      the string to strip
     * @param prefix the candidate prefix to remove if present
     * @return the given string with the given prefix removed
     */
    public static String stripPrefix(String s, String prefix)
    {
        if (s != null && s.startsWith(prefix))
        {
            return s.substring(prefix.length());
        }
        else
        {
            return s;
        }
    }

    /**
     * Returns the given string with the given suffix stripped iff the string
     * ends with the suffix.  Otherwise, the string is returned unchanged.
     *
     * @param s      the string to strip
     * @param suffix the candidate suffix to remove if present
     * @return the given string with the given suffix removed
     */
    public static String stripSuffix(String s, String suffix)
    {
        if (s != null && s.endsWith(suffix))
        {
            return s.substring(0, s.length() - suffix.length());
        }
        else
        {
            return s;
        }
    }

    /**
     * Counts the number of occurrences of a given character in a given string.
     *
     * @param s the string to inspect
     * @param c the character to count occurrences of
     * @return the number of times {@code c} appears in {@code s}
     */
    public static int count(String s, char c)
    {
        int count = 0;
        for (int i = 0; i < s.length(); i++)
        {
            if (s.charAt(i) == c)
            {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns a copy of the given string with the first letter capitalised
     * and the rest lower cased using the default locale.  If the string is
     * null or empty it is returned unchanged.
     *
     * @param s the string to capitalise
     * @return a captilalised version of the input string
     */
    public static String capitalise(String s)
    {
        if (stringSet(s))
        {
            return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
        }
        else
        {
            return s;
        }
    }

    /**
     * Converts a byte array to a hexadecimal string.  Each single byte is
     * converted to a two character hexadecimal string.
     *
     * @param bytes the input bytes
     * @return the hexadecimal string form of the input bytes
     */
    public static String toHexString(byte[] bytes)
    {
        Formatter formatter = new Formatter();
        for (byte b: bytes)
        {
            formatter.format("%02x", b);
        }

        return formatter.toString();
    }

    /**
     * Returns the given object converted to a string, handling null objects
     * and errors so that this conversion will never fail.  Suitable for use
     * in error handling blocks where further errors need to be prevented.
     *
     * @param o the object to convert
     * @return the output of o.toString(), or a place holder string
     */
    public static String safeToString(Object o)
    {
        if (o == null)
        {
            return "<null>";
        }

        try
        {
            return o.toString();
        }
        catch (Exception e)
        {
            return "<error calling toString>";
        }
    }

    /**
     * @return a function that takes a string and returns the trimmed equivalent
     * 
     * @see String#trim()
     */
    public static Function<String, String> trim()
    {
        return Trim.INSTANCE;
    }

    private enum Trim implements Function<String, String>
    {
        INSTANCE;

        public String apply(String input)
        {
            return input.trim();
        }


        @Override
        public String toString()
        {
            return "trim";
        }
    }
}
