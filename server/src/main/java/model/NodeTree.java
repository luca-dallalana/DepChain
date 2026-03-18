package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import crypto.CryptoLib;

public class NodeTree {
    // Store all nodes by their hash for tree traversal
    private ConcurrentHashMap<String, Node> nodeStore;

    private Node firstNode;

    public NodeTree() {
        this.nodeStore = new ConcurrentHashMap<>();

        this.firstNode = new Node(null, new ClientRequest(0, 0, "", null), 0);
        try {
            storeNode(firstNode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Store a node in the tree
    public void storeNode(Node node) throws Exception {
        String nodeHash = CryptoLib.hashToString(node.depHash());
        nodeStore.put(nodeHash, node);
    }

    // Retrieve a node by hash
    public Node getNodeByHash(byte[] hash) {
        return nodeStore.get(CryptoLib.hashToString(hash));
    }

    // Get the first node
    public Node getFirstNode() {
        return firstNode;
    }

    // Check if descendant extends from ancestor in the tree
    public boolean extendsFrom(Node descendant, Node ancestor) {
        try {
            if (descendant == null || ancestor == null) {
                return false;
            }
            byte[] ancestorHash = ancestor.depHash();

            // Traverse up the tree from descendant
            Node current = descendant;
            while (current != null && current.parentHash != null) {
                // Get parent from storage
                Node parent = getNodeByHash(current.parentHash);

                if (parent == null) {
                    //System.out.println("-------> Parent node not found for node: " + current);
                    return false;
                }

                // Check if parent matches ancestor
                byte[] parentHash = parent.depHash();
                if (Arrays.equals(parentHash, ancestorHash)) {
                    return true;
                }

                current = parent;
            }
            //System.out.println("-------> Node " + descendant + " does not extend from " + ancestor);
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Node> getNodesUntil(Node node, int height){
        List<Node> nodeList = new ArrayList<>();
        Node nextNode = node;
        while (true) {
            
            if (nextNode == null) {
                break; // No more nodes to execute
            }

            if (nextNode.height <= height) {
                break; // Already executed
            }

            nodeList.add(nextNode);

            Node tmpNode = getNodeByHash(nextNode.parentHash);
            nextNode = tmpNode;
        }
        return nodeList;
    }

}
