package com.zutubi.pulse.master.model;

import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;
import com.zutubi.util.Predicate;
import com.zutubi.util.UnaryProcedure;

import java.util.*;

/**
 * A build dependency graph, linking a root build to all of its dependencies in one direction
 * (either upstream or downstream) transitively.  This is a DAG (Directed Acyclic Graph).
 */
public class BuildGraph
{
    private Node root;

    /**
     * Creates a new graph rooted at the given node.
     * 
     * @param root the root of the graph
     */
    BuildGraph(Node root)
    {
        this.root = root;
    }

    /**
     * Returns the node representing the given build, if any.
     * 
     * @param buildId id of the build to find the node for
     * @return the node for the given build, or null if there is no such node
     */
    public Node findNodeByBuildId(long buildId)
    {
        return root.findByBuildId(buildId);
    }

    /**
     * Returns the build path for a given node.  This is a sequence of build results found on nodes
     * between the root (excluded) and the given node (included).
     * 
     * @param node the node to get the path for
     * @return path of build results from the root to the given node, or null if the node is not
     *         found in this graph
     */
    public List<BuildResult> getBuildPath(Node node)
    {
        List<Node> path = root.getPath(node);
        if (path == null)
        {
            return null;
        }
        
        return CollectionUtils.map(path, new Mapping<Node, BuildResult>()
        {
            public BuildResult map(Node node)
            {
                return node.getBuild();
            }
        });
    }

    /**
     * Finds a node by a given build path, using the projects of the builds to walk the graph.  This
     * can be used to find equivalent nodes in two graphs rooted at builds of the same project.
     * Note that the shape of the graph can change between builds, so sometimes there is no
     * equivalent build to find.
     * 
     * @param buildPath build path from which the projects are extracted and used to traverse from
     *                  the root of this graph
     * @return the node found by traversing the full path, or null if no such node could be found
     */
    public Node findNodeByProjects(List<BuildResult> buildPath)
    {
        Node node = root;
        for (BuildResult build: buildPath)
        {
            node = nextByProjectId(node, build.getProject().getId());
            if (node == null)
            {
                break;
            }
        }
        
        return node;
    }

    private Node nextByProjectId(Node node, long projectId)
    {
        for (Node connected: node.getConnected())
        {
            if (connected.getBuild().getProject().getId() == projectId)
            {
                return connected;
            }
        }

        return null;
    }

    /**
     * Applies the given procedure to all nodes in this graph, starting at the root and working
     * downwards in depth-first fashion.
     * 
     * @param fn procedure to apply to each node
     */
    public void forEach(UnaryProcedure<Node> fn)
    {
        root.forEach(fn, new HashSet<Node>());
    }

    /**
     * A single node in a build graph, representing a specific build.
     */
    public static class Node
    {
        private BuildResult build;
        private Set<Node> connected = new HashSet<Node>();

        Node(BuildResult build)
        {
            this.build = build;
        }
        
        void connectNode(Node node)
        {
            connected.add(node);
        }

        /**
         * @return the build represented by this node
         */
        public BuildResult getBuild()
        {
            return build;
        }

        /**
         * @return the set of all nodes reachable from this one.
         */
        public Set<Node> getConnected()
        {
            return Collections.unmodifiableSet(connected);
        }

        Node findByBuildId(final long buildId)
        {
            return findByPredicate(new Predicate<Node>()
            {
                public boolean satisfied(Node node)
                {
                    return node.getBuild().getId() == buildId;
                }
            });
        }

        Node findByPredicate(Predicate<Node> predicate)
        {
            if (predicate.satisfied(this))
            {
                return this;
            }
            else
            {
                for (Node node: connected)
                {
                    Node found = node.findByPredicate(predicate);
                    if (found != null)
                    {
                        return found;
                    }
                }

                return null;
            }
        }

        void forEach(UnaryProcedure<Node> fn, Set<Node> visited)
        {
            if (visited.contains(this))
            {
                return;
            }
            
            visited.add(this);
            fn.run(this);
            
            for (Node node: connected)
            {
                node.forEach(fn, visited);
            }
        }

        List<Node> getPath(Node node)
        {
            if (node == this)
            {
                return Collections.emptyList();
            }
            else
            {
                for (Node n: connected)
                {
                    List<Node> relativePath = n.getPath(node);
                    if (relativePath != null)
                    {
                        List<Node> path = new LinkedList<Node>();
                        path.add(n);
                        path.addAll(relativePath);
                        return path;
                    }
                }
                
                return null;
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            Node that = (Node) o;
            return build.equals(that.build);
        }

        @Override
        public int hashCode()
        {
            return build.hashCode();
        }

        @Override
        public String toString()
        {
            return build.getProject().getName() + " :: build " + build.getNumber(); 
        }
    }
}
