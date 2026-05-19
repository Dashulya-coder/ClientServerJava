import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Protocol tests")
class ProtocolTest {

    private static final byte[] TEST_KEY = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    };

    private CryptoService cryptoService;
    private PackageBuilder builder;
    private PackageParser  parser;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService(TEST_KEY);
        builder = new PackageBuilder(cryptoService);
        parser  = new PackageParser(cryptoService);
    }

    @Nested
    @DisplayName("CRC16")
    class Crc16Tests {

        @Test
        @DisplayName("same bytes produce same checksum")
        void sameInput_sameOutput() {
            byte[] data = {0x01, 0x02, 0x03, 0x04};
            assertEquals(Crc16.calculate(data), Crc16.calculate(data));
        }

        @Test
        @DisplayName("different bytes produce different checksum")
        void differentInput_differentOutput() {
            assertNotEquals(
                    Crc16.calculate(new byte[]{0x01, 0x02, 0x03}),
                    Crc16.calculate(new byte[]{0x01, 0x02, 0x04})
            );
        }

        @Test
        @DisplayName("checksum fits into two bytes")
        void resultInRange() {
            int crc = Crc16.calculate(new byte[]{0x13, 0x01, 0x00});
            assertTrue(crc >= 0 && crc <= 0xFFFF);
        }

        @Test
        @DisplayName("byte array form matches integer form")
        void calculateBytes_bigEndian() {
            byte[] data = {0x01, 0x02, 0x03};
            byte[] crcBytes = Crc16.calculateBytes(data, 0, data.length);
            int fromBytes = ((crcBytes[0] & 0xFF) << 8) | (crcBytes[1] & 0xFF);
            assertEquals(Crc16.calculate(data), fromBytes);
        }
    }

    @Nested
    @DisplayName("AES encryption")
    class CryptoTests {

        @Test
        @DisplayName("decrypt undoes encrypt")
        void encryptDecrypt_returnsOriginal() throws Exception {
            byte[] orig = "secret data".getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(orig, cryptoService.decrypt(cryptoService.encrypt(orig)));
        }

        @Test
        @DisplayName("ciphertext is not the plaintext")
        void encrypted_differs() throws Exception {
            byte[] plain = "test data".getBytes();
            assertFalse(Arrays.equals(plain, cryptoService.encrypt(plain)));
        }

        @Test
        @DisplayName("decrypting with a wrong key fails")
        void wrongKey_throws() throws Exception {
            byte[] encrypted = cryptoService.encrypt("secret".getBytes());
            CryptoService wrong = new CryptoService(new byte[32]);
            assertThrows(Exception.class, () -> wrong.decrypt(encrypted));
        }
    }

    @Nested
    @DisplayName("Build and parse round-trip")
    class RoundTripTests {

        @Test
        @DisplayName("simple package survives the round-trip")
        void simpleRoundTrip() throws Exception {
            Message original = new Message(0x01, 42, "{\"action\":\"login\"}");
            byte[] raw = builder.build((byte) 0x05, 1L, original);
            Package parsed = parser.parse(raw);

            assertEquals(0x05, parsed.getClientId() & 0xFF);
            assertEquals(1L, parsed.getPacketId());
            assertEquals(original, parsed.getMessage());
        }

        @Test
        @DisplayName("large packet ids are preserved")
        void largePacketId() throws Exception {
            long id = 9_999_999_999L;
            byte[] raw = builder.build((byte) 1, id, new Message(1, 1, ""));
            assertEquals(id, parser.parse(raw).getPacketId());
        }

        @Test
        @DisplayName("non-ASCII payload survives the round-trip")
        void unicodePayload() throws Exception {
            String text = "Hello, world! Це секретне повідомлення.";
            byte[] raw = builder.build((byte) 2, 100L, new Message(5, 55, text));
            assertEquals(text, parser.parse(raw).getMessage().getPayload());
        }

        @Test
        @DisplayName("negative command type is preserved")
        void commandTypePreserved() throws Exception {
            byte[] raw = builder.build((byte) 1, 1L, new Message(0xDEADBEEF, 7, "data"));
            assertEquals(0xDEADBEEF, parser.parse(raw).getMessage().getCommandType());
        }

        @ParameterizedTest(name = "packetId = {0}")
        @ValueSource(longs = {0L, 1L, 255L, 65535L, 100000L, Long.MAX_VALUE / 2})
        @DisplayName("various packet ids are preserved")
        void variousPacketIds(long id) throws Exception {
            byte[] raw = builder.build((byte) 1, id, new Message(1, 1, "x"));
            assertEquals(id, parser.parse(raw).getPacketId());
        }
    }

    @Nested
    @DisplayName("Invalid packages")
    class InvalidPacketTests {

        @Test
        @DisplayName("parsing null throws")
        void nullInput_throws() {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        }

        @Test
        @DisplayName("parsing an empty array throws")
        void emptyInput_throws() {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[0]));
        }

        @Test
        @DisplayName("wrong magic byte is rejected")
        void wrongMagic_throws() throws Exception {
            byte[] raw = builder.build((byte) 1, 1L, new Message(1, 1, "test"));
            raw[0] = 0x00;
            assertThrows(IllegalArgumentException.class, () -> parser.parse(raw));
        }

        @Test
        @DisplayName("tampered header fails the CRC check")
        void corruptHeader_throws() throws Exception {
            byte[] raw = builder.build((byte) 1, 1L, new Message(1, 1, "test"));
            raw[3] ^= 0xFF;
            assertThrows(IllegalArgumentException.class, () -> parser.parse(raw));
        }

        @Test
        @DisplayName("tampered body fails to parse")
        void corruptBody_throws() throws Exception {
            byte[] raw = builder.build((byte) 1, 1L, new Message(1, 1, "hello"));
            raw[raw.length - 1] ^= 0xFF;
            assertThrows(Exception.class, () -> parser.parse(raw));
        }
    }

    @Nested
    @DisplayName("Package layout")
    class StructureTests {

        @Test
        @DisplayName("packet starts with the magic byte")
        void firstByteIsMagic() throws Exception {
            byte[] raw = builder.build((byte) 1, 1L, new Message(1, 1, "x"));
            assertEquals(0x13, raw[0] & 0xFF);
        }

        @Test
        @DisplayName("second byte stores the client id")
        void secondByteIsClientId() throws Exception {
            byte[] raw = builder.build((byte) 0x77, 1L, new Message(1, 1, "x"));
            assertEquals(0x77, raw[1] & 0xFF);
        }

        @Test
        @DisplayName("packet id is written as big-endian long")
        void packetIdBigEndian() throws Exception {
            long id = 0x0102030405060708L;
            byte[] raw = builder.build((byte) 1, id, new Message(1, 1, "x"));
            assertEquals(id, ByteBuffer.wrap(raw, 2, 8).getLong());
        }
    }
}