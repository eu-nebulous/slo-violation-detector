package utilities;
import java.security.MessageDigest;

public class SHA256Hash {
    public static String getSha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(input.getBytes());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hashBytes.length; i++) {
            String hex = Integer.toHexString(0xff & hashBytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public static String getShaTruncated(String input) throws Exception {
        String sha256= getSha256(input);
        return truncate(sha256,8);
    }
    
    public static String truncate(String input, int size){
        return input.substring(0,size);
    }
}
