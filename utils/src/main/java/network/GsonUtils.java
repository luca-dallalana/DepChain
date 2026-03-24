package network;

import java.io.IOException;
import java.util.Base64;

import org.hyperledger.besu.datatypes.Address;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class GsonUtils {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .registerTypeAdapter(Address.class, new AddressTypeAdapter())
            .create();

    public static final Gson PRETTY_GSON = new GsonBuilder()
            .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .registerTypeAdapter(Address.class, new AddressTypeAdapter())
            .setPrettyPrinting()
            .create();

    private static class ByteArrayToBase64TypeAdapter extends TypeAdapter<byte[]> {
        @Override
        public void write(JsonWriter out, byte[] value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(Base64.getEncoder().encodeToString(value));
        }

        @Override
        public byte[] read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String b64 = in.nextString();
            if (b64 == null || b64.isEmpty()) {
                return null;
            }
            return Base64.getDecoder().decode(b64);
        }
    }
    
    private static class AddressTypeAdapter extends TypeAdapter<Address> {
        @Override
        public void write(JsonWriter out, Address address) throws IOException {
            if (address == null) {
                out.nullValue();
                return;
            }
            // Convert Address to its hex string representation
            out.value(address.toHexString());
        }

        @Override
        public Address read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String hexAddress = in.nextString();
            if (hexAddress == null || hexAddress.isEmpty()) {
                return null;
            }
            // Convert hex string back to Address
            return Address.fromHexString(hexAddress);
        }
    }
}