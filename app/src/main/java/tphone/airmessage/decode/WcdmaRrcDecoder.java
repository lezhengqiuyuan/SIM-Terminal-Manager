package tphone.airmessage.decode;
public class WcdmaRrcDecoder {
    static { try { System.loadLibrary("wcdma_rrc_decoder"); } catch (UnsatisfiedLinkError e) {} }
    public static native int getMessageID(byte[] msg, int len);
    public static native String[] getTypeNameList();
    public static native String parseMessageToXml(byte[] msg, int len);
    public static boolean isAvailable() { try { WcdmaRrcDecoder.class.getDeclaredMethod("isAvailable"); return true; } catch (Exception e) { return false; } }
}
