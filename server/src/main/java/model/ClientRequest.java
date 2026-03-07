package model;

import java.util.Objects;

public class ClientRequest {
    private int port;
    private String command;

    public ClientRequest(int port, String command){
        this.port = port;
        this.command = command;
    }

    public int getPort() {
        return port;
    }

    public String getCommand() {
        return command;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientRequest that = (ClientRequest) o;
        return port == that.port && Objects.equals(command, that.command);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(port, command);
    }
}
