package model;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import crypto.CryptoLib;

public class Node {
    public byte[]  parentHash;   // hash of the parent node
    public ClientRequest  cmd;          // the client command
    public int     height;       // position in the tree

    public Node(byte[] parentHash, ClientRequest cmd, int height) {
        this.parentHash = parentHash;
        this.cmd = cmd;
        this.height = height;
    }

    public byte[] depHash() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (parentHash != null) {
            baos.write(parentHash);
        }
        baos.write(cmd.getCommand().getBytes(StandardCharsets.UTF_8));
        baos.write(ByteBuffer.allocate(4).putInt(cmd.getPort()).array());
        baos.write(ByteBuffer.allocate(4).putInt(height).array());

        return CryptoLib.hash(baos.toByteArray());
    }

    public static Node createLeaf(Node parent, ClientRequest cmd) throws Exception {
        return new Node(
            parent.depHash(),
            cmd,
            parent.height + 1
        );
    }

}
