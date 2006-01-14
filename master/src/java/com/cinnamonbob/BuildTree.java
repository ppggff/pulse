package com.cinnamonbob;

import com.cinnamonbob.core.util.TreeNode;
import com.cinnamonbob.core.util.TreeNodeOperation;
import com.cinnamonbob.model.BuildResult;

/**
 */
public class BuildTree
{
    private TreeNode<RecipeController> root;

    public BuildTree()
    {
        root = new TreeNode<RecipeController>(null);
    }

    public TreeNode<RecipeController> getRoot()
    {
        return root;
    }

    public void prepare(final BuildResult buildResult, final String bobFileSource)
    {
        apply(new TreeNodeOperation<RecipeController>()
        {

            public void apply(TreeNode<RecipeController> node)
            {
                node.getData().prepare(buildResult, bobFileSource);
            }
        });
    }

    public void cleanup(final BuildResult buildResult)
    {
        apply(new TreeNodeOperation<RecipeController>()
        {

            public void apply(TreeNode<RecipeController> node)
            {
                node.getData().cleanup(buildResult);
            }
        });
    }

    public void apply(TreeNodeOperation<RecipeController> op)
    {
        apply(op, root);
    }

    private void apply(TreeNodeOperation<RecipeController> op, TreeNode<RecipeController> node)
    {
        for (TreeNode<RecipeController> child : node)
        {
            op.apply(child);
            apply(op, child);
        }
    }

}
