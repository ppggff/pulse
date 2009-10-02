package com.zutubi.tove.variables;

import com.zutubi.tove.variables.api.ResolutionException;
import com.zutubi.tove.variables.api.VariableMap;
import com.zutubi.util.junit.ZutubiTestCase;

import static java.util.Arrays.asList;
import java.util.List;

public class VariableResolverTest extends ZutubiTestCase
{
    private VariableMap scope = null;

    public void setUp()
    {
        scope = new HashVariableMap();
        scope.add(new GenericVariable<String>("foo", "foo"));
        scope.add(new GenericVariable<String>("bar", "baz"));
        scope.add(new GenericVariable<String>("a\\b", "slashed"));
        scope.add(new GenericVariable<String>("empty", ""));
        scope.add(new GenericVariable<String>("a{b}c", "braced"));
        scope.add(new GenericVariable<String>("a(b)c", "parened"));
        scope.add(new GenericVariable<String>("invalid.name", " this/is\\a$badname  "));
    }

    private void errorTest(String input, String expectedError)
    {
        String result;

        try
        {
            result = VariableResolver.resolveVariables(input, scope);
            fail("Expected config exception, got '" + result + "'");
        }
        catch (ResolutionException e)
        {
            assertEquals(expectedError, e.getMessage());
        }
    }

    private void successTest(String in, String out) throws Exception
    {
        String result = VariableResolver.resolveVariables(in, scope);
        assertEquals(out, result);
    }

    private void successSplitTest(String in, String... out) throws Exception
    {
        List<String> result = VariableResolver.splitAndResolveVariable(in, scope, VariableResolver.ResolutionStrategy.RESOLVE_STRICT);
        assertEquals(asList(out), result);
    }

    private void errorSplitTest(String input, String expectedError)
    {
        List<String> result;
        try
        {
            result = VariableResolver.splitAndResolveVariable(input, scope, VariableResolver.ResolutionStrategy.RESOLVE_STRICT);
            fail("Expected config exception, got '" + result + "'");
        }
        catch (ResolutionException e)
        {
            assertEquals(expectedError, e.getMessage());
        }
    }

    public void testDollarEOI()
    {
        errorTest("ladida $", "Syntax error: unexpected end of input looking for '{' or '('");
    }

    public void testDollarBraceEOI()
    {
        errorTest("ladida ${", "Syntax error: unexpected end of input looking for '}'");
    }

    public void testDollarParenEOI()
    {
        errorTest("ladida $(", "Syntax error: unexpected end of input looking for ')'");
    }

    public void testDollarNoBraceOrParen()
    {
        errorTest("$e", "Syntax error: expecting '{' or '(', got 'e'");
    }

    public void testHalfEscape()
    {
        errorTest("hoorah \\", "Syntax error: unexpected end of input in escape sequence (\\)");
    }

    public void testEmptyVariable()
    {
        errorTest("${}", "Syntax error: empty variable");
    }

    public void testParenEmptyVariable()
    {
        errorTest("$()", "Syntax error: empty variable");
    }

    public void testUnknownVariable()
    {
        errorTest("${greebo}", "Unknown variable 'greebo'");
    }

    public void testParenUnknownVariable()
    {
        errorTest("$(greebo)", "Unknown variable 'greebo'");
    }

    public void testSimpleSubstitution() throws Exception
    {
        successTest("${foo}", "foo");
    }

    public void testParentSimpleSubstitution() throws Exception
    {
        successTest("$(foo)", "foo");
    }

    public void testSimpleSubstitution2() throws Exception
    {
        successTest("${bar}", "baz");
    }

    public void testSubstitutionLeading() throws Exception
    {
        successTest("leading text ${bar}", "leading text baz");
    }

    public void testParenSubstitutionLeading() throws Exception
    {
        successTest("leading text $(bar)", "leading text baz");
    }

    public void testSubstitutionTrailing() throws Exception
    {
        successTest("${bar} trailing text", "baz trailing text");
    }

    public void testParenSubstitutionTrailing() throws Exception
    {
        successTest("$(bar) trailing text", "baz trailing text");
    }

    public void testSubstitutionTwice() throws Exception
    {
        successTest("${foo}${bar}", "foobaz");
    }

    public void testParenSubstitutionTwice() throws Exception
    {
        successTest("$(foo)$(bar)", "foobaz");
    }

    public void testSimpleEscape() throws Exception
    {
        successTest("esc\\ape", "escape");
    }

    public void testEscapeDollar() throws Exception
    {
        successTest("\\${foo}", "${foo}");
    }

    public void testEscapeSlash() throws Exception
    {
        successTest("\\\\", "\\");
    }

    public void testEscapeSlashSlash() throws Exception
    {
        successTest("\\\\\\\\", "\\\\");
    }

    public void testEscapeSlashOther() throws Exception
    {
        successTest("\\\\\\x", "\\x");
    }

    public void testSlashInVariable() throws Exception
    {
        successTest("${a\\b}", "slashed");
    }

    public void testParenSlashInVariable() throws Exception
    {
        successTest("$(a\\b)", "slashed");
    }

    public void testNestedVariable() throws Exception
    {
        scope.add(new GenericVariable<String>("a", "${foo}"));
        successTest("${a}", "${foo}");
    }

    public void testSpacesPreserved() throws Exception
    {
        successTest("in space  two   three ${bar} vars", "in space  two   three baz vars");
    }

    public void testParenSpacesPreserved() throws Exception
    {
        successTest("in space  two   three $(bar) vars", "in space  two   three baz vars");
    }

    public void testQuote() throws Exception
    {
        successTest("a \"quote", "a \"quote");
    }

    public void testQuotes() throws Exception
    {
        successTest("some \"quotes in\" here", "some \"quotes in\" here");
    }

    public void testSplitSimple() throws Exception
    {
        successSplitTest("hello", "hello");
    }

    public void testSplitTwoPieces() throws Exception
    {
        successSplitTest("two pieces", "two", "pieces");
    }

    public void testSplitMultipleSpaces() throws Exception
    {
        successSplitTest("multiple   spaces", "multiple", "spaces");
    }

    public void testSplitEmptyString() throws Exception
    {
        successSplitTest("");
    }

    public void testSplitSpace() throws Exception
    {
        successSplitTest(" ");
    }

    public void testSplitSpaces() throws Exception
    {
        successSplitTest("   ");
    }

    public void testSplitManyPieces() throws Exception
    {
        successSplitTest("this string  has   many pieces in it", "this", "string", "has", "many", "pieces", "in", "it");
    }

    public void testSplitQuoted() throws Exception
    {
        successSplitTest("with \"a quoted\" bit", "with", "a quoted", "bit");
    }

    public void testSplitQuotedInMiddle() throws Exception
    {
        successSplitTest("with\"a quoted\"bit there", "witha quotedbit", "there");
    }

    public void testSplitEscapedSpace() throws Exception
    {
        successSplitTest("an\\ escaped space", "an escaped", "space");
    }

    public void testSplitEscapedQuote() throws Exception
    {
        successSplitTest("an \\\"escaped quote \"and then this\"", "an", "\"escaped", "quote", "and then this");
    }

    public void testSplitEscapedQuoteInQuotes() throws Exception
    {
        successSplitTest("\"inside \\\" quotes\"", "inside \" quotes");
    }

    public void testSplitSingleVariable() throws Exception
    {
        successSplitTest("${bar}", "baz");
    }

    public void testSplitParenSingleVariable() throws Exception
    {
        successSplitTest("$(bar)", "baz");
    }

    public void testSplitAroundMultipleVariables() throws Exception
    {
        successSplitTest("${foo} and ${bar}", "foo", "and", "baz");
    }

    public void testSplitParenAroundMultipleVariables() throws Exception
    {
        successSplitTest("$(foo) and $(bar)", "foo", "and", "baz");
    }

    public void testSplitVariableInQuotes() throws Exception
    {
        successSplitTest("quotes \"around ${bar}\"", "quotes", "around baz");
    }

    public void testSplitParenVariableInQuotes() throws Exception
    {
        successSplitTest("quotes \"around $(bar)\"", "quotes", "around baz");
    }

    public void testSplitQuotesInVariable() throws Exception
    {
        scope.add(new GenericVariable<String>("a\"b", "val"));
        successSplitTest("odd ${a\"b} ref", "odd", "val", "ref");
    }

    public void testSplitParenQuotesInVariable() throws Exception
    {
        scope.add(new GenericVariable<String>("a\"b", "val"));
        successSplitTest("odd $(a\"b) ref", "odd", "val", "ref");
    }

    public void testSplitSpaceInVariable() throws Exception
    {
        scope.add(new GenericVariable<String>("space invader", "val"));
        successSplitTest("odd ${space invader} ref", "odd", "val", "ref");
    }

    public void testSplitParenSpaceInVariable() throws Exception
    {
        scope.add(new GenericVariable<String>("space invader", "val"));
        successSplitTest("odd $(space invader) ref", "odd", "val", "ref");
    }

    public void testSplitSpaceAtStart() throws Exception
    {
        successSplitTest(" space at start", "space", "at", "start");
    }

    public void testSplitSpaceAtEnd() throws Exception
    {
        successSplitTest("space at end ", "space", "at", "end");
    }

    public void testSplitQuotesAtStart() throws Exception
    {
        successSplitTest("\"quotes at\" start", "quotes at", "start");
    }

    public void testSplitQuotesAtEnd() throws Exception
    {
        successSplitTest("quotes \"at end\"", "quotes", "at end");
    }

    public void testSplitEmptyQuotedString() throws Exception
    {
        successSplitTest("\"\"", "");
    }

    public void testSplitEmptyStringInMiddle() throws Exception
    {
        successSplitTest("in \"\" middle", "in", "", "middle");
    }

    public void testSplitEmptyStringAdjacent() throws Exception
    {
        successSplitTest("empty\"\" adjacent", "empty", "adjacent");
    }

    public void testSplitEmptyVariable() throws Exception
    {
        successSplitTest("${empty}");
    }

    public void testSplitParenEmptyVariable() throws Exception
    {
        successSplitTest("$(empty)");
    }

    public void testSplitQuotedEmptyVariable() throws Exception
    {
        successSplitTest("\"${empty}\"", "");
    }

    public void testSplitParenQuotedEmptyVariable() throws Exception
    {
        successSplitTest("\"$(empty)\"", "");
    }

    public void testSplitEmptyVariableAdjacent() throws Exception
    {
        successSplitTest("adjacent${empty} ref", "adjacent", "ref");
    }

    public void testSplitParenEmptyVariableAdjacent() throws Exception
    {
        successSplitTest("adjacent$(empty) ref", "adjacent", "ref");
    }

    public void testSplitUnterminatedQuotes() throws Exception
    {
        errorSplitTest("\"unterminated", "Syntax error: unexpected end of input looking for closing quotes (\")");
    }

    public void testDefaultVariableDefined() throws Exception
    {
        successTest("$(bar?def)", "baz");
    }

    public void testDefaultVariableDefinedButEmpty() throws Exception
    {
        successTest("$(empty?def)", "");
    }

    public void testDefaultVariableNotDefined() throws Exception
    {
        successTest("$(undefined?def)", "def");
    }

    public void testDefaultEmpty() throws Exception
    {
        successTest("$(undefined?)", "");
    }

    public void testDefaultSingleVariable() throws Exception
    {
        assertEquals("def", VariableResolver.resolveVariable("$(undefined?def)", scope));
    }

    public void testMixedBracketsBraceParen()
    {
        errorTest("${foo)", "Syntax error: unexpected end of input looking for '}'");
    }

    public void testMixedBracketsParenBrace()
    {
        errorTest("$(foo}", "Syntax error: unexpected end of input looking for ')'");
    }

    public void testBraceInExtendedName() throws Exception
    {
        successTest("$(a{b}c)", "braced");
    }

    public void testParenInName() throws Exception
    {
        successTest("${a(b)c}", "parened");
    }

    public void testReservedCharactersInExtendedName() throws Exception
    {
        for (Character c: asList('!', '%', '#', '&', '/', ':', ';', '/'))
        {
            errorTest("$(a" + c + "b)", "Syntax error: '" + c + "' is reserved and may not be used in an extended variable name");
        }
    }

    public void testFilter() throws Exception
    {
        successTest("$(invalid.name|name)", "this.is.a.badname");
    }

    public void testMultipleFilters() throws Exception
    {
        successTest("$(invalid.name|name|upper)", "THIS.IS.A.BADNAME");
    }

    public void testFilterAndDefault() throws Exception
    {
        successTest("$(foo|upper?def)", "FOO");
    }

    public void testFilterAndDefaultUndefined() throws Exception
    {
        successTest("$(undefined|upper?def)", "def");
    }

    public void testFilterUnknown() throws Exception
    {
        errorTest("$(foo|nosuchfilter)", "Unknown filter 'nosuchfilter'");
    }

    public void testFilterUnknownNonStrict() throws Exception
    {
        assertEquals("foo", VariableResolver.resolveVariables("$(foo|nosuchfilter)", scope, VariableResolver.ResolutionStrategy.RESOLVE_NON_STRICT));
    }
}