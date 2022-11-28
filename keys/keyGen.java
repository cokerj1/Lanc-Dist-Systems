import java.io.*;
import java.security.*;
import javax.crypto.*;

public class keyGen{
    public static Key[] makeKeys() throws NoSuchAlgorithmException{
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(1024);

        KeyPair kp = keyPairGen.generateKeyPair();
        Key pub = kp.getPublic();
        Key pvt = kp.getPrivate();

        Key[] pair = {pub,pvt};

        return pair;
    }
}