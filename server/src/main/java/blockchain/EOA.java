package blockchain;

public class EOA extends Account {
    public EOA(String address, long balance, long nonce_count) {
        super(address, balance, nonce_count);
    }

    public EOA() {}

    @Override
    public boolean isContract() { return false; }
}
