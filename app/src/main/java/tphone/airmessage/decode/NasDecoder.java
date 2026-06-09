package tphone.airmessage.decode;
public class NasDecoder {
    static { try { System.loadLibrary("nasDecoder"); } catch (UnsatisfiedLinkError e) {} }
    public static native String getNasMsgParseDetails(byte[] msg, int len, int rat);
    public static boolean isAvailable() { try { NasDecoder.class.getDeclaredMethod("isAvailable"); return true; } catch (Exception e) { return false; } }
}
