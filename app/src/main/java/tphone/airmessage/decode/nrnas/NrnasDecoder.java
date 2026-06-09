package tphone.airmessage.decode.nrnas;
public class NrnasDecoder {
    static { try { System.loadLibrary("NRMsgDecode"); } catch (UnsatisfiedLinkError e) {} }
    public static native String getAttachReqType(byte[] msg, int len);
    public static native String getNASMsgParseDetails(byte[] msg, int len);
    public static native String getRRCMessageIEValue(byte[] msg, int len, String ie);
    public static native String getRRCMsgParseDetails(byte[] msg, int len);
    public static boolean isAvailable() { try { NrnasDecoder.class.getDeclaredMethod("isAvailable"); return true; } catch (Exception e) { return false; } }
}
