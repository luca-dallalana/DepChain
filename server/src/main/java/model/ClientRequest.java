package model;

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
}
