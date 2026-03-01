package model;

import crypto.CryptoLib;

public class Node {
    public byte[]  parentHash;   // hash of the parent node
    public String  cmd;          // the client command
    public int     height;       // position in the tree

    public Node(byte[] parentHash, String cmd, int height) {
        this.parentHash = parentHash;
        this.cmd = cmd;
        this.height = height;
    }

    public static Node createLeaf(Node parent, String cmd) throws Exception {
        return new Node(
            CryptoLib.hashNode(parent),
            cmd,
            parent.height + 1
        );
    }

}
