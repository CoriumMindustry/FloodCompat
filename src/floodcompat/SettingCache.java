package floodcompat;

import java.nio.*;

/** A class containing static cached setting values */
public class SettingCache{
    public static boolean applied, draw, noEffects;
    public static short floodTeam;

    public static void init(byte[] data){
        if(data.length <= 0) return;

        ByteBuffer buffer = ByteBuffer.wrap(data);

        floodTeam = (short) (buffer.get() & 0xff);
    }
}
