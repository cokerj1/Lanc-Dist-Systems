import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Path; 
import java.nio.file.Paths;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import javax.crypto.*;

class Challenge{
    private byte[] key;
    private String string;

    // Constructor
    public Challenge(byte[] key, String string){
        this.key = key;
        this.string = string;
    }

    public byte[] init(){
        try {
            PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(key);
            PrivateKey pvt = KeyFactory.getInstance("RSA").generatePrivate(ks);

            //Creating a Signature object & initialize the signature 
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(pvt);
            //Adding data to the signature & calculating said signature
            byte[] str = string.getBytes();
            sign.update(str);
            byte[] signature = sign.sign();
            return signature;
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            // TODO Auto-generated catch block
            System.err.println("Error generating server signature");
        }
        return null;
    }
}

class Authenticate{
    private byte[] signature;
    private byte[] key;
    private String string;

    public Authenticate(byte[] signature, byte[] key, String string){
        this.signature = signature;
        this.key = key;
        this.string = string;
    }

    public boolean init(){
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));
            Signature sign = Signature.getInstance("SHA256WithRSA");
            sign.initVerify(publicKey);
            sign.update(string.getBytes());
            boolean result = sign.verify(signature);
            return result;
        } catch (Exception e) {
            System.err.println("User not found");
            e.printStackTrace();
            return false;
        }
    }
}

public class AuctionServer implements Auction{
    private ArrayList<AuctionItem> lot;
    private Map<Integer, String> userMap;
    private Map<AuctionItem,Integer> auctionMap;
    private Map<AuctionItem,Integer> bidMap;
    private Map<Integer, NewUserInfo> userInfoMap;
    private Map<AuctionItem,AuctionSaleItem> comparisonMap;
    private int usercount;

    public AuctionServer() throws NoSuchAlgorithmException {
        super();
        lot = new ArrayList<AuctionItem>();
        Key[] sPair = keyGen.makeKeys();
        //Matching user IDs to their emails
        userMap = new HashMap<Integer,String>();
        // Matching Auctiom items to the Users that put the items up for bid
        auctionMap = new HashMap<AuctionItem,Integer>();
        // Matching Auction items with the user currently holding the highest bid
        bidMap = new HashMap<AuctionItem,Integer>();
        // Matching User IDs to newUserInfo objects
        userInfoMap = new HashMap<Integer, NewUserInfo>();
        // Matching Auction Items to SaleItem objects
        comparisonMap = new HashMap<AuctionItem,AuctionSaleItem>();
        usercount = 1;
        //add "admin"
        userMap.put(1, "admin@auction.com");
        // Making the server key pair and saving into keys folder.
        serverPairKeys(sPair);

        // Just adding placeholder items
        AuctionItem tablet = createAuctionItem(0001, "Samsung Tab 10.9'", "A tablet", 0);
        AuctionSaleItem tab = createSaleItem("Samsung Tab 10.9'", "A tablet", 125);
        bidMap.put(tablet, 0);
        auctionMap.put(tablet, 1);
        comparisonMap.put(tablet, tab);

        AuctionItem airpods = createAuctionItem(0002, "Apple Airpods", "Wireless Earphones, made in China", 0);
        AuctionSaleItem pods = createSaleItem("Apple Airpods", "Wireless Earphones, made in China", 89);
        bidMap.put(airpods, 0);
        auctionMap.put(airpods, 1);
        comparisonMap.put(airpods, pods);
    }

    @Override
    public synchronized NewUserInfo newUser(String email) throws RemoteException {
        // checks if user has not registered
        if (!userMap.containsValue(email)) {
            usercount++;
            userMap.put(usercount, email);
            Key[] sPair;
            try {
                sPair = keyGen.makeKeys();
                NewUserInfo nui = new NewUserInfo();
                nui.userID = usercount;
                nui.publicKey = sPair[0].getEncoded();
                nui.privateKey =sPair[1].getEncoded();
                userInfoMap.put(nui.userID, nui);
                return nui;
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Error extracting public/private keys");
                return null;
            }
        } else{
            // get existing userinfo of user
            for (Map.Entry<Integer, String> entry : userMap.entrySet()) {
                if (email.equals(entry.getValue())) {
                    int id = entry.getKey();
                    // return user info
                    return userInfoMap.get(id);
                }
            }

        }
        System.err.println("cant find user");
        return null;
    }

    @Override
    public synchronized int newAuction(int userID, AuctionSaleItem item) throws RemoteException {
        // finds highest itemID and increments it before adding that calculation to a variable
        int lotCount = findMaxValueID();
        AuctionItem newItem = createAuctionItem(lotCount+1, item.name, item.description, 0);
        auctionMap.put(newItem, userID);
        // No one has the highest bid, so userID is 0
        bidMap.put(newItem, 0);
        comparisonMap.put(newItem, item);
        return newItem.itemID;
    }

    @Override
    public AuctionCloseInfo closeAuction(int userID, int itemID) throws RemoteException {
        for (Map.Entry<AuctionItem,Integer> aEntry: auctionMap.entrySet()) {
            AuctionItem aItem = aEntry.getKey();
            // Finding the Auction Item
            if (aItem.itemID == itemID) {
                System.out.println("Item: " + aItem.name);
                for (Map.Entry<AuctionItem, AuctionSaleItem> cEntry: comparisonMap.entrySet()){
                    AuctionItem cItem = cEntry.getKey();
                    // Finding the Auction Item that will retrieve the SaleItem
                    if (aItem.itemID == cItem.itemID) {
                        System.out.println("Found sale Item");
                        AuctionSaleItem saleItem = cEntry.getValue();
                        //Checking the highest bid exceeds the reserve price
                        if (aItem.highestBid > saleItem.reservePrice) {
                            System.out.println("highest bid exceeds reserve");
                            Integer uID = aEntry.getValue();
                            if (uID == userID) {
                                System.out.println("User made item");
                                AuctionCloseInfo nInfo = new AuctionCloseInfo();
                                for (Map.Entry<AuctionItem,Integer> bEntry: bidMap.entrySet()){
                                    AuctionItem bItem = bEntry.getKey();
                                    if (bItem.itemID == itemID) {
                                        int winnerID = bEntry.getValue();
                                        nInfo.winningEmail = userMap.get(winnerID);
                                        nInfo.winningPrice = aItem.highestBid;
                                        auctionMap.remove(aItem);
                                        comparisonMap.remove(aItem, saleItem);
                                        bidMap.remove(aItem);
                                        lot.remove(aItem);
                                        return nInfo;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public synchronized boolean bid(int userID, int itemID, int price) throws RemoteException {
        try {
            //checking for the userID to verify if user is valid for bidding
            for (Map.Entry<Integer,String> uEntry: userMap.entrySet()){
                Integer uID = uEntry.getKey();
                if (uID == userID) {
                    // checking for the item to see if it's still up for auction
                    for (Map.Entry<AuctionItem,Integer> aEntry: bidMap.entrySet()) {
                        AuctionItem aItem = aEntry.getKey();
                        if (aItem.itemID == itemID) {
                            // only if the proposed bid is higher than the highest bid then it will be accepted
                            if (aItem.highestBid < price) {
                                aItem.highestBid = price;
                                bidMap.replace(aItem, userID);
                                return true;
                            }
                        }
                    }
                }
            } 
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public AuctionItem[] listItems() throws RemoteException {
        AuctionItem[] lotArry = lot.toArray(new AuctionItem[lot.size()]); 
        return lotArry;
    }

    @Override
    public AuctionItem getSpec(int itemID) throws RemoteException {
        for (AuctionItem item:lot){
            if (item.itemID == itemID) {
                return item;
            }
        }
        return null;
    }

    public int findMaxValueID() {
        int maxKey = 0;
        for (AuctionItem key : bidMap.keySet()) {
            if (key.itemID > maxKey) {
                maxKey = key.itemID;
            }
        }
        return maxKey;
    }
    public void printEmail(){
        for (Map.Entry<Integer, String> entry : userMap.entrySet()) {
            System.out.println(entry.getKey() + "-" + entry.getValue());
        }
    }
    
    public int getUserID(String email) {
        for (Map.Entry<Integer, String> entry : userMap.entrySet()) {
            if (email == entry.getValue()) {
                return entry.getKey();
            }
        }
        return 0;
    }
    
    public String getEmail(Integer id){
        try {
            return userMap.get(id);
        } catch (Exception e) {
            return null;
        }
    }

    public void serverPairKeys(Key[] pair) throws NoSuchAlgorithmException{
        try {
            File pvt_file = new File("../keys/server_private.key");
            File pub_file = new File("../keys/server_public.key");
            FileOutputStream outFile_pvt = new FileOutputStream(pvt_file);
            FileOutputStream outFile_pub = new FileOutputStream(pub_file);
            outFile_pub.write(pair[0].getEncoded());
            outFile_pvt.write(pair[1].getEncoded());
            outFile_pub.close();
            outFile_pvt.close();
        } catch (Exception e) {
            System.err.println("Error making server keys");
            e.printStackTrace();
        }

    }
    public static void main(String[] args) {
        try {
            AuctionServer s = new AuctionServer();
            String name = "Auction";
            Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("Server ready");
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }

    @Override
    public byte[] challenge(int userID) throws RemoteException {
        Path path = Paths.get("../keys/server_private.key");
        try {
            // Loading private key
            byte[] bytes = Files.readAllBytes(path);
            // creating a signature 
            Challenge chal = new Challenge(bytes, "auction");
            byte[] signature = chal.init();
            return signature;
        } catch (IOException e) {
            System.err.println("Error loading private key");
        }
        return null;
    }

    @Override
    public boolean authenticate(int userID, byte[] signature) throws RemoteException {
        try {
            // Retrieve the email and verify the signature
            String userEmail = userMap.get(userID);
            byte[] pubkey = userInfoMap.get(userID).publicKey;
            Authenticate auth = new Authenticate(signature, pubkey, userEmail);
            boolean result = auth.init();
            return result;
        } catch (Exception e) {
            System.err.println("User not found");
            e.printStackTrace();
            return false;
        }
    }

    public AuctionItem createAuctionItem(int itemID, String name, String description, int startingBid){
        AuctionItem item = new AuctionItem();
        item.itemID = itemID;
        item.name = name;
        item.description = description; 
        item.highestBid = startingBid;
        lot.add(item);
        return item;
    }
    public AuctionSaleItem createSaleItem(String name, String description, int reservePrice){
        AuctionSaleItem item = new AuctionSaleItem();
        item.name = name;
        item.description = description; 
        item.reservePrice = reservePrice;
        return item;
    }

    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        // TODO Auto-generated method stub
        return 0;
    }
  }