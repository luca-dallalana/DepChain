package model;

import crypto.CryptoLib;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

public class Node {
    public byte[]  parentHash;   // hash of the parent node
    public String  cmd;          // the client command
    public int     height;       // position in the tree

    public Node(byte[] parentHash, String cmd, int height) {
        this.parentHash = parentHash;
        this.cmd = cmd;
        this.height = height;
    }

    public byte[] depHash() throws Exception { //FIXME: Nos devias adicionar viewNumber e QC aos hashes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (parentHash != null) {
            baos.write(parentHash);
        }
        baos.write(cmd.getBytes(StandardCharsets.UTF_8));
        baos.write(ByteBuffer.allocate(4).putInt(height).array());

        return CryptoLib.hash(baos.toByteArray());
    }

    public static Node createLeaf(Node parent, String cmd) throws Exception {
        return new Node(
            parent.depHash(),
            cmd,
            parent.height + 1
        );
    }

}
