import java.util.Arrays;
public class Checksum {
    public static byte[] genChecksum(byte[] input) {
        int value=0;
        int len=input.length;
        boolean isEven=input.length%2==0;
        if(!isEven)len--;
        for (int i=0;i<len;i+=2){
            value+=((input[i]&0xff)<<8)+(input[i+1]&0xff);
        }
        if(!isEven)value+=(input[len]&0xff)<<8;
        //the way don't use the first 16
        byte[]result=new byte[2];
        result[0]=(byte)(~(value>>8&0xff));
        result[1]=(byte) (~(value&0xff));
        //TODO
        return result;
    }

    public static boolean verifyChecksum(byte[] input) {
        int value=0;
        int len=input.length;
        boolean isEven=input.length%2==0;
        if(!isEven)len--;
        for (int i=0;i<len;i+=2){
            value+=((input[i]&0xff)<<8)+(input[i+1]&0xff);
        }
        if(!isEven)value+=(input[len]&0xff)<<8;
        return ((value&0xffff)==0xffff);
        //TODO
    }
}
