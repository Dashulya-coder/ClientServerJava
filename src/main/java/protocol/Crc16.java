package protocol;

public class Crc16 {

    private static final int POLYNOMIAL = 0x8005;

    public static int calculate(byte[] bytes) {
        return calculate(bytes, 0, bytes.length);
    }

    public static int calculate(byte[] bytes, int offset, int length) {
        int crc = 0xFFFF;

        for (int i = offset; i < offset + length; i++) {
            crc ^= (bytes[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
            }
            crc &= 0xFFFF;
        }
        return crc;
    }

    public static byte[] calculateBytes(byte[] bytes, int offset, int length) {
        int crc = calculate(bytes, offset, length);
        return new byte[]{
                (byte) ((crc >> 8) & 0xFF),
                (byte) (crc & 0xFF)
        };
    }
}