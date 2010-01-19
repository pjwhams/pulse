package com.zutubi.pulse.master.xwork.actions.project;

import com.zutubi.pulse.master.dependency.DependencyGraphData;
import com.zutubi.pulse.master.dependency.ProjectDependencyGraph;
import com.zutubi.pulse.master.dependency.SimpleTreeLayoutAlgorithm;
import com.zutubi.util.*;

import java.util.List;

/**
 * Renders the trees in project dependency graphs to grid-based diagrams so
 * that they may be visualised.  Grids are used as they translate directly to
 * HTML tables.
 */
public class ProjectDependencyGraphRenderer
{
    /**
     * Factor for scaling from abstract x positions to grid x positions.
     * Accounts for the grid space used to draw connecting lines.
     */
    public static final int SCALE_FACTOR_X = 3;
    /**
     * Factor for scaling from abstract y positions to grid y positions.
     * Accounts for the fact that boxes are two cells high.
     */
    public static final int SCALE_FACTOR_Y = 2;

    /**
     * Renders a tree of upstream dependencies.  This tree is rendered with the
     * leaves on the left flowing to the root on the right.
     *
     * @param graph graph to take the upstream tree from
     * @return a rendered version of the graph's upstream tree
     */
    public Grid<ProjectDependencyData> renderUpstream(ProjectDependencyGraph graph)
    {
        Grid<ProjectDependencyData> grid = render(graph.getUpstreamRoot());
        grid.flipHorizontal(new UnaryProcedure<ProjectDependencyData>()
        {
            public void run(ProjectDependencyData dependencyData)
            {
                dependencyData.flipHorizontal();
            }
        });

        return grid;
    }

    /**
     * Renders a tree of downstream dependencies.  This tree is rendered with
     * the root on the left flowing to the leaves on the right.
     *
     * @param graph graph to take the downstream tree from
     * @return a rendered version of the graph's downstream tree
     */
    public Grid<ProjectDependencyData> renderDownstream(ProjectDependencyGraph graph)
    {
        return render(graph.getDownstreamRoot());
    }

    private Grid<ProjectDependencyData> render(TreeNode<DependencyGraphData> root)
    {
        SimpleTreeLayoutAlgorithm<DependencyGraphData> layout = new SimpleTreeLayoutAlgorithm<DependencyGraphData>();
        TreeNode<Pair<DependencyGraphData, Point>> layTree = layout.layout(root);
        Point bounds = layout.getBounds(layTree);

        // The additional 1 for x and 2 for y allow for the fact that the grid
        // encompasses the boxs at the right and bottom of the diagram.
        Grid<ProjectDependencyData> grid = new Grid<ProjectDependencyData>(bounds.getX() * SCALE_FACTOR_X + 1, bounds.getY() * SCALE_FACTOR_Y + 2);
        renderToGrid(layTree, grid, true);
        return grid;
    }

    private void renderToGrid(TreeNode<Pair<DependencyGraphData, Point>> node, Grid<ProjectDependencyData> grid, boolean root)
    {
        Point position = getGridPosition(node);

        // Fill the cell at our position, and mark the one below dead.
        grid.getCell(position).setData(ProjectDependencyData.makeBox(node.getData().first, root));
        grid.getCell(position.down()).setData(ProjectDependencyData.makeDead());

        if (!node.isLeaf())
        {
            // Draw the edge coming from us and the vertical line (if required).
            List<TreeNode<Pair<DependencyGraphData,Point>>> children = node.getChildren();
            boolean multiChild = children.size() > 1;
            if (multiChild)
            {
                TreeNode<Pair<DependencyGraphData, Point>> firstChild = children.get(0);
                TreeNode<Pair<DependencyGraphData, Point>> lastChild = children.get(children.size() - 1);
                Point firstChildPosition = getGridPosition(firstChild);
                Point lastChildPosition = getGridPosition(lastChild);

                Point currentPosition = firstChildPosition.down().left().left();
                while (currentPosition.getY() <= lastChildPosition.getY())
                {
                    grid.getCell(currentPosition).setData(ProjectDependencyData.makeBordered(true, currentPosition.getY() == position.getY()));
                    currentPosition = currentPosition.down();
                }
            }
            else
            {
                Point rightPosition = position.right();
                grid.getCell(rightPosition).setData(ProjectDependencyData.makeBordered(false, true));
                grid.getCell(rightPosition.right()).setData(ProjectDependencyData.makeBordered(false, true));
            }

            // Draw children, including an edge leading in to each one.
            for (TreeNode<Pair<DependencyGraphData, Point>> child: node)
            {
                grid.getCell(getGridPosition(child).left()).setData(ProjectDependencyData.makeBordered(false, true));
                renderToGrid(child, grid, false);
            }
        }
    }

    private Point getGridPosition(TreeNode<Pair<DependencyGraphData, Point>> node)
    {
        return treeToGrid(node.getData().second);
    }

    private Point treeToGrid(Point point)
    {
        return new Point(point.getX() * SCALE_FACTOR_X, point.getY() * SCALE_FACTOR_Y);
    }
}