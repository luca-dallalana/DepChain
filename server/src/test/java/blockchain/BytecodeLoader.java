package blockchain;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class BytecodeLoader {

    public static String loadBytecode(String contractName) {
        String path = "/contracts/bytecode/" + contractName + ".bin";

        try (InputStream is = BytecodeLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Bytecode file not found: " + path);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines()
                    .map(String::trim)
                    .collect(Collectors.joining())
                    .trim();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load bytecode for " + contractName + ": " + e.getMessage(), e);
        }
    }
}
