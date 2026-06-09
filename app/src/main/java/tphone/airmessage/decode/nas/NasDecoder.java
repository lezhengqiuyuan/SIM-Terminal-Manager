package tphone.airmessage.decode.nas;
public class NasDecoder {
    static { try { System.loadLibrary("lteMsgDecode"); } catch (UnsatisfiedLinkError e) {} }
    public static native String getAttachReqType(byte[] msg, int len);
    public static native String getNASMsgParseDetails(byte[] msg, int len);
    public static native String getRRCMessageIEValue(byte[] msg, int len, String ie);
    public static native String getRRCMsgParseDetails(byte[] msg, int len);
    public static boolean isAvailable() { try { NasDecoder.class.getDeclaredMethod("isAvailable"); return true; } catch (Exception e) { return false; } }
}
