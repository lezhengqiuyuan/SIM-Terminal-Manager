package tphone.airmessage.decode;
public class WcdmaDecoder {
    static { try { System.loadLibrary("wcdmaMsgDecode"); } catch (UnsatisfiedLinkError e) {} }
    public static native String getWcdmaMsgKeyInfo(byte[] msg, int len);
    public static native String getWcdmaMsgParseDetails(byte[] msg, int len);
    public static boolean isAvailable() { try { WcdmaDecoder.class.getDeclaredMethod("isAvailable"); return true; } catch (Exception e) { return false; } }
}
