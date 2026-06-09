package tphone.airmessage.decode;
public class GsmDecoder {
    static { try { System.loadLibrary("gsmMsgDecode"); } catch (UnsatisfiedLinkError e) {} }
    public static native String getGsmMsgParseDetails(byte[] msg, int len);
    public static boolean isAvailable() { try { GsmDecoder.class.getDeclaredMethod("isAvailable"); return true; } catch (Exception e) { return false; } }
}
