package com.zutubi.pulse.core;

import com.zutubi.pulse.core.model.Property;

import java.util.List;

/**
 * 
 *
 */
public class FileLoaderTest extends FileLoaderTestBase
{
    public void setUp() throws Exception 
    {
        super.setUp();

        // initialise the loader some test objects.
        loader.register("dependency", Dependency.class);
        loader.register("reference", SimpleReference.class);
        loader.register("nested", SimpleNestedType.class);
        loader.register("type", SimpleType.class);
        loader.register("some-reference", SomeReference.class);
        loader.register("validateable", SimpleValidateable.class);
    }

    public void testSimpleReference() throws Exception
    {
        SimpleRoot root = new SimpleRoot();
        loader.load(getInput("testSimpleReference"), root);

        Object o = root.getReference("a");
        assertNotNull(o);
        assertTrue(o instanceof SimpleReference);

        SimpleReference t = (SimpleReference) o;
        assertEquals("a", t.getName());
    }

    public void testResolveReference() throws Exception
    {
        SimpleRoot root = new SimpleRoot();
        loader.load(getInput("testResolveReference"), root);

        Object a = root.getReference("a");
        assertNotNull(a);
        assertTrue(a instanceof SimpleReference);

        Object b = root.getReference("b");
        assertNotNull(b);
        assertTrue(b instanceof SimpleReference);

        SimpleReference rb = (SimpleReference) b;
        assertEquals(a, rb.getRef());
    }

    public void testNestedType() throws Exception
    {
        SimpleRoot root = new SimpleRoot();
        loader.load(getInput("testNestedType"), root);

        assertNotNull(root.getReference("a"));

        SimpleNestedType a = (SimpleNestedType) root.getReference("a");
        assertNotNull(a.getNestedType("b"));
        assertNotNull(a.getNestedType("c"));
    }

    public void testNonBeanName() throws Exception
    {
        SimpleRoot root = new SimpleRoot();
        loader.load(getInput("testNonBeanName"), root);

        Object a = root.getReference("a");
        assertNotNull(a);
        assertTrue(a instanceof SomeReference);
        assertEquals("a", ((SomeReference) a).getSomeValue());

    }

    public void testSampleProject() throws Exception
    {
        PulseFile bf = new PulseFile();
        Scope scope = new Scope();
        Property property = new Property("base.dir", "/whatever");
        scope.add(property);

        loader.load(getInput("testSampleProject"), bf, scope, new FileResourceRepository(), null);
    }

    public void testDependency() throws Exception
    {
        PulseFile bf = new PulseFile();
        loader.load(getInput("testDependency"), bf);

        assertNotNull(bf.getDependencies());
        assertEquals(1, bf.getDependencies().size());
        assertEquals("1", bf.getDependencies().get(0).getName());
        assertEquals("2", bf.getDependencies().get(0).getVersion());

        Recipe recipe = bf.getRecipe(bf.getDefaultRecipe());
        assertNotNull(recipe);
        assertEquals(1, recipe.getDependencies().size());
        assertEquals("a", recipe.getDependencies().get(0).getName());
        assertEquals("b", recipe.getDependencies().get(0).getVersion());
    }

    public void testScope() throws Exception
    {
        PulseFile pf = new PulseFile();
        loader.load(getInput("testScope"), pf);

        Recipe recipe = pf.getRecipe("r1");
        assertNotNull(recipe);
        assertNotNull(recipe.getCommand("scope1"));
        assertNotNull(recipe.getCommand("scope2"));
    }

    public void testScopeTopLevel() throws Exception
    {
        PulseFile pf = new PulseFile();
        loader.load(getInput("testScopeTopLevel"), pf);

        assertNotNull(pf.getRecipe("first"));
        assertNotNull(pf.getRecipe("second"));
    }

    public void testScoping() throws Exception
    {
        PulseFile pf = new PulseFile();
        loader.load(getInput("testScoping"), pf);

        Recipe recipe = pf.getRecipe("global");
        assertNotNull(recipe);
        Command command = recipe.getCommand("in recipe");
        assertNotNull(command);
/*
        ExecutableCommand exe = (ExecutableCommand) ((CommandGroup)command).getCommand();
        assertEquals("in command", exe.getExe());
*/
    }

    public void testMacro() throws Exception
    {
        PulseFile pf = new PulseFile();
        loader.load(getInput("testMacro"), pf);

        Recipe recipe = pf.getRecipe("r1");
        assertNotNull(recipe);
        Command command = recipe.getCommand("m1-e1");
        assertNotNull(command);
        command = recipe.getCommand("m1-e2");
        assertNotNull(command);
    }

    public void testMacroEmpty() throws Exception
    {
        PulseFile pf = new PulseFile();
        loader.load(getInput("testMacroEmpty"), pf);

        Recipe recipe = pf.getRecipe("r1");
        assertNotNull(recipe);
        assertEquals(0, recipe.getCommands().size());
    }

    public void testMacroExpandError() throws Exception
    {
        errorHelper("testMacroExpandError", "While expanding macro defined at line 4 column 5: Processing element 'no-such-type': starting at line 5 column 9: Undefined type 'no-such-type'");
    }

    public void testMacroNoName() throws Exception
    {
        errorHelper("testMacroNoName", "Required attribute 'name' not found");
    }

    public void testMacroUnknownAttribute() throws Exception
    {
        errorHelper("testMacroUnknownAttribute", "Unrecognised attribute 'unkat'");
    }

    public void testMacroRefNoMacro() throws Exception
    {
        errorHelper("testMacroRefNoMacro", "Required attribute 'macro' not found");
    }

    public void testMacroRefNotMacro() throws Exception
    {
        errorHelper("testMacroRefNotMacro", "Reference '${not-macro}' does not resolve to a macro");
    }

    public void testMacroRefNotFound() throws Exception
    {
        errorHelper("testMacroRefNotFound", "Unknown variable reference 'not-found'");
    }

    public void testMacroRefUnknownAttribute() throws Exception
    {
        errorHelper("testMacroRefUnknownAttribute", "Unrecognised attribute 'whatthe'");
    }

    public void testMacroInfiniteRecursion() throws Exception
    {
        errorHelper("testMacroInfiniteRecursion", "Maximum recursion depth 128 exceeded");
    }

    public void testValidation() throws Exception
    {
        try
        {
            PulseFile bf = new PulseFile();
            loader.load(getInput("testValidateable"), bf);
            fail();
        }
        catch (ParseException e)
        {
            assertEquals(e.getMessage(), "Processing element 'validateable': starting at line 4 column 5: error\n");
        }
    }

    public void testArtifactInvalidName() throws Exception
    {
        errorHelper("testArtifactInvalidName", "alphanumeric");
    }

    public void testArtifactMissingName() throws Exception
    {
        errorHelper("testArtifactMissingName", "Required attribute name not specified");
    }

    public void testProcessNoProcessor() throws PulseException
    {
        try
        {
            PulseFile bf = new PulseFile();
            loader.load(getInput("testProcessNoProcessor"), bf);
            fail();
        }
        catch (ParseException e)
        {
            assertTrue(e.getMessage().contains("attribute 'processor' not specified"));
        }
    }

    public void testSpecificRecipe() throws PulseException
    {
        PulseFile bf = new PulseFile();
        loader.load(getInput("testSpecificRecipe"), bf, null, new FileResourceRepository(), new RecipeLoadPredicate(bf, "default"));
        assertEquals(2, bf.getRecipes().size());
        assertNotNull(bf.getRecipe("default"));
        assertNotNull(bf.getRecipe("default").getCommand("build"));
        assertNotNull(bf.getRecipe("don't load!"));
    }

    public void testSpecificRecipeDefault() throws PulseException
    {
        PulseFile bf = new PulseFile();
        loader.load(getInput("testSpecificRecipe"), bf, null, new FileResourceRepository(), new RecipeLoadPredicate(bf, null));
        assertEquals(2, bf.getRecipes().size());
        assertNotNull(bf.getRecipe("default"));
        assertNotNull(bf.getRecipe("default").getCommand("build"));
        assertNotNull(bf.getRecipe("don't load!"));
    }

    public void testSpecificRecipeError() throws PulseException
    {
        try
        {
            PulseFile bf = new PulseFile();
            loader.load(getInput("testSpecificRecipe"), bf, null, new FileResourceRepository(), new RecipeLoadPredicate(bf, "don't load!"));
            fail();
        }
        catch (PulseException e)
        {
            e.printStackTrace();
        }
    }

    public void testUnknownAttribute()
    {
        try
        {
            loader.load(getInput("testUnknownAttribute"), new SimpleType());
            fail();
        }
        catch (PulseException e)
        {
            if (!e.getMessage().contains("bad-attribute"))
            {
                fail();
            }
        }
    }
}
