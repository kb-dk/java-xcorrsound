package dk.kb.xcorrsound;

public class Utils {
    public static int[] bytesToInts(byte[] bytes){
        int[] result = new int[bytes.length / 4];
        for (int i = 0; i < bytes.length; i+=4) {
            int integer = ((bytes[i+0] << 24) + (bytes[i+1] << 16) + (bytes[i+2] << 8) + (bytes[i+3] << 0));
            result[i/4] = integer;
        }
        return result;
    }
    
    public static int bytesToIntsLittleEndian(byte byte1, byte byte2, byte byte3, byte byte4){
        int integer = ((byte1 << 24) + (byte2 << 16) + (byte3 << 8) + (byte4 << 0));
        return integer;
    }
    
    public static int bytesToIntsBigEndian(byte byte1, byte byte2, byte byte3, byte byte4){
        int integer = ((byte4 << 24) + (byte3 << 16) + (byte2 << 8) + (byte1 << 0));
        return integer;
    }
    
}
