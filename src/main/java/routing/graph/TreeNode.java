package routing.graph;

import org.matsim.api.core.v01.network.Link;

public class TreeNode {
    public final TreeNode parent;
    public final int nodeIdx;
    public final Link link;
    public final double cost;

    public TreeNode(int nodeIdx, TreeNode parent, Link link, double cost) {
        this.parent = parent;
        this.nodeIdx = nodeIdx;
        this.link = link;
        this.cost = cost;
    }
}
