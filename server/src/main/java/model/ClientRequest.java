package model;

import java.util.Arrays;
import java.util.Objects;

public class ClientRequest {
    private int port;
    private String command;
    private byte[] sig;

    public ClientRequest(int port, String command, byte[] sig) {
        this.port = port;
        this.command = command;
        this.sig = sig;
    }

    public int getPort() {
        return port;
    }

    public String getCommand() {
        return command;
    }

    public byte[] getSig() {
        return sig;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientRequest that = (ClientRequest) o;
        return port == that.port && Objects.equals(command, that.command) && Arrays.equals(sig, that.sig);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(port, command);
        result = result + Arrays.hashCode(sig);
        return result;
    }
}
